package com.example.demo.interfaces.consumer;

import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Component
@RocketMQMessageListener(
        topic = "test-topic",
        consumerGroup = "test-consumer-group",
        messageModel = MessageModel.CLUSTERING
)
public class RocketConsumer implements RocketMQListener<String> {

    @Override
    public void onMessage(String message) {
        System.out.printf("Received message: %s", message);
    }
}
