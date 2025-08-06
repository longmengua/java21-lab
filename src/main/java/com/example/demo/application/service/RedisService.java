package com.example.demo.application.service;

import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import redis.clients.jedis.JedisCluster;

@Service
@ConditionalOnProperty(
    prefix = "spring.redis.cluster",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class RedisService {

    @Autowired
    private JedisCluster jedisCluster;

    public void set(String key, String value) {
        this.jedisCluster.set(key, value);
    }

    public String get(String key) {
        return this.jedisCluster.get(key);
    }

    public void delete(String key) {
        this.jedisCluster.del(key);
    }

    public String reset() {
        try {
            for (String nodeKey : this.jedisCluster.getClusterNodes().keySet()) {
                String[] parts = nodeKey.split(":");
                String host = parts[0];
                int port = Integer.parseInt(parts[1]);

                try (redis.clients.jedis.Jedis jedis = new redis.clients.jedis.Jedis(host, port)) {
                    jedis.flushAll(); // Flush this node
                }
            }
            return "All Redis cluster data flushed.";
        } catch (Exception e) {
            e.printStackTrace();
            return "Failed to flush Redis cluster: " + e.getMessage();
        }
    }
}
