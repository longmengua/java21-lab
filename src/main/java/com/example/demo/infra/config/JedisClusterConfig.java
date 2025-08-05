package com.example.demo.infra.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisCluster;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Configuration
public class JedisClusterConfig {

    private final RedisClusterProperties properties;

    public JedisClusterConfig(RedisClusterProperties properties) {
        this.properties = properties;
    }

    @Bean
    public JedisCluster jedisCluster() {
        List<String> nodeList = properties.getNodes();
        Set<HostAndPort> nodes = new HashSet<>();
        for (String node : nodeList) {
            String[] parts = node.split(":");
            nodes.add(new HostAndPort(parts[0], Integer.parseInt(parts[1])));
        }

        if (properties.getPassword() == null || properties.getPassword().isEmpty()) {
            return new JedisCluster(
                    nodes,
                    properties.getTimeout(),
                    properties.getTimeout(),
                    properties.getMaxAttempts(),
                    new GenericObjectPoolConfig<>());
        } else {
            return new JedisCluster(
                    nodes,
                    properties.getTimeout(),
                    properties.getTimeout(),
                    properties.getMaxAttempts(),
                    properties.getPassword(),
                    new GenericObjectPoolConfig<>());
        }
    }
}
