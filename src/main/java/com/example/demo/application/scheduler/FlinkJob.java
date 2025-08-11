package com.example.demo.application.scheduler;

import com.example.demo.application.event.KafkaToClickhouseEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

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
@Slf4j
public class FlinkJob {

    /**
     * 啟動 Flink Job
     * 在 Spring 容器初始化後自動執行（@PostConstruct）
     */
    @PostConstruct
    public void startFlinkJob() throws Exception {
        // ✅ 開始執行 Flink 前打印 log
        log.info("✅ Starting Flink job...");

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
     * ⚠️ 注意：Flink 的算子在序列化/反序列化時，非 transient 欄位需要可序列化。
     *         因此與外部資源（Connection、ExecutorService）相關欄位一律標記為 transient，
     *         並在 open()/close() 生命週期中建立與釋放。
     */
    static class ClickHouseSink extends RichSinkFunction<KafkaToClickhouseEvent> {
        // --- 外部資源（在 open() 建立，在 close() 釋放） ---
        private transient Connection conn;                 // ClickHouse JDBC 連線
        private transient PreparedStatement stmt;          // 預編譯 SQL
        private transient ScheduledExecutorService monitorExecutor; // 監控排程器
        private final transient AtomicReference<ScheduledFuture<?>> monitorFutureRef = new AtomicReference<>();

        // --- 批次緩衝 ---
        private final List<KafkaToClickhouseEvent> buffer = new ArrayList<>(); // 暫存事件
        private static final int BATCH_SIZE = 100; // 每批寫入筆數

        @Override
        public void open(Configuration parameters) throws Exception {
            // 建立 ClickHouse 連線
            // 如需帳密或連線池，請改為使用 DataSource 並從外部注入
            conn = DriverManager.getConnection("jdbc:clickhouse://localhost:8123/default");
            //（可選）關閉 auto-commit 以提升批次性能；ClickHouse JDBC 通常會忽略，但保留語義
            try { conn.setAutoCommit(true); } catch (Exception ignored) {}

            // 預編譯 SQL（避免重複解析）
            stmt = conn.prepareStatement("INSERT INTO flink_sink (user_id, action, event_time) VALUES (?, ?, ?)");

            // 建立監控排程器（單執行緒，daemon），每 5 秒輸出一次 buffer 狀態
            monitorExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "ch-buffer-monitor");
                t.setDaemon(true);
                return t;
            });

            // 保存 future，關閉時可取消
            ScheduledFuture<?> future = monitorExecutor.scheduleAtFixedRate(() -> {
                try {
                    if (buffer.isEmpty()) {
                        log.info("🟡 Still waiting for data from Kafka...");
                    } else {
                        log.info("📦 Buffer size: {}", buffer.size());
                    }
                } catch (Throwable t) {
                    // 監控不應中斷主流程，記錄即可
                    log.warn("Buffer monitor error: {}", t.getMessage(), t);
                }
            }, 0, 5, TimeUnit.SECONDS);
            monitorFutureRef.set(future);
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
            if (buffer.isEmpty()) return; // 沒資料就略過
            try {
                for (KafkaToClickhouseEvent e : buffer) {
                    stmt.setLong(1, e.getUserId());
                    stmt.setString(2, e.getAction());
                    stmt.setTimestamp(3, Timestamp.from(e.getEventTime()));
                    stmt.addBatch();
                }
                int[] result = stmt.executeBatch();
                //（可選）若關閉 auto-commit，這裡要 conn.commit()
                log.info("✅ ClickHouse batch insert success: {} records at {}", result.length, Instant.now());
            } catch (SQLException ex) {
                // 詳細記錄錯誤，方便定位哪筆數據導致異常
                log.error("ClickHouse batch insert failed: {}", ex.getMessage(), ex);
                //（可選）如果有使用手動 commit，可考慮 rollback
                // try { conn.rollback(); } catch (SQLException ignored) {}
            } finally {
                buffer.clear(); // 總是清空 buffer，避免重複送
            }
        }

        @Override
        public void close() throws Exception {
            // 任務結束前，先 flush 最後的 buffer
            try {
                flush();
            } catch (Exception e) {
                log.warn("Flush on close error: {}", e.getMessage(), e);
            }

            // 1) 關閉監控排程器（先取消任務，再 shutdown，再 awaitTermination）
            if (monitorExecutor != null && !monitorExecutor.isShutdown()) {
                try {
                    ScheduledFuture<?> f = monitorFutureRef.get();
                    if (f != null && !f.isCancelled()) {
                        f.cancel(false); // 不中斷正在執行的任務，但取消後續排程
                    }
                    monitorExecutor.shutdown();
                    boolean terminated = monitorExecutor.awaitTermination(2, TimeUnit.SECONDS);
                    if (!terminated) {
                        log.warn("Monitor executor did not terminate in time, forcing shutdownNow()");
                        monitorExecutor.shutdownNow();
                    }
                } catch (InterruptedException ie) {
                    // 恢復中斷狀態，避免吞掉中斷
                    Thread.currentThread().interrupt();
                    log.warn("Interrupted while awaiting monitor executor termination", ie);
                    monitorExecutor.shutdownNow();
                } catch (Exception e) {
                    log.warn("Error during monitor executor shutdown: {}", e.getMessage(), e);
                }
            }

            // 2) 關閉 JDBC 資源（先 stmt 再 conn）
            if (stmt != null) {
                try { stmt.close(); } catch (SQLException e) {
                    log.warn("Error closing PreparedStatement: {}", e.getMessage(), e);
                }
            }
            if (conn != null) {
                try { conn.close(); } catch (SQLException e) {
                    log.warn("Error closing Connection: {}", e.getMessage(), e);
                }
            }
        }
    }
}
