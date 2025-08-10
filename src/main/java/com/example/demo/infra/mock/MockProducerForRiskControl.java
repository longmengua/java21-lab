package com.example.demo.infra.mock;

import com.example.demo.domain.model.risk.EventType;
import com.example.demo.domain.event.RiskEvent;
import com.example.demo.domain.model.risk.Side;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Random;
import java.util.concurrent.Executors;
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
@ConditionalOnProperty(name = "mock.kafka.risk-control", havingValue = "true") // 只有 true 才會建立這個 Bean
public class MockProducerForRiskControl {

    /** Spring Kafka 用於發送訊息到 Kafka 的模板物件 */
    private final KafkaTemplate<String, String> kafka;

    /** JSON 轉換器，用於將 RiskEvent 轉成字串 */
    private final ObjectMapper om = new ObjectMapper();

    /** 隨機數生成器，用於隨機生成 IP 後綴 */
    private final Random rnd = new Random();

    /** Kafka 事件 Topic（從 application.properties / yml 注入） */
    @Value("${topics.events}")
    String EVENTS;

    /** 建構子，注入 KafkaTemplate */
    public MockProducerForRiskControl(KafkaTemplate<String, String> kafka) {
        this.kafka = kafka;
    }

    /**
     * Bean 初始化後啟動
     * - 建立單一排程執行緒
     * - 每 2 秒執行一次 burstFastCancel() 模擬高頻快撤行為
     */
    @PostConstruct
    public void start() {
        try (var ex = Executors.newSingleThreadScheduledExecutor()) {
            ex.scheduleAtFixedRate(this::burstFastCancel, 1000, 2000, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * 模擬「高頻快撤單」行為
     * - 每次產生 12 組下單 + 撤單事件
     * - 撤單與下單間隔 100ms
     * - 這種模式會快速觸發「快撤」風控規則
     */
    private void burstFastCancel() {
        try {
            String acc = "u123"; // 測試帳號 ID
            String sym = "BTCUSDT"; // 測試交易對
            String ip = "10.0.0." + rnd.nextInt(50); // 隨機生成測試 IP

            for (int i = 0; i < 12; i++) {
                send(acc, ip, sym, EventType.PLACE); // 下單事件
                Thread.sleep(100);                  // 模擬快速撤單的時間差
                send(acc, ip, sym, EventType.CANCEL);// 撤單事件
            }
        } catch (Exception ignore) {
            // 測試環境中忽略例外
        }
    }

    /**
     * 發送單筆事件到 Kafka
     *
     * @param acc 帳號 ID
     * @param ip 來源 IP
     * @param sym 交易對
     * @param t   事件類型（下單 / 撤單）
     */
    private void send(String acc, String ip, String sym, EventType t) throws Exception {
        RiskEvent ev = new RiskEvent();
        ev.setAccountId(acc);
        ev.setIp(ip);
        ev.setSymbol(sym);
        ev.setType(t);
        ev.setSide(Side.BUY);
        ev.setTs(Instant.now());

        // 發送到 Kafka：key = accountId, value = JSON 序列化的事件
        kafka.send(new ProducerRecord<>(EVENTS, acc, om.writeValueAsString(ev)));
    }
}
