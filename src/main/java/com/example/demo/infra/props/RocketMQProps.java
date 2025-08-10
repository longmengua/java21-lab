package com.example.demo.infra.props;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter @Setter
@ConfigurationProperties(prefix = "rocketmq")
public class RocketMQProps {
    /** spring.rocketmq.enabled（自定義旗標） */
    private boolean enabled = false;

    /** spring.rocketmq.name-server */
    private String nameServer;

    private Producer producer = new Producer();

    @Getter @Setter
    public static class Producer {
        /** spring.rocketmq.producer.group */
        private String group;

        /** spring.rocketmq.producer.send-message-timeout */
        private Integer sendMessageTimeout;

        /** spring.rocketmq.producer.retry-times-when-send-failed */
        private Integer retryTimesWhenSendFailed;
    }
}

