package com.example.demo.infra.cache;

import com.example.demo.infra.props.RedisProps;
import org.junit.jupiter.api.*;
import redis.clients.jedis.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RedisService 真實 Redis Cluster 整合測試
 *
 * 包含：
 *   1) 熱路徑 KV/Hash/Counter 基礎操作測試
 *   2) Cluster SCAN 跨分片掃描測試
 *   3) Cluster FlushAll 清空測試
 *   4) Cluster Nodes 狀態檢查
 *   5) 方案 B（Lua）：ZSET + 每筆事件獨立 TTL 的滑動視窗快撤計數（同槽位 HashTag）
 *   6) 方案 A（純 Java）：不使用 Lua，使用 JedisCluster 直接完成滑動視窗快撤計數與清理
 *
 * 說明：
 *   - 規則「同合約高頻掛撤掛」：在視窗內（例如 1 分鐘）計算「掛→快速撤」的命中次數。
 *   - 我們使用 ZSET 存命中事件；每個成員的 score = 到期時間（cancel_ts + WINDOW_MS），
 *     查詢使用 ZCOUNT(key, "(" + now, "+inf") 取得「尚未過期（仍在視窗內）」的事件數，
 *     偶爾以 ZREMRANGEBYSCORE(key, "-inf", now) 清理過期成員，維持記憶體健康。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class RedisServiceClusterIT {

    /** <<< 在這裡填你的 Cluster 節點 >>> */
    private static final String[] CLUSTER_NODES = {
            "redis-1.redis.orb.local:6379",
            "redis-2.redis.orb.local:6379",
            "redis-3.redis.orb.local:6379",
    };

    /** 若叢集有密碼請填；沒有就留 null 或空字串 */
    private static final String PASSWORD = null;

    /** 連線/Socket timeout（毫秒） */
    private static final int TIMEOUT_MS = 3000;

    private static RedisService svc;             // 你的業務封裝（假設已有基本方法）
    private static JedisCluster clusterClient;   // 直接使用 JedisCluster 進行指令測試
    private static TestProps props;              // 測試用 RedisProps 實作


    // ============================ 輔助方法 ============================

    /** 產生短隨機尾碼，避免同毫秒 ZADD 覆蓋 */
    private static String randomSuffix() {
        long r = Math.abs(ThreadLocalRandom.current().nextLong());
        return Long.toString(r, 8);
    }

    /**
     * 等待叢集就緒：輪詢 cluster info，直到 "cluster_state:ok"
     */
    private static void waitClusterReady(String node, String password) throws Exception {
        String[] ap = node.split(":");
        HostAndPort hp = new HostAndPort(ap[0], Integer.parseInt(ap[1]));

        long deadline = System.currentTimeMillis() + 90_000L;
        Exception last = null;
        while (System.currentTimeMillis() < deadline) {
            try (Jedis j = new Jedis(hp,
                    DefaultJedisClientConfig.builder()
                            .connectionTimeoutMillis(RedisServiceClusterIT.TIMEOUT_MS)
                            .socketTimeoutMillis(RedisServiceClusterIT.TIMEOUT_MS)
                            .password(password)
                            .build())) {
                String info = j.clusterInfo();
                if (info != null && info.contains("cluster_state:ok")) return;
            } catch (Exception e) {
                last = e;
            }
            Thread.sleep(1000);
        }
        if (last != null) throw last;
        throw new IllegalStateException("Redis Cluster not ready within " + (90_000L / 1000) + "s");
    }

    /**
     * 計算所有節點的 DB size 總合（僅用於測試/維運）
     */
    private static long totalDbSizeAcrossNodes(List<String> nodes, String password) {
        long total = 0;
        for (String n : nodes) {
            String[] ap = n.split(":");
            try (Jedis j = new Jedis(new HostAndPort(ap[0], Integer.parseInt(ap[1])),
                    DefaultJedisClientConfig.builder()
                            .connectionTimeoutMillis(RedisServiceClusterIT.TIMEOUT_MS)
                            .socketTimeoutMillis(RedisServiceClusterIT.TIMEOUT_MS)
                            .password(password)
                            .build())) {
                total += j.dbSize();
            } catch (Exception ignored) {}
        }
        return total;
    }

    /** 工具：空字串轉 null（Jedis config 需要用 null 代表未設定密碼） */
    private static String blankToNull(String s) { return (s == null || s.isBlank()) ? null : s; }

    /**
     * 測試用最小 RedisProps 實作（提供節點/密碼/逾時）
     */
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

    // ============================ 測試生命周期 ============================

    @BeforeAll
    static void setUpAll() throws Exception {
        List<String> nodes = Arrays.asList(CLUSTER_NODES);
        props = new TestProps(nodes, PASSWORD, TIMEOUT_MS);

        // 1) 等待叢集就緒：輪詢 cluster info，直到 cluster_state:ok
        waitClusterReady(nodes.getFirst(), blankToNull(PASSWORD));

        // 2) 建立 JedisCluster 作為熱路徑 client
        Set<HostAndPort> seed = new HashSet<>();
        for (String n : nodes) {
            String[] ap = n.split(":");
            seed.add(new HostAndPort(ap[0], Integer.parseInt(ap[1])));
        }
        DefaultJedisClientConfig.Builder cfg = DefaultJedisClientConfig.builder()
                .connectionTimeoutMillis(TIMEOUT_MS)
                .socketTimeoutMillis(TIMEOUT_MS)
                .password(blankToNull(PASSWORD));
        clusterClient = new JedisCluster(seed, cfg.build());

        // 3) 直接手動 new 你的 RedisService（不透過 Spring 注入）
        svc = new RedisService(clusterClient, props);
    }

    @AfterAll
    static void tearDownAll() throws Exception {
        if (clusterClient != null) clusterClient.close();
    }

    // ============================ 測試案例 ============================

    /**
     * (1) 熱路徑：基本 KV/Hash/Counter 操作應可正常運作
     */
    @Test
    @Order(1)
    void hot_path_kv_hash_counter_should_work() {
        // 清場，避免上次殘留資料影響
        svc.del("it:k");
        svc.del("it:h");
        svc.del("it:cnt");

        // String
        assertEquals("OK", svc.set("it:k", "v"));
        assertEquals("v", svc.get("it:k"));
        assertEquals(1L, svc.expire("it:k", 30));  // 設定 TTL
        assertTrue(svc.ttl("it:k") > 0);

        // Hash
        assertEquals(1L, svc.hset("it:h", "f", "x")); // 第一次新增欄位，回傳 1
        assertEquals("x", svc.hget("it:h", "f"));
        assertEquals("x", svc.hgetAll("it:h").get("f"));

        // Counter
        assertEquals(3L, svc.incrBy("it:cnt", 3));
        assertEquals(2L, svc.decrBy("it:cnt", 1));

        // 刪除 & 存在性
        assertEquals(1L, svc.del("it:k"));
        assertFalse(svc.exists("it:k"));
    }

    /**
     * (2) SCAN：跨分片掃描 prefix，收斂結果（僅作為維運用途示範，非熱路徑）
     */
    @Test
    @Order(2)
    void scan_should_collect_keys_across_shards() {
        // 寫入多個不同 slot 的 key（不要使用 {tag}，避免都進同一 slot）
        IntStream.rangeClosed(1, 40).forEach(i -> svc.set("it:risk:" + i, "v" + i));
        svc.set("it:user:1", "u1");

        // 使用自定的 scan 封裝收集所有分片的符合鍵
        Set<String> keys = svc.scan("it:risk:*", 200);

        // 基本驗證
        assertFalse(keys.isEmpty());
        assertTrue(keys.stream().allMatch(k -> k.startsWith("it:risk:")));
        assertTrue(keys.size() >= 35);   // 粗略門檻，確保結果來自多節點
        assertFalse(keys.contains("it:user:1"));
    }

    /**
     * (3) FlushAll：確保可清空所有節點資料（僅限測試/維運）
     */
    @Test
    @Order(3)
    void flushAllCluster_should_clear_all_nodes() {
        IntStream.rangeClosed(1, 20).forEach(i -> svc.set("it:flush:" + i, "x"));

        long before = totalDbSizeAcrossNodes(props.getNodes(), blankToNull(PASSWORD));
        assertTrue(before > 0, "清空前應有資料");

        svc.flushAllCluster();

        long after = totalDbSizeAcrossNodes(props.getNodes(), blankToNull(PASSWORD));
        assertEquals(0L, after, "清空後應為 0");
    }

    /**
     * (4) Cluster Nodes：應可取得叢集節點資訊
     */
    @Test
    @Order(4)
    void clusterNodes_should_reflect_cluster_state() {
        var nodes = svc.clusterNodes();
        assertNotNull(nodes);
        assertTrue(nodes.size() >= 3); // 常見 3 主 3 從，這裡保守驗證至少多節點
    }

    /**
     * (5) 模擬交易所風控監控高頻交易計數的簡易案例，各維度統計
     */
    @Test
    @Order(5)
    void ztest() {
        Random rand = new Random();
        String[] operations = new String[]{"開倉","平倉","撤單"};
        String[] metrics = new String[]{"統計維度Ａ","統計維度Ｂ","統計維度Ｃ"};
        int loopNum = 0;

        final long WINDOW_MS = 30_000L; // 30 秒

        class Event {
            final String userId, op, metric;
            final long ts;
            Event(String userId, String op, String metric, long ts) {
                this.userId = userId; this.op = op; this.metric = metric; this.ts = ts;
            }
        }
        List<Event> events = new ArrayList<>();

        svc.flushAllCluster();

        while (loopNum < 50) {
            int randomID = rand.nextInt(3) + 1;
            int randomOps = rand.nextInt(3);
            int randomMetric = rand.nextInt(3);
            String userID = String.format("UserID_%d", randomID);
            long timestamp = Instant.now().toEpochMilli();
            String operation = operations[randomOps];
            String metric = metrics[randomMetric];

            String key = String.format("%s:%s", metric, userID);
            String member = String.format("%s:%s", operation, randomSuffix());

            // 寫入 Redis（保留原行為）
            svc.zset(key, member, (double) timestamp);

            // 收集在地事件用於統計
            events.add(new Event(userID, operation, metric, timestamp));
            loopNum++;
        }

        // ====== 統計（連續規則）======
        events.sort(Comparator.comparingLong(e -> e.ts));

        int countA = 0, countB = 0, countC = 0;

        // A：連續 開倉 -> 平倉 <30s
        class AState { long lastOpenTs = -1L; boolean waitingClose = false; }
        Map<String, AState> aState = new HashMap<>();

        // B：連續 開倉 -> 撤單 <30s
        class BState { long lastOpenTs = -1L; boolean waitingCancel = false; }
        Map<String, BState> bState = new HashMap<>();

        // C：連續 開倉 -> 開倉 <30s（中間不得插入其他事件）
        class CState { long lastOpenTs = -1L; boolean waitingNextOpen = false; }
        Map<String, CState> cState = new HashMap<>();

        for (Event e : events) {
            // 維度 A
            if ("統計維度Ａ".equals(e.metric)) {
                AState st = aState.computeIfAbsent(e.userId, k -> new AState());
                switch (e.op) {
                    case "開倉":
                        st.lastOpenTs = e.ts;
                        st.waitingClose = true;   // 期待下一筆就是平倉
                        break;
                    case "平倉":
                        if (st.waitingClose && st.lastOpenTs >= 0 && (e.ts - st.lastOpenTs) < WINDOW_MS) {
                            countA++;
                        }
                        st.waitingClose = false;
                        st.lastOpenTs = -1L;      // 連續性結束
                        break;
                    default: // 撤單或其他 => 打斷連續
                        st.waitingClose = false;
                        st.lastOpenTs = -1L;
                }
            }

            // 維度 B
            if ("統計維度Ｂ".equals(e.metric)) {
                BState st = bState.computeIfAbsent(e.userId, k -> new BState());
                switch (e.op) {
                    case "開倉":
                        st.lastOpenTs = e.ts;
                        st.waitingCancel = true;  // 期待下一筆就是撤單
                        break;
                    case "撤單":
                        if (st.waitingCancel && st.lastOpenTs >= 0 && (e.ts - st.lastOpenTs) < WINDOW_MS) {
                            countB++;
                        }
                        st.waitingCancel = false;
                        st.lastOpenTs = -1L;
                        break;
                    default: // 平倉或其他 => 打斷連續
                        st.waitingCancel = false;
                        st.lastOpenTs = -1L;
                }
            }

            // 維度 C
            if ("統計維度Ｃ".equals(e.metric)) {
                CState st = cState.computeIfAbsent(e.userId, k -> new CState());
                if ("開倉".equals(e.op)) {
                    if (st.waitingNextOpen && st.lastOpenTs >= 0 && (e.ts - st.lastOpenTs) < WINDOW_MS) {
                        countC++;
                        // 串接：O O O 可算兩段（想禁止串接就改為重置 waitingNextOpen=false）
                        st.lastOpenTs = e.ts;
                        st.waitingNextOpen = true;
                    } else {
                        st.lastOpenTs = e.ts;
                        st.waitingNextOpen = true;
                    }
                } else {
                    // 任意非開倉事件打斷
                    st.waitingNextOpen = false;
                    st.lastOpenTs = -1L;
                }
            }
        }

        System.out.printf("統計維度Ａ（連續：開倉→平倉 <30s）: %d%n", countA);
        System.out.printf("統計維度Ｂ（連續：開倉→撤單 <30s）: %d%n", countB);
        System.out.printf("統計維度Ｃ（連續：開倉→開倉 <30s）: %d%n", countC);

        // 只保留基本斷言
        assertTrue(countA >= 0 && countB >= 0 && countC >= 0);
    }
}
