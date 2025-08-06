package com.example.demo.application.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Properties;
import java.util.UUID;

/***
 * Spring Boot 啟動
 *    │
 *    ├── 掃描 @Component → KafkaDataProducerJob
 *    │
 *    ├── 實例化 KafkaDataProducerJob（建構子產生 KafkaProducer 實例）
 *    │
 *    └── 執行 @PostConstruct → startProducing()
 *            │
 *            └── 啟動 Thread，開始每秒送 1 萬筆資料到 Kafka
 * */
@Component
public class KafkaDataProducerJob {

    private static final String TOPIC = "events";
    private static final int RECORDS_PER_SECOND = 10_000;
    private static final ObjectMapper mapper = new ObjectMapper();

    private final Producer<String, String> producer;

    public KafkaDataProducerJob() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "single-kafka.kafka.orb.local:9092");
        props.put(ProducerConfig.ACKS_CONFIG, "1");
        props.put(ProducerConfig.LINGER_MS_CONFIG, "5");
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, 32_768); // 32KB batch
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        this.producer = new KafkaProducer<>(props);
    }

    @PostConstruct
    public void startProducing() {
        Thread thread = new Thread(() -> {
            System.out.println("🚀 Kafka Producer Job started. Sending " + RECORDS_PER_SECOND + " records per second...");
            int second = 0;
            int stopSecond = 5;
            while (second != stopSecond) {
                long start = System.currentTimeMillis();
                for (int i = 0; i < RECORDS_PER_SECOND; i++) {
                    try {
                        String json = generateJson();
                        producer.send(new ProducerRecord<>(TOPIC, json));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                long duration = System.currentTimeMillis() - start;
                long sleepTime = 1000 - duration;
                if (sleepTime > 0) {
                    try {
                        Thread.sleep(sleepTime);
                    } catch (InterruptedException ignored) {
                    }
                }
                second++;
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    private String generateJson() throws Exception {
        var node = mapper.createObjectNode();
        node.put("user_id", (long)(Math.random() * 10_000));
        node.put("action", UUID.randomUUID().toString().substring(0, 8));
        node.put("event_time", Instant.now().toString());
        return mapper.writeValueAsString(node);
    }
}
