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

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

repositories {
    mavenCentral()
}

dependencies {
    // ✅ Spring & Others
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("redis.clients:jedis:4.4.3")
    implementation("org.apache.rocketmq:rocketmq-spring-boot-starter:2.2.3") {
        exclude(group = "com.vaadin.external.google", module = "android-json")
    }

    // ✅ Flink Core
    implementation("org.apache.flink:flink-streaming-java:1.19.0")
    implementation("org.apache.flink:flink-clients:1.19.0")

    // ✅ Kafka 連接器（包含 FlinkKafkaConsumer）
//    implementation("org.apache.flink:flink-connector-kafka:1.19.0")

    // ✅ ClickHouse JDBC
    implementation("ru.yandex.clickhouse:clickhouse-jdbc:0.3.2")

    // ✅ JSON 解析
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")

    // ✅ Lombok
    compileOnly("org.projectlombok:lombok:1.18.30")
    annotationProcessor("org.projectlombok:lombok:1.18.30")

    // ✅ 測試依賴
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

// ✅ Jacoco 設定（如需覆蓋率報告，可啟用）
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

