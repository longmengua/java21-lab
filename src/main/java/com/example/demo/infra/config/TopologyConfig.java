package com.example.demo.infra.config;

import com.example.demo.application.service.RiskConfigService;
import com.example.demo.domain.event.RiskEvent;
import com.example.demo.interfaces.consumer.kafka.RiskProcessor;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.Topology;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerde;

/**
 * Kafka Streams 拓撲 (Topology) 配置
 *
 * 這個配置類負責建立 Kafka Streams 的處理拓撲：
 *   - 從 Kafka 讀取事件資料 (source topic)
 *   - 交由風控處理器 (RiskProcessor) 處理
 *   - 將結果輸出到告警 topic (sink topic)
 */
@Configuration
public class TopologyConfig {

    // 事件輸入 Topic (從 application.properties / application.yml 注入)
    @Value("${topics.events}")
    String EVENTS;

    // 告警輸出 Topic
    @Value("${topics.alerts}")
    String ALERTS;

    /**
     * 建立風控 Kafka Streams 處理拓撲
     *
     * @param cfg 風控配置服務，供 RiskProcessor 在處理事件時讀取風控規則
     * @return Kafka Streams Topology
     */
    @Bean(name = "riskKafkaTopology")
    public Topology topology(RiskConfigService cfg) {
        // 建立 JSON 反序列化工具，將 Kafka 中的 JSON 轉為 RiskEvent 物件
        JsonSerde<RiskEvent> riskEventSerde = new JsonSerde<>(RiskEvent.class);

        // 從 Serde 取出 Deserializer，設定為信任所有 package
        // 避免 Spring Kafka 在反序列化時因 package 限制而拋出錯誤
        JsonDeserializer<RiskEvent> valueDeser =
                (JsonDeserializer<RiskEvent>) riskEventSerde.deserializer();
        valueDeser.addTrustedPackages("*");

        // 建立 Kafka Streams 拓撲
        Topology t = new Topology();

        // Step 1: 定義資料來源 (Source)
        //   - 名稱: "SRC"
        //   - Key 反序列化：StringDeserializer
        //   - Value 反序列化：JSON -> RiskEvent
        //   - 來源 Topic: EVENTS
        t.addSource(
                "SRC",
                Serdes.String().deserializer(),
                valueDeser,
                EVENTS
        );

        // Step 2: 定義資料處理器 (Processor)
        //   - 名稱: "RISK"
        //   - 處理邏輯: RiskProcessor (根據風控規則檢測事件並產生告警)
        //   - 上游來源: "SRC"
        t.addProcessor("RISK", () -> new RiskProcessor(cfg), "SRC");

        // Step 3: 定義資料輸出 (Sink)
        //   - 名稱: "ALERTS"
        //   - 輸出 Topic: ALERTS
        //   - Key 序列化：StringSerializer
        //   - Value 序列化：StringSerializer
        //   - 上游來源: "RISK"
        t.addSink(
                "ALERTS",
                ALERTS,
                Serdes.String().serializer(),
                Serdes.String().serializer(),
                "RISK"
        );

        // 返回完整的拓撲結構
        return t;
    }
}
