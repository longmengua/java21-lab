package com.example.demo.infra.config;

import com.example.demo.infra.props.RedisProps;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import redis.clients.jedis.*;
import redis.clients.jedis.commands.JedisCommands;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Configuration
public class JedisConfig {

    @Bean(name = "jedisClient")
    @Primary
    public JedisCommands redisClient(RedisProps props) {
        List<String> nodes = props.getNodes();
        if (nodes == null || nodes.isEmpty()) {
            throw new IllegalArgumentException("redis.nodes 不可為空");
        }

        DefaultJedisClientConfig cfg = DefaultJedisClientConfig.builder()
                .connectionTimeoutMillis(props.getTimeoutMillis())
                .socketTimeoutMillis(props.getTimeoutMillis())
                .password(blankToNull(props.getPassword()))
                .build();

        if (nodes.size() == 1) {
            // 單機模式
            String[] hp = parseHostPort(nodes.get(0));
            HostAndPort hap = new HostAndPort(hp[0], Integer.parseInt(hp[1]));
            JedisPooled jedis = new JedisPooled(hap, cfg);
            // 預檢測
            try {
                jedis.ping();
            } catch (Exception e) {
                throw new IllegalStateException("無法連線到 Redis 單機節點: " + hap, e);
            }
            return jedis;
        } else {
            // Cluster 模式
            Set<HostAndPort> clusterNodes = new HashSet<>();
            for (String n : nodes) {
                String[] hp = parseHostPort(n);
                clusterNodes.add(new HostAndPort(hp[0], Integer.parseInt(hp[1])));
            }
            JedisCluster cluster = new JedisCluster(clusterNodes, cfg, props.getMaxAttempts());
            // 預檢測
            try {
                cluster.ping();
            } catch (Exception e) {
                throw new IllegalStateException("無法連線到 Redis Cluster 節點: " + clusterNodes, e);
            }
            return cluster;
        }
    }

    private static String[] parseHostPort(String node) {
        String[] parts = node.split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException("非法節點格式: " + node + "，應為 host:port");
        }
        return parts;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
