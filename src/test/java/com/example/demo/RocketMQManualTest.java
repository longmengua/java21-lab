package com.example.demo;

import com.example.demo.service.RocketProducerService;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQMessageConverter;
import org.apache.rocketmq.client.producer.DefaultMQProducer;

public class RocketMQManualTest {

    public static void main(String[] args) throws Exception {
        // 1. 建立 RocketMQ 原生 producer
        DefaultMQProducer producer = new DefaultMQProducer("test-producer-group");
        producer.setNamesrvAddr("localhost:9876");
        producer.start();

        // 2. 建立 RocketMQTemplate 並注入 producer
        RocketMQTemplate rocketMQTemplate = new RocketMQTemplate();
        rocketMQTemplate.setProducer(producer);
        rocketMQTemplate.setMessageConverter(new RocketMQMessageConverter().getMessageConverter());

        // 3. 注入 service
        RocketProducerService service = new RocketProducerService();
        service.setRocketMQTemplate(rocketMQTemplate); // 改用 setter 注入

        // 4. 呼叫發送訊息
        service.sendMessage("test-topic", "Hello RocketMQ from pure Java!");

        // 5. 結束
        Thread.sleep(2000);
        producer.shutdown();
    }
}
