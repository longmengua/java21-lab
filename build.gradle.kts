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

repositories {
    mavenCentral()
}

dependencies {
    // ===== Spring Boot 基礎 =====
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-devtools")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("com.mysql:mysql-connector-j")

    // ===== Kafka =====
    implementation("org.springframework.kafka:spring-kafka")          // Spring Kafka (Producer/Consumer + Streams 支援)
    implementation("org.apache.kafka:kafka-streams")                   // Kafka Streams 核心 API（版本與 spring-kafka 對應）

    // ===== Redis =====
    implementation("redis.clients:jedis:4.4.3")

    // ===== Flink =====
    implementation("org.apache.flink:flink-streaming-java:1.17.2")
    implementation("org.apache.flink:flink-clients:1.17.2")
    implementation("org.apache.flink:flink-json:1.17.2")
    implementation("org.apache.flink:flink-connector-kafka:1.17.2")

    // ===== ClickHouse =====
    implementation("ru.yandex.clickhouse:clickhouse-jdbc:0.3.2")

    // ===== RocketMQ =====
    implementation("org.apache.rocketmq:rocketmq-spring-boot-starter:2.2.3") {
        exclude(group = "com.vaadin.external.google", module = "android-json")
    }

    // ===== 風控規則引擎 =====
    implementation("org.mvel:mvel2:2.4.12.Final")

    // ===== JSON 處理 =====
    implementation("com.fasterxml.jackson.core:jackson-databind") // 這裡可省略，因為 Spring Boot starter-web 會自帶 Jackson
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // ===== 工具類 =====
    implementation("org.projectlombok:lombok:1.18.38")
    annotationProcessor("org.projectlombok:lombok:1.18.38")
    implementation("org.slf4j:slf4j-api:2.0.13")

    // ===== 測試 =====
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
    testImplementation("io.projectreactor:reactor-test")
}

tasks.named<JavaExec>("bootRun") {
    jvmArgs = listOf("--add-opens", "java.base/java.lang=ALL-UNNAMED")
}

tasks.withType<Test> {
    useJUnitPlatform()
    jvmArgs = listOf("--add-opens", "java.base/java.lang=ALL-UNNAMED")
    testLogging {
        showStandardStreams = true
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// ===== Jacoco 設定 =====
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
