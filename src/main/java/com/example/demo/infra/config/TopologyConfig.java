package com.example.demo.infra.config;

import com.example.demo.application.service.RiskConfigService;
import com.example.demo.domain.event.RiskEvent;
import com.example.demo.interfaces.consumer.kafka.RiskProcessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.streams.Topology;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

/**
 * <p>Kafka Streams 拓撲（Topology）配置類</p>
 *
 * 負責建立 Kafka Streams 的數據流處理邏輯：
 *  1. 從指定的 source topic 讀取 Kafka 訊息
 *  2. 交由 RiskProcessor 進行風控檢測
 *  3. 將結果輸出到指定的告警 topic
 * 註：在 Kafka Streams 裡，你有兩種主要建拓撲的方式：
 *      - DSL API（高階 API，例如 StreamsBuilder.stream()）
 *      - Processor API（低階 API，例如 Topology.addSource() / addProcessor() / addSink()）
 */
@Configuration
public class TopologyConfig {

    /**
     * <p>靜態單例的 String Serde</p>
     *
     * Kafka 提供的 Serdes.String() 是無狀態、可安全重用的。
     * 用 static final 方式持有可避免 IDE 認為我們每次都「創建未關閉的資源」而警告。
     */
    private static final Serde<String> STRING_SERDE = Serdes.String();

    // 從 application.yml / application.properties 讀取事件輸入 topic 名稱
    @Value("${topics.events}")
    String EVENTS;

    // 從 application.yml / application.properties 讀取告警輸出 topic 名稱
    @Value("${topics.alerts}")
    String ALERTS;

    /**
     * 建立 Kafka Streams Topology Bean
     *
     * @param cfg 風控配置服務（供 RiskProcessor 在處理事件時讀取風控規則）
     * @return Kafka Streams Topology
     */
    @Bean(name = "riskKafkaTopology")
    public Topology topology(RiskConfigService cfg, ObjectMapper om) {
        // ===== Step 0: 建立 JSON Serde（反序列化 + 序列化） =====

        // 建立 JSON 反序列化器（將 Kafka JSON 訊息轉成 RiskEvent 物件）
        Serde<RiskEvent> riskEventSerde = getRiskEventSerde();

        // ===== Step 1: 建立空拓撲物件 =====
        Topology t = new Topology();

        // ===== Step 2: 定義資料來源（Source Node） =====
        //  - 名稱："SRC"（拓撲節點名稱）
        //  - Key 反序列化：STRING_SERDE.deserializer()
        //  - Value 反序列化：riskEventSerde.deserializer()
        //  - 來源 Topic：EVENTS（配置檔注入）
        t.addSource(
                "SRC",
                STRING_SERDE.deserializer(),
                riskEventSerde.deserializer(),
                EVENTS
        );

        // ===== Step 3: 定義處理節點（Processor Node） =====
        //  - 名稱："RISK"
        //  - 處理邏輯：RiskProcessor（自訂的 Processor API 實作，用來執行風控檢測邏輯）
        //  - 上游節點："SRC"（從 Source Node 接收資料）
        t.addProcessor("RISK", () -> new RiskProcessor(cfg, om), "SRC");

        // ===== Step 4: 定義輸出節點（Sink Node） =====
        //  - 名稱："ALERTS"
        //  - 輸出 Topic：ALERTS（配置檔注入）
        //  - Key 序列化：STRING_SERDE.serializer()
        //  - Value 序列化：STRING_SERDE.serializer()
        //  - 上游節點："RISK"（從 RiskProcessor 輸出資料）
        //  ⚠️ 如果要輸出 JSON，可改成：
        //      new JsonSerializer<AlertDto>()
        t.addSink(
                "ALERTS",
                ALERTS,
                STRING_SERDE.serializer(),
                STRING_SERDE.serializer(),
                "RISK"
        );

        // ===== Step 5: 返回完整拓撲 =====
        return t;
    }

    private static Serde<RiskEvent> getRiskEventSerde() {
        JsonDeserializer<RiskEvent> riskEventJsonDeserializer = new JsonDeserializer<>(RiskEvent.class);
        riskEventJsonDeserializer.addTrustedPackages("*"); // 信任所有 package，避免反序列化時被阻擋
        riskEventJsonDeserializer.ignoreTypeHeaders();     // 忽略 Kafka type headers，避免 header/type 不一致導致的反序列化錯誤（可選）

        // 建立 JSON 序列化器（將 RiskEvent 物件轉回 JSON，用於寫回 Kafka 時）
        Serializer<RiskEvent> riskEventJsonSerializer = new JsonSerializer<>();

        // 將序列化器與反序列化器組裝成 Serde
        // 好處：Serde 物件可以同時提供 serializer() / deserializer() 給不同 API 使用
        return Serdes.serdeFrom(riskEventJsonSerializer, riskEventJsonDeserializer);
    }
}
