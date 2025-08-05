# java21-demo-app

## Summary

- new futures test and demo project

## Architecture

- com.example
  - application
    - service/          // 用例層，呼叫 domain 和 infra
  - domain
    - model/            // 實體、value object
    - repository/       // 抽象介面
    - event/            // domain event
  - infra
    - config/           // Redis、Kafka 等配置類
    - redis/            // Redis 實作類，例如 RedisCacheRepository
    - kafka/            // Kafka producer / consumer adapter
  - interfaces
    - web/              // controller 層 (REST API)
    - consumer/         // 非同步接入，例如 Kafka 消費者

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

