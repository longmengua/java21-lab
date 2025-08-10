package com.example.demo.infra.cache;

import com.example.demo.infra.props.RedisProps;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import redis.clients.jedis.*;
import redis.clients.jedis.commands.JedisCommands;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

import java.util.*;
import java.util.stream.Collectors;

/**
 * RedisService - Redis 單機 / Cluster 模式的統一訪問封裝
 *
 * 設計原則：
 *  1) 熱路徑（get/set/hash/counter/publish 等日常操作）全部走 JedisCommands 介面，
 *     由外部 Bean 注入，底層可為 JedisPooled（單機）或 JedisCluster（叢集）。
 *  2) 維運工具（flushAll/scan 等多節點操作）不走 JedisCluster#getClusterNodes()，
 *     而是直接使用 RedisProps.nodes 建暫時連線，單機與叢集通用。
 *  3) 連線建立使用 DefaultJedisClientConfig 避免 Jedis 4/5 建構子差異。
 *  4) 密碼驗證與超時設定由 RedisProps 提供，避免硬編碼。
 */
@Service
@ConditionalOnProperty(prefix = "redis", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RedisService {

    /** 熱路徑 Redis 客戶端（可能是 JedisPooled 或 JedisCluster） */
    private final JedisCommands client;

    /** 節點列表（單機 = 1 個，Cluster = 多個），來自配置檔 */
    private final List<String> nodes;

    /** Redis 密碼（可為 null 或空字串） */
    private final String password;

    /** 超時時間（毫秒） */
    private final int timeoutMillis;

    /**
     * 建構子
     *
     * @param client   注入的 JedisCommands 實例（單機或叢集）
     * @param props    Redis 自訂配置
     */
    public RedisService(JedisCommands client, RedisProps props) {
        this.client = client;
        this.nodes = Objects.requireNonNull(props.getNodes(), "redis.nodes 不可為空");
        this.password = props.getPassword();
        this.timeoutMillis = props.getTimeoutMillis();
    }

    // ===========================
    // 基本 Key/Value 操作（熱路徑）
    // ===========================

    /** 讀取字串值 */
    public String get(String key) { return client.get(key); }

    /** 設定字串值（無 TTL） */
    public String set(String key, String value) { return client.set(key, value); }

    /** 設定字串值並指定 TTL（秒） */
    public String set(String key, String value, int ttlSeconds) { return client.setex(key, ttlSeconds, value); }

    /** 刪除鍵 */
    public Long del(String key) { return client.del(key); }

    /** 判斷鍵是否存在 */
    public boolean exists(String key) { return client.exists(key); }

    /** 設定 TTL（秒） */
    public Long expire(String key, int seconds) { return client.expire(key, seconds); }

    /** 查詢 TTL（秒，-1 表示永久，-2 表示不存在） */
    public Long ttl(String key) { return client.ttl(key); }

    /** 原子遞增 */
    public Long incrBy(String key, long delta) { return client.incrBy(key, delta); }

    /** 原子遞減 */
    public Long decrBy(String key, long delta) { return client.decrBy(key, delta); }

    // ===========================
    // Hash 操作
    // ===========================

    /** Hash 設定單欄位值 */
    public Long hset(String key, String field, String value) { return client.hset(key, field, value); }

    /** Hash 讀取單欄位值 */
    public String hget(String key, String field) { return client.hget(key, field); }

    /** Hash 刪除欄位 */
    public Long hdel(String key, String... fields) { return client.hdel(key, fields); }

    /** Hash 讀取全部欄位值 */
    public Map<String, String> hgetAll(String key) { return client.hgetAll(key); }

    // ===========================
    // Pub/Sub
    // ===========================

    /** 發佈訊息（叢集下由驅動選擇節點） */
    public Long publish(String channel, String message) { return client.rpush(channel, message); }

    // ===========================
    // 維運工具（逐節點連線，請勿放在熱路徑）
    // ===========================

    /**
     * 全節點 FLUSHALL（清空 DB）
     * ⚠️ 強烈建議僅限於測試 / 本地環境使用
     */
    public void flushAllCluster() {
        for (HostAndPort hp : parseAllNodes(nodes)) {
            try (Jedis j = new Jedis(hp, clientConfig())) {
                j.flushAll();
            }
        }
    }

    /**
     * 跨節點 SCAN
     *
     * @param pattern      glob 匹配模式，例如 "risk:*"
     * @param countPerNode 每次返回的建議數量（只是 hint）
     * @return 合併後的 key 集合
     */
    public Set<String> scan(String pattern, int countPerNode) {
        Set<String> result = new HashSet<>();
        ScanParams params = new ScanParams();
        if (pattern != null && !pattern.isBlank()) params.match(pattern);
        if (countPerNode > 0) params.count(countPerNode);

        for (HostAndPort hp : parseAllNodes(nodes)) {
            try (Jedis j = new Jedis(hp, clientConfig())) {
                String cursor = ScanParams.SCAN_POINTER_START;
                do {
                    ScanResult<String> sr = j.scan(cursor, params);
                    result.addAll(sr.getResult());
                    cursor = sr.getCursor();
                } while (!"0".equals(cursor));
            }
        }
        return result;
    }

    /**
     * 列出節點資訊
     *
     * @return host:port 清單（排序）
     */
    public List<String> clusterNodes() {
        if (client instanceof JedisCluster jc) {
            return jc.getClusterNodes().keySet().stream().sorted().collect(Collectors.toList());
        }
        return List.of(nodes.get(0));
    }

    // ===========================
    // 私有工具方法
    // ===========================

    /** 建立 Jedis 連線配置（含密碼與超時） */
    private DefaultJedisClientConfig clientConfig() {
        return DefaultJedisClientConfig.builder()
                .connectionTimeoutMillis(timeoutMillis)
                .socketTimeoutMillis(timeoutMillis)
                .password(blankToNull(password))
                .build();
    }

    /** 將所有節點字串轉成 HostAndPort 物件 */
    private static List<HostAndPort> parseAllNodes(List<String> nodes) {
        List<HostAndPort> list = new ArrayList<>(nodes.size());
        for (String n : nodes) list.add(parseHostPort(n));
        return list;
    }

    /** 將 host:port 字串解析為 HostAndPort */
    private static HostAndPort parseHostPort(String node) {
        String[] parts = node.split(":");
        if (parts.length != 2) throw new IllegalArgumentException("非法節點格式: " + node + "，應為 host:port");
        return new HostAndPort(parts[0].trim(), Integer.parseInt(parts[1].trim()));
    }

    /** 空字串轉 null（避免 Jedis 密碼驗證異常） */
    private static String blankToNull(String s) { return (s == null || s.isBlank()) ? null : s; }
}
