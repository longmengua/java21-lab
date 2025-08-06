package com.example.demo.infra.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisCluster;

import java.util.HashSet;
import java.util.Set;

@Configuration
@ConditionalOnProperty(prefix = "spring.redis.cluster", name = "enabled", havingValue = "true", matchIfMissing = true)
public class JedisClusterConfig {

    private final RedisClusterProperties properties;

    public JedisClusterConfig(RedisClusterProperties properties) {
        this.properties = properties;
    }

    @Bean
    public JedisCluster jedisCluster() {
        Set<HostAndPort> nodes = new HashSet<>();
        for (String node : properties.getNodes()) {
            String[] parts = node.split(":");
            String host = parts[0];
            int port = Integer.parseInt(parts[1]);
            nodes.add(new HostAndPort(host, port));
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
