plugins {
    java
    id("org.springframework.boot") version "3.1.0" 
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.apache.rocketmq:rocketmq-spring-boot-starter:2.2.3")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("redis.clients:jedis:4.4.3")

    // Lombok
    compileOnly("org.projectlombok:lombok:1.18.30")
    annotationProcessor("org.projectlombok:lombok:1.18.30")
    testCompileOnly("org.projectlombok:lombok:1.18.30")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.30")

    // 測試相關
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.3")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.3")
}


tasks.withType<Test> {
    useJUnitPlatform()

    // 設置測試輸出顯示
    testLogging {
        showStandardStreams = true  // 顯示標準輸出
    }
}
