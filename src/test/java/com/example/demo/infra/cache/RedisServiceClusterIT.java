package com.example.demo.infra.cache;

import com.example.demo.infra.props.RedisProps;
import org.junit.jupiter.api.*;
import redis.clients.jedis.*;

import java.util.*;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RedisService 真實 Redis Cluster 整合測試
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class RedisServiceClusterIT {

    /** <<< 在這裡填你的 cluster 節點 >>> */
    private static final String[] CLUSTER_NODES = {
            "redis-1.redis.orb.local:6379",
            "redis-2.redis.orb.local:6379",
            "redis-3.redis.orb.local:6379",
    };

    /** 若叢集有密碼請填；沒有就留 null 或空字串 */
    private static final String PASSWORD = null;

    /** 連線/Socket timeout（毫秒） */
    private static final int TIMEOUT_MS = 3000;

    private static RedisService svc;
    private static JedisCluster clusterClient;
    private static TestProps props;

    @BeforeAll
    static void setUpAll() throws Exception {
        List<String> nodes = Arrays.asList(CLUSTER_NODES);
        props = new TestProps(nodes, PASSWORD, TIMEOUT_MS);

        // 等待叢集就緒（對第一個節點輪詢 cluster info）
        waitClusterReady(nodes.getFirst());

        // 建立 JedisCluster 作為熱路徑 client
        Set<HostAndPort> seed = new HashSet<>();
        for (String n : nodes) {
            String[] ap = n.split(":");
            seed.add(new HostAndPort(ap[0], Integer.parseInt(ap[1])));
        }
        DefaultJedisClientConfig.Builder cfg = DefaultJedisClientConfig.builder()
                .connectionTimeoutMillis(TIMEOUT_MS)
                .socketTimeoutMillis(TIMEOUT_MS);
        clusterClient = new JedisCluster(seed, cfg.build());

        // 直接手動 new 你的 RedisService（不透過 Spring 注入）
        svc = new RedisService(clusterClient, props);
    }

    @AfterAll
    static void tearDownAll() throws Exception {
        if (clusterClient != null) clusterClient.close();
    }

    @Test
    @Order(1)
    void hot_path_kv_hash_counter_should_work() {
        assertEquals("OK", svc.set("it:k", "v"));
        assertEquals("v", svc.get("it:k"));
        assertEquals(1L, svc.expire("it:k", 30));
        assertTrue(svc.ttl("it:k") > 0);

        assertTrue(svc.hset("it:h", "f", "x") >= 0);
        assertEquals("x", svc.hget("it:h", "f"));
        assertEquals("x", svc.hgetAll("it:h").get("f"));

        assertEquals(3L, svc.incrBy("it:cnt", 3));
        assertEquals(2L, svc.decrBy("it:cnt", 1));

        assertEquals(1L, svc.del("it:k"));
        assertFalse(svc.exists("it:k"));
    }

    @Test
    @Order(2)
    void scan_should_collect_keys_across_shards() {
        // 寫多個不同 slot 的 key（不要使用 {tag}，避免都進同一 slot）
        IntStream.rangeClosed(1, 40).forEach(i -> svc.set("it:risk:" + i, "v" + i));
        svc.set("it:user:1", "u1");

        Set<String> keys = svc.scan("it:risk:*", 200);
        assertFalse(keys.isEmpty());
        assertTrue(keys.stream().allMatch(k -> k.startsWith("it:risk:")));
        assertTrue(keys.size() >= 35);   // 粗略門檻，確保來自多節點
        assertFalse(keys.contains("it:user:1"));
    }

    @Test
    @Order(3)
    void flushAllCluster_should_clear_all_nodes() {
        IntStream.rangeClosed(1, 20).forEach(i -> svc.set("it:flush:" + i, "x"));
        long before = totalDbSizeAcrossNodes(props.getNodes());
        assertTrue(before > 0);

        svc.flushAllCluster();

        long after = totalDbSizeAcrossNodes(props.getNodes());
        assertEquals(0L, after);
    }

    @Test
    @Order(4)
    void clusterNodes_should_reflect_cluster_state() {
        var nodes = svc.clusterNodes();
        assertNotNull(nodes);
        assertTrue(nodes.size() >= 3); // 通常 6 個；保守驗證至少多節點
    }

    // ----------------- Helpers -----------------

    private static void waitClusterReady(String node) throws Exception {
        String[] ap = node.split(":");
        HostAndPort hp = new HostAndPort(ap[0], Integer.parseInt(ap[1]));

        long deadline = System.currentTimeMillis() + (long) 90000;
        Exception last = null;
        while (System.currentTimeMillis() < deadline) {
            try (Jedis j = new Jedis(hp,
                    DefaultJedisClientConfig.builder()
                            .connectionTimeoutMillis(RedisServiceClusterIT.TIMEOUT_MS)
                            .socketTimeoutMillis(RedisServiceClusterIT.TIMEOUT_MS)
                            .password(null)
                            .build())) {
                String info = j.clusterInfo();
                if (info != null && info.contains("cluster_state:ok")) return;
            } catch (Exception e) {
                last = e;
            }
            Thread.sleep(1000);
        }
        if (last != null) throw last;
        throw new IllegalStateException("Redis Cluster not ready within " + ((long) 90000 /1000) + "s");
    }

    private static long totalDbSizeAcrossNodes(List<String> nodes) {
        long total = 0;
        for (String n : nodes) {
            String[] ap = n.split(":");
            try (Jedis j = new Jedis(new HostAndPort(ap[0], Integer.parseInt(ap[1])),
                    DefaultJedisClientConfig.builder()
                            .connectionTimeoutMillis(RedisServiceClusterIT.TIMEOUT_MS)
                            .socketTimeoutMillis(RedisServiceClusterIT.TIMEOUT_MS)
                            .password(null)
                            .build())) {
                total += j.dbSize();
            }
        }
        return total;
    }

    private static String blankToNull(String s) { return (s == null || s.isBlank()) ? null : s; }

    /** 測試用最小 RedisProps 實作 */
    static class TestProps extends RedisProps {
        private final List<String> nodes;
        private final String password;
        private final int timeoutMillis;

        TestProps(List<String> nodes, String password, int timeoutMillis) {
            this.nodes = nodes;
            this.password = password;
            this.timeoutMillis = timeoutMillis;
        }
        @Override public List<String> getNodes() { return nodes; }
        @Override public String getPassword() { return password; }
        @Override public int getTimeoutMillis() { return timeoutMillis; }
    }
}
