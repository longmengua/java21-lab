package com.example.demo.infra.mock;

import com.example.demo.infra.props.KafkaProps;
import com.example.demo.infra.props.TopicsProps;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ThreadLocalRandom;

/**
 * <p>Kafka 模擬資料生產器</p>
 *
 * 啟動條件：
 *   1) application.yml: mock.kafka.clickhouse=true
 *   2) application.yml: kafka.enabled=true
 *   3) 已設定 kafka.bootstrap-servers
 *   4) 已設定 topics.events
 * 行為：
 *   - 啟動後背景固定每秒送出 rps 筆資料到 events topic，總計 seconds 秒
 *   - 結束後 flush 並關閉 Producer
 */
@Component
@ConditionalOnProperty(name = "mock.kafka.clickhouse", havingValue = "true")
@Slf4j
public class MockProducerForClickhouse {

    /** Kafka 連線與 Producer 測試參數（由 application.yml 綁定） */
    private final KafkaProps kafkaProps;

    /** Topics 設定（由 application.yml 綁定） */
    private final TopicsProps topics;

    /** JSON 序列化工具（產測試 JSON 字串） */
    private static final ObjectMapper mapper = new ObjectMapper();

    /** Kafka Producer 實例（在 PostConstruct 建立，PreDestroy 關閉） */
    private Producer<String, String> producer;

    /** 每秒排程器（長生命週期，與 Bean 同生同滅） */
    private ScheduledExecutorService scheduler;

    /** 保存 scheduleAtFixedRate 回傳的 future，好在到時候取消自己 */
    private final AtomicReference<ScheduledFuture<?>> scheduledRef = new AtomicReference<>();

    /** 用於記錄啟動的背景「啟動器」執行緒（可選，主要為了與原邏輯對齊） */
    private Thread launcher;

    public MockProducerForClickhouse(KafkaProps kafkaProps, TopicsProps topics) {
        this.kafkaProps = kafkaProps;
        this.topics = topics;
    }

