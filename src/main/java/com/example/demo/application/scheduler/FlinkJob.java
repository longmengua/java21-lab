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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/***
 * <p>Kafka → Flink → ClickHouse 批量寫入任務</p>
 *
 * 啟動流程：
 *   1. Spring Boot 啟動時掃描到本類別（@Component）
 *   2. 實例化 FlinkJob Bean
 *   3. 調用 @PostConstruct 標註的 startFlinkJob()
 *   4. 初始化 Flink 環境並啟動 DataStream 任務（非同步）
 */
@Component
@ConditionalOnProperty(name = "flink.enabled", havingValue = "true") // 只有 true 才會建立這個 Bean
public class FlinkJob {

    /**
     * 啟動 Flink Job
     * 在 Spring 容器初始化後自動執行（@PostConstruct）
     */
    @PostConstruct
    public void startFlinkJob() throws Exception {
        System.out.println("✅ Starting Flink job...");

        // 建立 Flink 執行環境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // 設定全域併行度（依 CPU 核心數調整）
        env.setParallelism(2);

        // 建立 Kafka Source（使用 Flink Kafka new API）
        KafkaSource<String> source = KafkaSource.<String>builder()
                .setBootstrapServers("single-kafka.kafka.orb.local:9092") // Kafka broker
                .setTopics("events") // 訂閱 topic
                .setGroupId("flink-consumer") // consumer group
                .setValueOnlyDeserializer(new SimpleStringSchema()) // 只取 value（String）
                .setStartingOffsets(OffsetsInitializer.earliest()) // 從最舊的 offset 開始
                .build();

        // 從 Kafka Source 建立資料流（raw String）
        DataStream<String> raw = env.fromSource(source, WatermarkStrategy.noWatermarks(), "KafkaSource");

        // JSON → 事件物件轉換
        DataStream<KafkaToClickhouseEvent> parsed = raw.map(new JsonToEvent());

        // 將事件寫入 ClickHouse（批量 Sink）
        parsed.addSink(new ClickHouseSink());

        // 非同步啟動 Job
        env.executeAsync("Spring Boot Flink KafkaToClickHouse (KafkaSource)");
    }

    /**
     * JSON 轉 KafkaToClickhouseEvent
     * 使用 RichMapFunction 方便日後加上 open/close 等初始化邏輯
     */
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

    /**
     * ClickHouse Sink（批量寫入）
     * - 透過 JDBC 連線 ClickHouse
     * - 使用 buffer 暫存事件，滿 BATCH_SIZE 筆再批量提交
     * - 會額外啟動監控執行緒，每 5 秒輸出 buffer 狀態（空/筆數）
     */
    static class ClickHouseSink extends RichSinkFunction<KafkaToClickhouseEvent> {
        private transient Connection conn; // ClickHouse JDBC 連線
        private transient PreparedStatement stmt; // 預編譯 SQL
        private final List<KafkaToClickhouseEvent> buffer = new ArrayList<>(); // 暫存事件
        private static final int BATCH_SIZE = 100; // 每批寫入筆數

        private transient ScheduledExecutorService monitorExecutor; // buffer 監控執行緒

        @Override
        public void open(Configuration parameters) throws Exception {
            // 建立 ClickHouse 連線
            conn = DriverManager.getConnection("jdbc:clickhouse://localhost:8123/default");
            stmt = conn.prepareStatement("INSERT INTO flink_sink (user_id, action, event_time) VALUES (?, ?, ?)");

            // 啟動背景監控任務，每 5 秒輸出一次 buffer 狀態
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
            // 新事件加入 buffer
            buffer.add(event);

            // 如果 buffer 滿批，觸發 flush 寫入 ClickHouse
            if (buffer.size() >= BATCH_SIZE) {
                flush();
            }
        }

        /**
         * 批量寫入 ClickHouse
         * - 使用 PreparedStatement.addBatch() 累加
         * - executeBatch() 一次送出
         * - 成功後輸出 log
         */
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
                        result.length, Instant.now());
            } catch (SQLException ex) {
                ex.printStackTrace(); // 可以替換成正式 log
            } finally {
                buffer.clear(); // 清空 buffer
            }
        }

        @Override
        public void close() throws Exception {
            // 結束前 flush 最後的 buffer
            flush();
            if (stmt != null) stmt.close();
            if (conn != null) conn.close();
        }
    }
}
