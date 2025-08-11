package com.example.demo.infra.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.Topology;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.KafkaStreamsConfiguration;

/**
 * 直接以程式碼啟動 Kafka Streams（不依賴 KafkaStreamsFactoryBean）
 */
@Configuration
@Slf4j
public class KafkaStreamsStarterConfig {

    @Bean(destroyMethod = "close")
    public KafkaStreams kafkaStreams(Topology riskKafkaTopology,
                                     KafkaStreamsConfiguration conf) {
        KafkaStreams ks = new KafkaStreams(riskKafkaTopology, conf.asProperties());

        // 開發模式可選：全域未捕捉異常處理
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
//            e.printStackTrace();
            log.error("{}", e.getMessage());
        });

        ks.start();
        return ks;
    }
}
