plugins {
    java
    id("org.springframework.boot") version "3.1.0"
    id("io.spring.dependency-management") version "1.1.7"
    id("jacoco")
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withJavadocJar()
    withSourcesJar()
}

tasks.named<JavaExec>("bootRun") {
    jvmArgs = listOf(
        "--add-opens", "java.base/java.lang=ALL-UNNAMED"
    )
}

tasks.withType<Test> {
    jvmArgs = listOf(
        "--add-opens", "java.base/java.lang=ALL-UNNAMED"
    )
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot 基礎
    implementation("org.springframework.boot:spring-boot-starter-web")       // Web (REST API)
    implementation("org.springframework.boot:spring-boot-starter-jdbc")      // JDBC 支援
    implementation("com.mysql:mysql-connector-j")                            // MySQL driver
    implementation("org.springframework.boot:spring-boot-devtools")          // 開發熱重載
    implementation("org.springframework.boot:spring-boot-starter-actuator")  // 健康檢查與監控

    // Flink 實時處理
    implementation("org.apache.flink:flink-streaming-java:1.17.2")
    implementation("org.apache.flink:flink-clients:1.17.2")
    implementation("org.apache.flink:flink-json:1.17.2")
    implementation("org.apache.flink:flink-connector-kafka:1.17.2")           // Flink Kafka 連接器

    // ClickHouse JDBC
    implementation("ru.yandex.clickhouse:clickhouse-jdbc:0.3.2")

    // JSON 處理
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")

    // Lombok（簡化 POJO 代碼）
    implementation("org.projectlombok:lombok:1.18.38")
    annotationProcessor("org.projectlombok:lombok:1.18.38")

    // Slf4j Logger
    implementation("org.slf4j:slf4j-api:2.0.13")

    // Redis 原生客戶端
    implementation("redis.clients:jedis:4.4.3")

    // RocketMQ
    implementation("org.apache.rocketmq:rocketmq-spring-boot-starter:2.2.3") {
        exclude(group = "com.vaadin.external.google", module = "android-json")
    }

    // MVEL 表達式引擎（風控規則）
    implementation("org.mvel:mvel2:2.4.12.Final")

    // Kafka Streams 與 Spring Kafka（處理 Kafka 流式邏輯）
    implementation("org.apache.kafka:kafka-streams")
    implementation("org.springframework.kafka:spring-kafka")

    // 測試
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
    testImplementation("io.projectreactor:reactor-test")
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        showStandardStreams = true
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// ✅ Jacoco 設定
jacoco {
    toolVersion = "0.8.11"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}
