package com.example.demo.infra.config;

import lombok.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Primary;

import java.util.List;

@Primary
@ConfigurationProperties(prefix = "redis.cluster")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RedisClusterProperties {
    private List<String> nodes;
    private int timeout = 2000;
    private int maxAttempts = 3;
    private String password;
}
