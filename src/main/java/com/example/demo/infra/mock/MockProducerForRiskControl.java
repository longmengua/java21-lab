package com.example.demo.infra.mock;

import com.example.demo.domain.model.risk.EventType;
import com.example.demo.domain.event.RiskEvent;
import com.example.demo.domain.model.risk.Side;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * <p>MockProducer</p>
 *
 * 功能：
 *   - 用來在本地或測試環境中模擬 Kafka 事件流
 *   - 定時發送模擬的交易事件（下單 + 撤單），用來觸發風控規則
 * 測試目的：
 *   - 模擬「高頻快撤單」行為
 *   - 驗證風控系統是否正確檢測與產生告警
 */
@Component
@ConditionalOnProperty(name = "mock.kafka.risk-control", havingValue = "true")
@Slf4j
public class MockProducerForRiskControl {

    /** Spring Kafka 用於發送訊息到 Kafka 的模板物件 */
    private final KafkaTemplate<String, String> kafka;

    /** JSON 轉換器，用於將 RiskEvent 轉成字串 */
    private final ObjectMapper om;

    /** 隨機數生成器，用於隨機生成 IP 後綴 */
    private final Random rnd = new Random();

    /** Kafka 事件 Topic（從 application.properties / yml 注入） */
    @Value("${topics.events}")
    String EVENTS;

    /** 常駐排程執行緒池（避免在 @PostConstruct 中被關閉） */
    private ScheduledExecutorService executor;

    public MockProducerForRiskControl(KafkaTemplate<String, String> kafka, ObjectMapper om) {
        this.kafka = kafka;
        this.om = om; // Spring 會自動注入已註冊 JavaTimeModule 的 Mapper
    }

    /**
     * Bean 初始化後啟動（每 1 秒觸發一次）
     */
    @PostConstruct
    public void start() {
        executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(this::burstFastCancel, 1000, 2000, TimeUnit.MILLISECONDS);
        log.info("MockProducerForRiskControl 已啟動，固定產生模擬事件（間隔=1s）");
    }

    /**
     * Bean 銷毀前關閉排程
     */
    @PreDestroy
    public void stop() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
            log.info("MockProducerForRiskControl 已停止");
        }
    }

    /**
     * 模擬「高頻快撤單」行為
     * - 每次產生 12 組下單 + 撤單事件
     * - 撤單與下單間隔 100ms
     */
    private void burstFastCancel() {
        try {
            log.info("[開始模擬高頻快撤單事件]");
            String acc = "u123";                 // 測試帳號 ID
            String sym = "BTCUSDT";              // 測試交易對
            String ip = "10.0.0." + rnd.nextInt(50); // 隨機生成測試 IP

            for (int i = 0; i < 12; i++) {
                send(acc, ip, sym, EventType.PLACE);   // 下單事件
                Thread.sleep(100);                     // 模擬快速撤單的時間差
                send(acc, ip, sym, EventType.CANCEL);  // 撤單事件
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("模擬事件發送被中斷", ie);
        } catch (Exception e) {
            log.error("模擬事件發送失敗", e);
        }
    }

    /**
     * 發送單筆事件到 Kafka，並記錄 log
     */
    private void send(String acc, String ip, String sym, EventType t) throws Exception {
        RiskEvent ev = new RiskEvent();
        ev.setAccountId(acc);
        ev.setIp(ip);
        ev.setSymbol(sym);
        ev.setType(t);
        ev.setSide(Side.BUY);
        ev.setTs(Instant.now());

        String json = om.writeValueAsString(ev);

        // 發送到 Kafka：key = accountId, value = JSON 序列化的事件
        kafka.send(new ProducerRecord<>(EVENTS, acc, json))
                .whenComplete((meta, ex) -> {
                    if (ex != null) log.error("send fail", ex);
                    else log.debug("sent {}", meta);
                });

        // 新增一筆 log，方便觀察每筆測試資料
//        log.info("Mock Kafka 事件發送 JSON: {}", json);
    }
}
