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

    @Bean
    @Primary
    public JedisCommands redisClient(RedisProps props) {
        List<String> nodes = props.getNodes();
        if (nodes == null || nodes.isEmpty()) {
            throw new IllegalArgumentException("redis.nodes 不可為空");
        }

        // 用 Jedis 的 ClientConfig，避免踩建構子參數版本差異
        DefaultJedisClientConfig cfg = DefaultJedisClientConfig.builder()
                .connectionTimeoutMillis(props.getTimeoutMillis())
                .socketTimeoutMillis(props.getTimeoutMillis())
                .password(blankToNull(props.getPassword()))
                .build();

        if (nodes.size() == 1) {
            // Single
            String[] hp = parseHostPort(nodes.getFirst());
            HostAndPort hap = new HostAndPort(hp[0], Integer.parseInt(hp[1]));
            return new JedisPooled(hap, cfg);
        } else {
            // Cluster
            Set<HostAndPort> clusterNodes = new HashSet<>();
            for (String n : nodes) {
                String[] hp = parseHostPort(n);
                clusterNodes.add(new HostAndPort(hp[0], Integer.parseInt(hp[1])));
            }
            // Jedis 4/5：推薦這種簽名
            return new JedisCluster(clusterNodes, cfg, props.getMaxAttempts());
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
