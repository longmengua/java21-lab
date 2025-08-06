package com.example.demo.infra.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

@Setter
@Getter
@ConfigurationProperties(prefix = "spring.redis.cluster")
public class RedisClusterProperties {
    private List<String> nodes;
    private int timeout = 2000;
    private int maxAttempts = 3;
    private String password;
    private boolean enabled = true;
}
