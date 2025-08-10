package com.example.demo.infra.props;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@Getter
@Setter
@ConfigurationProperties(prefix = "kafka")
public class KafkaProps {
    /** kafka.enabled（自定義旗標，方便用 @ConditionalOnProperty 控制） */
    private boolean enabled = true;

    /** kafka.bootstrap-servers */
    private String bootstrapServers;

    /** kafka.streams.* */
    private KafkaStreamsProps streams = new KafkaStreamsProps();

    /** kafka.producer.* */
    private ProducerProps producer = new ProducerProps();

    @Getter
    @Setter
    public static class KafkaStreamsProps {
        /** kafka.streams.application-id */
        private String applicationId;

        /** kafka.streams.properties.* */
        private Map<String, String> properties;
    }

    @Getter
    @Setter
    public static class ProducerProps {
        /** kafka.producer.records-per-second */
        private int recordsPerSecond = 0;

        /** kafka.producer.duration-seconds */
        private int durationSeconds = 0;
    }
}
