package com.example.demo.interfaces.consumer.rocket;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Component
@RocketMQMessageListener(
        topic = "demo-topic",
        consumerGroup = "demo-consumer-group",
        messageModel = MessageModel.CLUSTERING
)
@Slf4j
public class DemoConsumer implements RocketMQListener<String> {

    @Override
    public void onMessage(String message) {
        log.info("✅ Received message: {}", message);
    }
}
