package com.example.demo.infra;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;

import java.nio.charset.StandardCharsets;
import java.util.List;

public class RocketMQTest {

    public static void main(String[] args) throws Exception {
        String topic = "topic-security_trade_data_reporter";
        String nameServer = "34.143.225.26:9876";

        // 建立並啟動 Consumer Thread
        Thread consumerThread = getConsumerThread(nameServer, topic);
        Thread.sleep(2000); // 等 consumer 啟動完成

        // 建立並啟動 Producer Thread
//        Thread producerThread = getProducerThread(nameServer, topic);
//        producerThread.start();

        // 主執行緒等待兩個 thread 結束
//        producerThread.join();
        consumerThread.join();

        System.out.println("🎉 所有任務完成，主程式結束");
    }

    private static Thread getProducerThread(String nameServer, String topic) {
        return new Thread(() -> {
            try {
                DefaultMQProducer producer = new DefaultMQProducer("demo-producer-group");
                producer.setNamesrvAddr(nameServer);
                producer.start();

                for (int i = 0; i < 10; i++) {
                    String body = "Message #" + i;
                    Message message = new Message(topic, "tagA", body.getBytes(StandardCharsets.UTF_8));
                    producer.send(message);
                    System.out.println("✅ 發送訊息: " + body);
                    Thread.sleep(1000); // 每秒發送一筆
                }

                producer.shutdown();
                System.out.println("🚪 Producer 結束");
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private static Thread getConsumerThread(String nameServer, String topic) {
        Thread consumerThread = new Thread(() -> {
            try {
                DefaultMQPushConsumer consumer = new DefaultMQPushConsumer("group_trade_risk");
                consumer.setNamesrvAddr(nameServer);
                consumer.subscribe(topic, "*");

                consumer.registerMessageListener(new MessageListenerConcurrently() {
                    @Override
                    public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
                        for (MessageExt msg : msgs) {
                            System.out.printf("📩 收到訊息：topic=%s, tag=%s, body=%s",
                                    msg.getTopic(), msg.getTags(), new String(msg.getBody()));
                        }
                        return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                    }
                });

                consumer.start();
                System.out.println("🚀 Consumer 啟動完成");

                // 持續 1 分鐘後停止
                Thread.sleep(60_000);
                consumer.shutdown();
                System.out.println("🚪 Consumer 結束");
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        consumerThread.start();
        return consumerThread;
    }
}
