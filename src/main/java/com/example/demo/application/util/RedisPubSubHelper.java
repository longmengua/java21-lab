package com.example.demo.application.util;

import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.DefaultJedisClientConfig;

public class RedisPubSubHelper {

    /**
     * 建立一個新的 Jedis 直連連線（Pub/Sub 專用）
     * 這裡你可以改成從 application.yml 讀配置
     */
    public static Jedis createRawJedisFromConfig() {
        // 測試用，這裡應該從 RedisProps 讀
        String host = "redis-single.redis.orb.local";
        int port = 6379;
        String password = null; // 如果有密碼，填進去

        var cfg = DefaultJedisClientConfig.builder()
                .password(password)
                .build();

        return new Jedis(new HostAndPort(host, port), cfg);
    }
}
