## 建立 Clickhouse 資料表

DROP TABLE IF EXISTS flink_sink;

CREATE TABLE flink_sink (
user_id UInt64,
action String,
event_time DateTime64(3)
) ENGINE = MergeTree()
ORDER BY (event_time);

## 查詢 Clickhouse 資料表

SELECT * FROM flink_sink ORDER BY event_time DESC LIMIT 10;

## 架構、流程介紹

KafkaDataProducerJob  (每秒產 1 萬筆 JSON 事件)
│
▼
Kafka Topic: "events"
│
▼
KafkaToClickHouseJob (@PostConstruct 啟動 Flink job)
│
├── KafkaSource（讀 Kafka）
│
├── map(json → KafkaToClickhouseEvent)
│
└── ClickHouse Sink（批次寫入）
