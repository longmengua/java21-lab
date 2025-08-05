# java21-demo-app

## Summary

- new futures test and demo project

## Architecture

- com.example
  - application
    - command/          // 輸入參數包裝 (CQRS)
    - event/            // 用來觸發其他應用層或外部行為，如：「發送 Email」「更新快取」「寫入審計日誌」
    - scheduler/        // 排程任務
    - service/          // 應用層呼叫 domain 和 infra
  - domain
    - model/            // entity、value object、aggregate、
    - repository/       // 抽象介面
    - event/            // 業務模型的事件，如：「用戶註冊成功」「訂單已付款」「商品已下架」
  - infra
    - config/           // Redis、Kafka 等配置類
    - redis/            // Redis 實作類，例如 RedisCacheRepository
    - kafka/            // Kafka producer / consumer adapter
  - interfaces
    - consumer/         // 非同步接入，例如 Kafka 消費者
    - web/              
      - controller/     // controller 層 (REST API)
      - dto/
      - exception/
      - interceptor/
      - validator/

## Goals

- phase 1
    - try spring webFlux

- phase 2
    - Try multithreading and relevant cases.

- phase 3
    - write multi-thread util and unit test cases.
        - update from 17 to 21 for testing vitual thread.

## Gradle commands

- List up Tasks of gradle
    - ./gradlew tasks
    - ./gradlew bootRun
    - ./gradlew clean build --refresh-dependencies
    - ./gradlew clean test --stacktrace
    - ./gradlew test --info

