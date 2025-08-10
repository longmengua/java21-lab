package com.example.demo.application.scheduler;

import com.example.demo.application.event.KafkaToClickhouseEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/***
 * 啟動 Spring Boot 應用
 *     │
 *     ├─ 掃描到 KafkaToClickHouseJob 被註解為 @Component
 *     │
 *     ├─ 實例化 KafkaToClickHouseJob Bean
 *     │
 *     └─ 調用 @PostConstruct 標註的方法 → startFlinkJob()
 *               │
 *               └──> Flink job 就開始執行（非同步）
 * */
@Component
public class KafkaToClickHouseJob {

    @PostConstruct
    public void startFlinkJob() throws Exception {
        System.out.println("✅ Starting Flink job...");

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // ✅ 設定 Flink 全域併行度（parallelism）
        env.setParallelism(2); // 可依 CPU 調整，例如 2 核心就設 2

        // ✅ Kafka Source (new API)
        KafkaSource<String> source = KafkaSource.<String>builder()
                .setBootstrapServers("single-kafka.kafka.orb.local:9092")
                .setTopics("events")
                .setGroupId("flink-consumer")
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .setStartingOffsets(OffsetsInitializer.earliest())
                .build();

        // ✅ 建立資料流程
        DataStream<String> raw = env.fromSource(source, WatermarkStrategy.noWatermarks(), "KafkaSource");

        // ✅ JSON 轉事件物件
        DataStream<KafkaToClickhouseEvent> parsed = raw.map(new JsonToEvent());

        // ✅ 寫入 ClickHouse
        parsed.addSink(new ClickHouseSink());

        env.executeAsync("Spring Boot Flink KafkaToClickHouse (KafkaSource)");
    }

    // ✅ JSON 字串轉事件
    static class JsonToEvent extends RichMapFunction<String, KafkaToClickhouseEvent> {
        private final ObjectMapper mapper = new ObjectMapper();

        @Override
        public KafkaToClickhouseEvent map(String json) throws Exception {
            JsonNode node = mapper.readTree(json);
            return new KafkaToClickhouseEvent(
                    node.get("user_id").asLong(),
                    node.get("action").asText(),
                    Instant.parse(node.get("event_time").asText())
            );
        }
    }

    // ✅ ClickHouse Sink with batch
    static class ClickHouseSink extends RichSinkFunction<KafkaToClickhouseEvent> {
        private transient Connection conn;
        private transient PreparedStatement stmt;
        private final List<KafkaToClickhouseEvent> buffer = new ArrayList<>();
        private static final int BATCH_SIZE = 100;

        private transient ScheduledExecutorService monitorExecutor;

        @Override
        public void open(Configuration parameters) throws Exception {
            conn = DriverManager.getConnection("jdbc:clickhouse://localhost:8123/default");
            stmt = conn.prepareStatement("INSERT INTO flink_sink (user_id, action, event_time) VALUES (?, ?, ?)");

            // ✅ 啟動背景監控任務，每 5 秒檢查 buffer 是否為空
            monitorExecutor = Executors.newSingleThreadScheduledExecutor();
            monitorExecutor.scheduleAtFixedRate(() -> {
                if (buffer.isEmpty()) {
                    System.out.println("🟡 Still waiting for data from Kafka...");
                } else {
                    System.out.println("📦 Buffer size: " + buffer.size());
                }
            }, 0, 5, TimeUnit.SECONDS);
        }

        @Override
        public void invoke(KafkaToClickhouseEvent event, Context context) throws Exception {
            buffer.add(event);
            if (buffer.size() >= BATCH_SIZE) {
                flush();
            }
        }

        private void flush() throws SQLException {
            try {
                for (KafkaToClickhouseEvent e : buffer) {
                    stmt.setLong(1, e.getUserId());
                    stmt.setString(2, e.getAction());
                    stmt.setTimestamp(3, Timestamp.from(e.getEventTime()));
                    stmt.addBatch();
                }
                int[] result = stmt.executeBatch();
                System.out.printf("✅ ClickHouse batch insert success: %d records at %s%n",
                        result.length, Instant.now()); // 成功寫入的筆數與時間
            } catch (SQLException ex) {
                ex.printStackTrace(); // 可以換成 log
            } finally {
                buffer.clear();
            }
        }

        @Override
        public void close() throws Exception {
            flush();
            if (stmt != null) stmt.close();
            if (conn != null) conn.close();
        }
    }
}
