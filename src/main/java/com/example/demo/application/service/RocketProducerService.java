package com.example.demo.application.service;

import lombok.Setter;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "spring.rocketmq", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RocketProducerService {

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    public void sendMessage(String topic, String message) {
        rocketMQTemplate.send(topic, MessageBuilder.withPayload(message).build());
    }
}
