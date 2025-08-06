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
    // ✅ Spring Boot 核心
    implementation("org.springframework.boot:spring-boot-starter")

    // ✅ Flink
    implementation("org.apache.flink:flink-streaming-java:1.17.2")
    implementation("org.apache.flink:flink-clients:1.17.2")
    implementation("org.apache.flink:flink-json:1.17.2")
    // https://mvnrepository.com/artifact/org.apache.flink/flink-connector-kafka
    implementation("org.apache.flink:flink-connector-kafka:1.17.2")

    // ✅ ClickHouse JDBC
    implementation("ru.yandex.clickhouse:clickhouse-jdbc:0.3.2")

    // ✅ JSON 處理
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")

    // ✅ lombok
    // https://mvnrepository.com/artifact/org.projectlombok/lombok
    implementation("org.projectlombok:lombok:1.18.38")

    // ✅ 其他工具（如使用）
    implementation("redis.clients:jedis:4.4.3")
    implementation("org.apache.rocketmq:rocketmq-spring-boot-starter:2.2.3") {
        exclude(group = "com.vaadin.external.google", module = "android-json")
    }

    // ✅ 測試
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
