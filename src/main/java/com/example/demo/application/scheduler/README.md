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