    /**
     * Bean 初始化：檢查設定 → 建 Producer → 啟動固定頻率送資料
     */
    @PostConstruct
    public void startProducing() {
        // ====== 0) 啟動前檢查條件 ======
        if (!kafkaProps.isEnabled()) {
            log.warn("Kafka 未啟用（kafka.enabled=false），跳過 Mock Producer");
            return;
        }

        String bootstrap = kafkaProps.getBootstrapServers();
        if (bootstrap == null || bootstrap.isBlank()) {
            log.warn("缺少 kafka.bootstrap-servers，跳過 Mock Producer");
            return;
        }

        String topicName = topics.getEvents();
        if (topicName == null || topicName.isBlank()) {
            throw new IllegalStateException("缺少 topics.events 配置，請在 application.yml 設定 topics.events");
        }

        int rps = (kafkaProps.getProducer() != null) ? kafkaProps.getProducer().getRecordsPerSecond() : 0;
        int seconds = (kafkaProps.getProducer() != null) ? kafkaProps.getProducer().getDurationSeconds() : 0;
        if (rps <= 0 || seconds <= 0) {
            log.info("測試參數不生效（recordsPerSecond={}, durationSeconds={}），跳過 Mock Producer", rps, seconds);
            return;
        }

        // ====== 1) 建立 Kafka Producer ======
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.put(ProducerConfig.ACKS_CONFIG, "1"); // 僅 leader ack，降低延遲（模擬資料不需高可靠）
        props.put(ProducerConfig.LINGER_MS_CONFIG, "5"); // 最多延遲 5ms 才發送（促進 batch）
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, "32768"); // 32KB 批次
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4"); // 壓縮提吞吐
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "false"); // 模擬資料，不求 EO
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "5");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        this.producer = new KafkaProducer<>(props);

        // ====== 2) 啟動固定頻率送資料（每秒） ======
        // 用單執行緒排程器；設為 daemon，隨 JVM 結束
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "mock-kafka-producer");
            t.setDaemon(true);
            return t;
        });

        final long endAt = System.currentTimeMillis() + seconds * 1000L;
        log.info("🚀 Mock Kafka Producer ON. Sending {} records/sec to topic '{}' for {} sec...", rps, topicName, seconds);

        // 啟動器：主要是為了與原本「啟動背景執行緒」的慣例一致，可省略
        this.launcher = new Thread(() -> {
            // 核心工作：每秒執行一次
            ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
                long now = System.currentTimeMillis();

                // 到時間了：取消自己 → 關閉排程器 → flush → 完成
                if (now >= endAt) {
                    try {
                        ScheduledFuture<?> self = scheduledRef.get();
                        if (self != null && !self.isCancelled()) {
                            self.cancel(false); // 取消後續觸發（不打斷正在跑的這次任務）
                        }
                        scheduler.shutdown(); // 停止接受新任務

                        // 等待排程器優雅關閉
                        boolean terminated = scheduler.awaitTermination(2, TimeUnit.SECONDS);
                        if (!terminated) {
                            log.warn("Mock Kafka Producer scheduler did not terminate within timeout");
                        }
                    } catch (InterruptedException ie) {
                        // 恢復中斷狀態，避免吞掉中斷
                        Thread.currentThread().interrupt();
                        log.warn("Scheduler termination interrupted", ie);
                    } catch (Exception e) {
                        log.warn("Scheduler shutdown error: {}", e.getMessage(), e);
                    }

                    // 送完尾端資料
                    try {
                        producer.flush();
                    } catch (Exception e) {
                        log.warn("Producer flush error on finish: {}", e.getMessage(), e);
                    }

                    log.info("✅ Mock Kafka Producer finished.");
                    return; // 結束這次執行
                }

                // 在本秒內送出 rps 筆
                for (int i = 0; i < rps; i++) {
                    try {
                        String key = String.valueOf(randomUserId()); // 同 userId → 同 partition（利於測試可預期性）
                        String json = generateJson(key);
                        producer.send(new ProducerRecord<>(topicName, key, json), (md, ex) -> {
                            if (ex != null) {
                                log.error("Produce failed: {}", ex.getMessage(), ex);
                            }
                        });
                    } catch (Exception e) {
                        log.error("Build record failed: {}", e.getMessage(), e);
                    }
                }
            }, 0, 1, TimeUnit.SECONDS);

            // 存起來，方便在「到時間」時自我取消
            scheduledRef.set(future);
        }, "mock-kafka-producer-launcher");

        this.launcher.setDaemon(true);
        this.launcher.start();
    }

    /**
     * Bean 銷毀：確保 scheduler 與 producer 都被優雅關閉
     */
    @PreDestroy
    public void shutdown() {
        // 1) 停排程器
        if (scheduler != null && !scheduler.isShutdown()) {
            try {
                ScheduledFuture<?> f = scheduledRef.get();
                if (f != null && !f.isCancelled()) {
                    f.cancel(false);
                }
                scheduler.shutdown(); // 停止接新任務
                boolean terminated = scheduler.awaitTermination(2, TimeUnit.SECONDS);
                if (!terminated) {
                    log.warn("Scheduler did not terminate in time, forcing shutdownNow()");
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted while awaiting scheduler termination", ie);
                scheduler.shutdownNow();
            } catch (Exception e) {
                log.warn("Error during scheduler shutdown: {}", e.getMessage(), e);
            }
        }

        // 2) 等待啟動器執行緒（若仍存活）
        try {
            if (launcher != null && launcher.isAlive()) {
                launcher.join(2000);
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while joining launcher thread", ie);
        }

        // 3) 關 Producer
        if (producer != null) {
            try {
                producer.flush();
            } catch (Exception e) {
                log.warn("Producer flush error on shutdown: {}", e.getMessage(), e);
            }
            try {
                producer.close();
            } catch (Exception e) {
                log.warn("Producer close error: {}", e.getMessage(), e);
            }
        }
    }

    /** 產生隨機 userId（0 ~ 9999） */
    private static long randomUserId() {
        return ThreadLocalRandom.current().nextLong(10_000L);
    }

    /** 產生測試 JSON 資料 */
    private static String generateJson(String userId) throws Exception {
        var node = mapper.createObjectNode()
                .put("user_id", userId)
                .put("action", UUID.randomUUID().toString().substring(0, 8))
                .put("event_time", Instant.now().toString());
        return mapper.writeValueAsString(node);
    }
}
