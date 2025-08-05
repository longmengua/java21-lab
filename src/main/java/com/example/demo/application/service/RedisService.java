package com.example.demo.application.service;

import org.springframework.stereotype.Service;
import redis.clients.jedis.JedisCluster;

@Service
public class RedisService {

    private final JedisCluster jedisCluster;

    public RedisService(JedisCluster jedisCluster) {
        this.jedisCluster = jedisCluster;
    }

    public void set(String key, String value) {
        jedisCluster.set(key, value);
    }

    public String get(String key) {
        return jedisCluster.get(key);
    }

    public void delete(String key) {
        jedisCluster.del(key);
    }

    public String reset() {
        try {
            for (String nodeKey : jedisCluster.getClusterNodes().keySet()) {
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
