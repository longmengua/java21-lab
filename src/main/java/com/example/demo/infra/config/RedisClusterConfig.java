package com.example.demo.infra.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisCluster;

import java.util.HashSet;
import java.util.Set;

@Configuration
public class RedisClusterConfig {

    @Bean
    @Primary // <-- 可選，但保險用
    @ConfigurationProperties(prefix = "spring.redis.cluster")
    public RedisClusterProperties redisClusterProperties() {
        return new RedisClusterProperties();
    }

    @Bean
    @ConditionalOnProperty(prefix = "spring.redis.cluster", name = "enabled", havingValue = "true", matchIfMissing = true)
    public JedisCluster jedisCluster(RedisClusterProperties properties) {
        Set<HostAndPort> nodes = new HashSet<>();
        for (String node : properties.getNodes()) {
            String[] parts = node.split(":");
            nodes.add(new HostAndPort(parts[0], Integer.parseInt(parts[1])));
        }

        if (properties.getPassword() != null && !properties.getPassword().isBlank()) {
            return new JedisCluster(
                    nodes,
                    properties.getTimeout(),
                    properties.getTimeout(),
                    properties.getMaxAttempts(),
                    properties.getPassword(),
                    null
            );
        } else {
            return new JedisCluster(
                    nodes,
                    properties.getTimeout(),
                    properties.getTimeout(),
                    properties.getMaxAttempts(),
                    null
            );
        }
    }
}
