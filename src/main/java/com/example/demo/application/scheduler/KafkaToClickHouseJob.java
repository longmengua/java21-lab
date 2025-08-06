package com.example.demo.application.scheduler;

import com.example.demo.application.event.KafkaToClickhouseEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jdk.jfr.Event;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.time.Instant;
import java.util.Properties;

@Component
public class KafkaToClickHouseJob {

    @PostConstruct
    public void startFlinkJob() throws Exception {
        System.out.println("✅ Starting Flink job...");

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        Properties kafkaProps = new Properties();
        kafkaProps.setProperty("bootstrap.servers", "localhost:9092");
        kafkaProps.setProperty("group.id", "flink-consumer");

        FlinkKafkaConsumer<String> consumer = new FlinkKafkaConsumer<>(
                "events", new SimpleStringSchema(), kafkaProps);

        DataStream<String> stream = env.addSource(consumer);

        DataStream<Event> parsed = stream.map(new JsonToEvent());

        parsed.addSink(new ClickHouseSink());

        env.executeAsync("Spring Boot Flink KafkaToClickHouse");
    }

    // JSON 轉 Event
    static class JsonToEvent extends RichMapFunction<String, Event> {
        private final ObjectMapper mapper = new ObjectMapper();

        @Override
        public Event map(String json) throws Exception {
            JsonNode node = mapper.readTree(json);
            return new KafkaToClickhouseEvent(
                    node.get("user_id").asLong(),
                    node.get("action").asText(),
                    Instant.parse(node.get("event_time").asText())
            );
        }
    }

    // ClickHouse Sink
    static class ClickHouseSink extends org.apache.flink.streaming.api.functions.sink.RichSinkFunction<Event> {
        private transient Connection conn;
        private PreparedStatement stmt;

        @Override
        public void open(Configuration parameters) throws Exception {
            conn = DriverManager.getConnection("jdbc:clickhouse://localhost:8123/default");
            stmt = conn.prepareStatement("INSERT INTO flink_sink (user_id, action, event_time) VALUES (?, ?, ?)");
        }

        @Override
        public void invoke(Event event, Context context) throws Exception {
            KafkaToClickhouseEvent e = (KafkaToClickhouseEvent)event;
            stmt.setLong(1, e.getUserId());
            stmt.setString(2, e.getAction());
            stmt.setTimestamp(3, Timestamp.from(e.getEventTime()));
            stmt.executeUpdate();
        }

        @Override
        public void close() throws Exception {
            if (stmt != null) stmt.close();
            if (conn != null) conn.close();
        }
    }
}
