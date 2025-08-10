package com.example.demo.application.scheduler;

import com.example.demo.infra.props.KafkaProps;
import com.example.demo.infra.props.TopicsProps;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * <p>Kafka 模擬資料生產器</p>
 *
 * 啟動條件：
 *   1. application.yml 中 `mock.kafka=true`
 *   2. KafkaProps 中 `kafka.enabled=true`
 *   3. 已設定有效的 kafka.bootstrap-servers
 *   4. 已設定 topics.events
 *
 * 功能：
 *   - 啟動後自動在背景執行緒中，每秒固定送出 N 筆測試資料到指定 topic
 *   - 持續 M 秒，結束後自動 flush 並關閉 producer
 */
@Component
@ConditionalOnProperty(name = "mock.kafka", havingValue = "true") // 只有 mock.kafka=true 才會建立這個 Bean
public class KafkaDataProducerJob {

    /** Kafka 相關設定（來自 application.yml 的 kafka.* 區塊） */
    private final KafkaProps kafkaProps;

    /** topics 設定（來自 application.yml 的 topics.* 區塊） */
    private final TopicsProps topics;

    /** JSON 序列化工具（用於產生測試資料 JSON 字串） */
    private static final ObjectMapper mapper = new ObjectMapper();

    /** Kafka Producer 實例 */
    private Producer<String, String> producer;

    /** 背景送資料的執行緒 */
    private Thread worker;

    /** 建構子注入 Kafka 與 Topics 設定 */
    public KafkaDataProducerJob(KafkaProps kafkaProps, TopicsProps topics) {
        this.kafkaProps = kafkaProps;
        this.topics = topics;
    }

    /**
     * Bean 初始化後執行，啟動 Mock 資料生產流程
     */
    @PostConstruct
    public void startProducing() {
        // 0) 檢查 Kafka 總開關
        if (!kafkaProps.isEnabled()) {
            System.out.println("⚠ Kafka 未啟用（kafka.enabled=false），跳過 Mock Producer");
            return;
        }

        // 0.1) 檢查 bootstrap servers 是否設定
        String bootstrap = kafkaProps.getBootstrapServers();
        if (bootstrap == null || bootstrap.isBlank()) {
            System.out.println("❌ 缺少 kafka.bootstrap-servers，跳過 Mock Producer");
            return;
        }

        // 0.2) 檢查 events topic 是否設定
        String topicName = topics.getEvents();
        if (topicName == null || topicName.isBlank()) {
            throw new IllegalStateException("❌ 缺少 topics.events 配置，請在 application.yml 設定 topics.events");
        }

        // 1) 讀取測試參數（records-per-second 與 duration-seconds）
        int rps =  (kafkaProps.getProducer() != null) ? kafkaProps.getProducer().getRecordsPerSecond() : 0;
        int seconds = (kafkaProps.getProducer() != null) ? kafkaProps.getProducer().getDurationSeconds() : 0;
        if (rps <= 0 || seconds <= 0) {
            System.out.printf("⚠ 測試參數不生效（recordsPerSecond=%d, durationSeconds=%d），跳過 Mock Producer%n", rps, seconds);
            return;
        }

        // 2) 建立 Kafka Producer 連線設定
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap); // Kafka broker 位址
        props.put(ProducerConfig.ACKS_CONFIG, "1"); // leader 確認即可
        props.put(ProducerConfig.LINGER_MS_CONFIG, "5"); // 批次延遲 5ms
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, "32768"); // 批次大小
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4"); // 壓縮方式
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "false"); // 模擬資料不需要 EO
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "5"); // 每連線同時請求數
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName()); // key 序列化
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName()); // value 序列化

        // 建立 Producer 實例
        this.producer = new KafkaProducer<>(props);

        // 3) 啟動背景執行緒送資料
        worker = new Thread(() -> {
            System.out.printf("🚀 Mock Kafka Producer ON. Sending %d records/sec to topic '%s' for %d sec...%n",
                    rps, topicName, seconds);

            long endAt = System.currentTimeMillis() + seconds * 1000L;

            while (System.currentTimeMillis() < endAt) {
                long tickStart = System.currentTimeMillis();

                // 每秒發送 rps 筆資料
                for (int i = 0; i < rps; i++) {
                    try {
                        String key = String.valueOf(randomUserId()); // 以 userId 做 key，確保同 user 落同分區
                        String json = generateJson(key); // 產生測試 JSON
                        producer.send(new ProducerRecord<>(topicName, key, json), (md, ex) -> {
                            if (ex != null) {
                                System.err.println("❌ Produce failed: " + ex.getMessage());
                            }
                        });
                    } catch (Exception e) {
                        System.err.println("❌ Build record failed: " + e.getMessage());
                    }
                }

                // 節流至 1 秒（模擬固定速率）
                long used = System.currentTimeMillis() - tickStart;
                long sleep = 1000 - used;
                if (sleep > 0) {
                    try { Thread.sleep(sleep); } catch (InterruptedException ignored) {}
                }
            }

            // 所有資料發送完畢後 flush
            producer.flush();
            System.out.println("✅ Mock Kafka Producer finished.");
        }, "mock-kafka-producer");

        // 設為 daemon 執行緒，隨應用結束自動關閉
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * Bean 銷毀前呼叫，負責釋放資源
     */
    @PreDestroy
    public void shutdown() {
        try {
            if (worker != null && worker.isAlive()) worker.join(2000); // 等待執行緒結束
        } catch (InterruptedException ignored) {}
        if (producer != null) producer.close(); // 關閉 Kafka 連線
    }

    /** 產生隨機 userId */
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
