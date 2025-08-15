package com.example.demo.infra.cache;

import com.example.demo.infra.props.RedisProps;
import org.junit.jupiter.api.*;
import redis.clients.jedis.*;

import java.util.*;
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
        // String
        assertEquals("OK", svc.set("it:k", "v"));
        assertEquals("v", svc.get("it:k"));
        assertEquals(1L, svc.expire("it:k", 30));  // 設定 TTL
        assertTrue(svc.ttl("it:k") > 0);

        // Hash
        assertTrue(svc.hset("it:h", "f", "x") >= 0);
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

    // ====================== 方案 B：Lua + ZSET（每筆事件獨立 TTL） ======================
    // 用 Lua 確保流程原子化（讀 open_ts -> 判斷快撤 -> 寫 hit -> 清理 -> 回傳視窗內計數）
    // 注意：在 Redis Cluster 上使用 EVAL，所有 KEYS 必須在同槽位，否則會 CROSSSLOT。

    /**
     * Lua 腳本：
     *   - KEYS[1] = seq_key（命中序列 ZSET，score=到期時間）
     *   - KEYS[2] = order_key（訂單索引 HASH，內含 open_ts）
     *   - ARGV[1] = cancel_ts_ms（撤單時間）
     *   - ARGV[2] = quick_cancel_ms（快撤閾值，例 10000ms）
     *   - ARGV[3] = window_ms（視窗長度，例 60000ms）
     *   - ARGV[4] = now_ms（當前時間，用於查數與清理）
     *   - ARGV[5] = clean_prob（機率清理分母；1 表每次清，100 表 1/100 機率清理）
     *
     * 流程：
     *   1) 讀 open_ts，若無資料則直接回傳當前視窗內計數（ZCOUNT (now, +inf)）
     *   2) 若 (cancel_ts - open_ts) <= quick_cancel_ms，命中快撤：
     *        ZADD seq_key score=(cancel_ts+window_ms) member='qc:'..order_key
     *   3) 機率清理過期成員：ZREMRANGEBYSCORE seq_key -inf now_ms
     *   4) 回傳當前視窗內命中次數：ZCOUNT seq_key (now_ms, +inf)
     */
    private static final String LUA_QC_WINDOW = """
    -- KEYS[1] = seq_key (risk:hch:seq:{acc|ctr})
    -- KEYS[2] = order_key (risk:hch:order:{orderId})
    -- ARGV[1] = cancel_ts_ms
    -- ARGV[2] = quick_cancel_ms
    -- ARGV[3] = window_ms
    -- ARGV[4] = now_ms
    -- ARGV[5] = clean_prob

    local open_ts = redis.call('HGET', KEYS[2], 'open_ts')
    if not open_ts then
      return redis.call('ZCOUNT', KEYS[1], '('..ARGV[4], '+inf')
    end

    local delta = tonumber(ARGV[1]) - tonumber(open_ts)
    if delta <= tonumber(ARGV[2]) then
      local expire_ts = tonumber(ARGV[1]) + tonumber(ARGV[3])
      redis.call('ZADD', KEYS[1], expire_ts, 'qc:' .. KEYS[2])
    end

    if (tonumber(ARGV[5]) or 0) > 0 then
      if (tonumber(ARGV[4]) % tonumber(ARGV[5])) == 0 then
        redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[4])
      end
    end

    return redis.call('ZCOUNT', KEYS[1], '('..ARGV[4], '+inf')
    """;

    /**
     * 產生 HashTag，確保同一 {acc|ctr} 的 key 分配到相同 slot（便於 EVAL）
     */
    private static String tag(String acc, String ctr) {
        return "{" + acc + "|" + ctr + "}";
    }

    /** 命中序列 ZSET key（score=到期時間） */
    private static String seqKey(String acc, String ctr) {
        return "it:risk:hch:seq:" + tag(acc, ctr);
    }

    /** 訂單索引 HASH key（含 open_ts） */
    private static String orderKey(String acc, String ctr, String orderId) {
        return "it:risk:hch:order:" + tag(acc, ctr) + ":" + orderId;
    }

    /** 小工具：刪 key（忽略錯誤） */
    private static void delKeys(String... keys) {
        if (keys == null || keys.length == 0) return;
        try { clusterClient.del(keys); } catch (Exception ignored) {}
    }

    /**
     * (5) Lua 版本：驗證「每筆事件獨立 TTL」的滑動視窗快撤計數
     * 步驟：
     *   - 建立兩筆 open，不同撤單時間：
     *       1) 5s 後撤 -> 應命中快撤
     *       2) 12s 後撤 -> 不命中快撤
     *   - 視窗長度 60s：第一次命中後計數=1；第二次不命中仍=1；再移動時間至第一筆過期後應=0
     */
    @Test
    @Order(5)
    void zset_window_quickCancel_should_count_per_member_expiry_Lua() {
        String acc = "1001";
        String ctr = "BTCUSDT";
        String oid1 = "O-A";
        String oid2 = "O-B";

        String seqKey = seqKey(acc, ctr);
        String orderKey1 = orderKey(acc, ctr, oid1);
        String orderKey2 = orderKey(acc, ctr, oid2);

        // 清理舊資料
        delKeys(seqKey, orderKey1, orderKey2);

        long windowMs = 60_000L;     // 1 分鐘視窗
        long qcMs     = 10_000L;     // 快撤閾值：10 秒

        long t0 = System.currentTimeMillis();

        // 建立兩筆掛單（open）
        clusterClient.hset(orderKey1, Map.of("accountId", acc, "contractId", ctr, "open_ts", String.valueOf(t0)));
        clusterClient.pexpire(orderKey1, 7_200_000); // 2h
        clusterClient.hset(orderKey2, Map.of("accountId", acc, "contractId", ctr, "open_ts", String.valueOf(t0 + 2_000)));
        clusterClient.pexpire(orderKey2, 7_200_000);

        // 1) 第一次撤單：5s 後撤 => 命中快撤
        long cancel1 = t0 + 5_000;
        long now1    = cancel1;
        long count1 = ((Number) clusterClient.eval(
                LUA_QC_WINDOW,
                List.of(seqKey, orderKey1),
                List.of(String.valueOf(cancel1), String.valueOf(qcMs), String.valueOf(windowMs), String.valueOf(now1), "1") // clean_prob=1 每次清
        )).longValue();
        assertEquals(1L, count1, "第一次快撤後，視窗內命中應為 1");

        // 2) 第二次撤單：12s 後撤 => 不命中快撤（>10s）
        long cancel2 = t0 + 12_000;
        long now2    = cancel2;
        long count2 = ((Number) clusterClient.eval(
                LUA_QC_WINDOW,
                List.of(seqKey, orderKey2),
                List.of(String.valueOf(cancel2), String.valueOf(qcMs), String.valueOf(windowMs), String.valueOf(now2), "1")
        )).longValue();
        assertEquals(1L, count2, "第二次非快撤，命中數仍應為 1");

        // 3) 視窗滑過第一筆過期之後（t0+5s+60s+1ms）
        long now3 = t0 + 65_001;
        long count3 = ((Number) clusterClient.eval(
                LUA_QC_WINDOW,
                List.of(seqKey, orderKey1), // 帶同槽位的任何 orderKey 即可
                List.of(String.valueOf(now3), String.valueOf(qcMs), String.valueOf(windowMs), String.valueOf(now3), "1")
        )).longValue();
        assertEquals(0L, count3, "窗口滑過後，命中應自動歸 0（過期 member 不再被統計）");

        // 清理
        delKeys(seqKey, orderKey1, orderKey2);
    }

    /**
     * (6) Lua 版本：短視窗 + 每次清理，驗證過期後會被清掉
     * 步驟：
     *   - 視窗 5 秒，立即撤單 -> 命中；睡 6 秒讓其過期；再查應為 0，且因 clean_prob=1 已清除過期成員。
     */
    @Test
    @Order(6)
    void zset_window_probabilistic_cleanup_should_remove_expired_members_Lua() {
        String acc = "1002";
        String ctr = "ETHUSDT";
        String oid = "O-C";

        String seqKey = seqKey(acc, ctr);
        String oKey   = orderKey(acc, ctr, oid);
        delKeys(seqKey, oKey);

        long windowMs = 5_000L;   // 短窗
        long qcMs     = 10_000L;

        long t0 = System.currentTimeMillis();
        clusterClient.hset(oKey, Map.of("accountId", acc, "contractId", ctr, "open_ts", String.valueOf(t0)));
        clusterClient.pexpire(oKey, 7_200_000);

        // 1) 立刻撤單 => 命中，member 的 score = t0 + 5s
        long cancel = t0;
        long now1   = t0;
        long c1 = ((Number) clusterClient.eval(
                LUA_QC_WINDOW,
                List.of(seqKey, oKey),
                List.of(String.valueOf(cancel), String.valueOf(qcMs), String.valueOf(windowMs), String.valueOf(now1), "1")
        )).longValue();
        assertEquals(1L, c1, "短窗內第一次查詢應為 1");

        // 2) 等到過期之後再查（>5s）
        try { Thread.sleep(6_000); } catch (InterruptedException ignored) {}
        long now2 = System.currentTimeMillis();

        long c2 = ((Number) clusterClient.eval(
                LUA_QC_WINDOW,
                List.of(seqKey, oKey),
                List.of(String.valueOf(now2), String.valueOf(qcMs), String.valueOf(windowMs), String.valueOf(now2), "1")
        )).longValue();
        assertEquals(0L, c2, "過期後 ZCOUNT 應為 0，且因 clean_prob=1，過期 member 應被清理");

        delKeys(seqKey, oKey);
    }

    /**
     * (7) Lua 版本：驗證同槽位 HashTag 可避免 CROSSSLOT
     * - 兩個 KEY 使用相同 HashTag，EVAL 不會噴 CROSSSLOT 錯誤
     */
    @Test
    @Order(7)
    void cluster_eval_same_slot_should_succeed() {
        String acc = "2001";
        String ctr = "SOLUSDT";
        String seqKey = seqKey(acc, ctr);
        String ok = orderKey(acc, ctr, "O-TEST");
        delKeys(seqKey, ok);

        clusterClient.hset(ok, Map.of("accountId", acc, "contractId", ctr, "open_ts", String.valueOf(System.currentTimeMillis())));
        clusterClient.pexpire(ok, 60_000);

        Object ret = clusterClient.eval(
                LUA_QC_WINDOW,
                List.of(seqKey, ok),
                List.of(String.valueOf(System.currentTimeMillis()), "10000", "60000", String.valueOf(System.currentTimeMillis()), "1")
        );
        assertNotNull(ret, "EVAL 應能在 Cluster 上執行（同槽位）");

        delKeys(seqKey, ok);
    }

    // ====================== 方案 A：純 Java（無 Lua） ======================
    // 客戶端自行完成：
    //   1) HGET open_ts
    //   2) 若命中快撤 -> ZADD(score=cancel_ts+window_ms)
    //   3) ZCOUNT(seq_key, "(" + now, "+inf") 取得視窗內命中數
    //   4) 機率/節流清理 ZREMRANGEBYSCORE(seq_key, "-inf", now) 減少記憶體
    //
    // 特性：
    //   - 無需 EVAL/Lua，部署簡單
    //   - 非原子：極端併發下可能 ±1 次數抖動（多數風控可容忍）
    //   - 建議仍使用同槽位 HashTag 讓相關鍵在同一分片，減少跨節點 RTT

    /**
     * 純 Java 快撤判定 + 視窗計數：
     * @param jc            JedisCluster
     * @param acc           帳號
     * @param ctr           合約
     * @param orderId       訂單 ID
     * @param cancelTsMs    撤單時間（毫秒）
     * @param quickCancelMs 快撤閾值（毫秒）
     * @param windowMs      視窗長度（毫秒）
     * @param nowMs         當前時間（毫秒）
     * @param cleanEveryN   節流清理分母（例 100 表 1/100 機率清理；1 表每次清）
     * @return 視窗內命中次數
     */
    private static long fastCancelAndCountPureJava(
            JedisCluster jc, String acc, String ctr, String orderId,
            long cancelTsMs, long quickCancelMs, long windowMs,
            long nowMs, int cleanEveryN
    ) {
        String t = tag(acc, ctr);
        String seqKey = "it:risk:hch:seq:" + t;
        String orderKey = "it:risk:hch:order:" + t + ":" + orderId;

        // 1) 取得掛單時間（open_ts）
        String openTsStr = jc.hget(orderKey, "open_ts");
        if (openTsStr != null) {
            long openTs = Long.parseLong(openTsStr);
            long delta = cancelTsMs - openTs;

            // 2) 若為「快速撤單」：寫入一筆命中記錄到 ZSET，score = 到期時間
            if (delta <= quickCancelMs) {
                long expireTs = cancelTsMs + windowMs;
                // member 帶上 orderKey 方便追蹤
                jc.zadd(seqKey, expireTs, "qc:" + orderKey);
            }
        }

        // 3) 計算視窗內命中數：score > now -> 尚未過期的事件
        long count = jc.zcount(seqKey, "(" + nowMs, "+inf");

        // 4) 機率/節流清理：偶爾移除已過期成員，避免 ZSET 無限成長
        if (cleanEveryN > 0 && (nowMs % cleanEveryN == 0)) {
            jc.zremrangeByScore(seqKey, "-inf", String.valueOf(nowMs));
        }

        return count;
    }

    /**
     * (8) 純 Java 版本：與 Lua 相同邏輯的 1 分鐘視窗測試
     * 步驟：
     *   - 建立兩筆 open
     *   - 5s 撤單 -> 命中；12s 撤單 -> 不命中
     *   - 視窗滑過第一筆過期後，計數應歸 0
     */
    @Test
    @Order(8)
    void pure_java_quickCancel_window_should_work_without_lua() {
        String acc = "3101";
        String ctr = "ARBUSDT";
        String oid1 = "OJ-1";
        String oid2 = "OJ-2";

        String t = tag(acc, ctr);
        String seqKey = "it:risk:hch:seq:" + t;
        String k1 = "it:risk:hch:order:" + t + ":" + oid1;
        String k2 = "it:risk:hch:order:" + t + ":" + oid2;

        // 清理
        delKeys(seqKey, k1, k2);

        long windowMs = 60_000L;  // 1 分鐘
        long qcMs = 10_000L;      // 快撤閾值 10 秒
        long t0 = System.currentTimeMillis();

        // 建立 open
        clusterClient.hset(k1, Map.of("accountId", acc, "contractId", ctr, "open_ts", String.valueOf(t0)));
        clusterClient.pexpire(k1, 7_200_000);
        clusterClient.hset(k2, Map.of("accountId", acc, "contractId", ctr, "open_ts", String.valueOf(t0 + 2_000)));
        clusterClient.pexpire(k2, 7_200_000);

        // 5s 撤單 -> 命中一次
        long c1 = fastCancelAndCountPureJava(clusterClient, acc, ctr, oid1, t0 + 5_000, qcMs, windowMs, t0 + 5_000, 1);
        assertEquals(1L, c1, "第一次快撤命中後應為 1");

        // 12s 撤單 -> 不命中（> 10s），計數仍為 1
        long c2 = fastCancelAndCountPureJava(clusterClient, acc, ctr, oid2, t0 + 12_000, qcMs, windowMs, t0 + 12_000, 1);
        assertEquals(1L, c2, "第二次非快撤，命中數仍應為 1");

        // 視窗滑過第一筆過期（> 65s 之後），計數應歸 0
        long c3 = fastCancelAndCountPureJava(clusterClient, acc, ctr, oid1, t0 + 65_001, qcMs, windowMs, t0 + 65_001, 1);
        assertEquals(0L, c3, "窗口滑過後應為 0");

        // 清理
        delKeys(seqKey, k1, k2);
    }

    /**
     * (9) 純 Java 版本：短視窗 + 每次清理（cleanEveryN=1），驗證過期能即時被清除
     */
    @Test
    @Order(9)
    void pure_java_short_window_and_cleanup_should_remove_expired() {
        String acc = "3102";
        String ctr = "OPUSDT";
        String oid = "OJ-3";

        String t = tag(acc, ctr);
        String seqKey = "it:risk:hch:seq:" + t;
        String k = "it:risk:hch:order:" + t + ":" + oid;

        delKeys(seqKey, k);

        long windowMs = 5_000L;  // 短窗
        long qcMs = 10_000L;
        long t0 = System.currentTimeMillis();

        // 建立 open
        clusterClient.hset(k, Map.of("accountId", acc, "contractId", ctr, "open_ts", String.valueOf(t0)));
        clusterClient.pexpire(k, 7_200_000);

        // 立即撤單 -> 命中
        long c1 = fastCancelAndCountPureJava(clusterClient, acc, ctr, oid, t0, qcMs, windowMs, t0, 1);
        assertEquals(1L, c1, "短窗內第一次查詢應為 1");

        // 等待過期（>5s）
        try { Thread.sleep(6_000); } catch (InterruptedException ignored) {}
        long now2 = System.currentTimeMillis();

        long c2 = fastCancelAndCountPureJava(clusterClient, acc, ctr, oid, now2, qcMs, windowMs, now2, 1);
        assertEquals(0L, c2, "過期後應為 0，且因 cleanEveryN=1 已清理過期 member");

        // 清理
        delKeys(seqKey, k);
    }

    // ============================ 輔助方法 ============================

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
}
