package com.example.demo.infra.props;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@Getter @Setter
@ConfigurationProperties(prefix = "spring.kafka")
public class KafkaProps {
    /** spring.kafka.enabled（自定義旗標，方便你用 @ConditionalOnProperty 控制） */
    private boolean enabled = true;

    /** spring.kafka.bootstrap-servers */
    private String bootstrapServers;

    private KafkaProps.KafkaStreamsProps cluster = new KafkaStreamsProps();

    @Getter @Setter
    public static class KafkaStreamsProps {
        /** spring.kafka.streams.application-id */
        private String applicationId;

        /** spring.kafka.streams.properties.* */
        private Map<String, String> properties;
    }
}

