package com.example.demo.application.service;

import com.example.demo.application.util.RedisPubSubHelper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import redis.clients.jedis.JedisPubSub;
import redis.clients.jedis.commands.JedisCommands;

import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * RiskConfigService
 *
 * 功能：
 *  - 從 Redis 載入並快取風控配置（JSON 格式）
 *  - 提供根據策略類型（type）查詢對應的風控規則
 *  - 支援 Redis Pub/Sub，當有新配置發佈時自動重新載入（熱更新）
 *
 * 資料來源：
 *  - Redis key: risk:cfg:all        （完整風控配置 JSON）
 *  - Redis channel: risk:cfg:channel（配置更新通知）
 *
 * 使用場景：
 *  - 系統啟動時自動載入一次配置
 *  - 業務邏輯需讀取配置時，直接從快取取得，避免頻繁訪問 Redis
 *  - 配置更新後，可透過 Pub/Sub 通知本服務 reload
 */
@Service
public class RiskConfigService {

    /** Redis 文字操作介面（單機/Cluster 模式皆可透過 JedisCommands 實現） */
    private final JedisCommands redis;

    /** JSON 解析器 */
    private final ObjectMapper om = new ObjectMapper();

    /** 本地風控配置快取，AtomicReference 保證多執行緒安全與可見性 */
    private final AtomicReference<JsonNode> cache = new AtomicReference<>();

    /** Redis 存放完整風控配置的 Key */
    public static final String CFG_KEY = "risk:cfg:all";

    /** Redis Pub/Sub 頻道名稱（配置更新通知） */
    public static final String CFG_CH  = "risk:cfg:channel";

    public RiskConfigService(JedisCommands redis) {
        this.redis = redis;
    }

    /**
     * 系統啟動時：
     *  - 先載入一次配置
     *  - 開啟 Pub/Sub 訂閱，監聽配置更新事件
     */
    @PostConstruct
    public void init() {
        loadOnce();
        startSubscribe();
    }

    /**
     * 從 Redis 載入一次配置並更新本地快取
     *  - 若 Redis 無資料或資料為空，不更新
     *  - 若 JSON 格式錯誤，則忽略並保留舊快取
     */
    public void loadOnce() {
        String json = redis.get(CFG_KEY); // 直接 GET Redis Key
        if (json == null || json.isBlank()) return;
        try {
            cache.set(om.readTree(json));
            System.out.println("[RiskConfigService] 配置已載入/更新");
        } catch (Exception e) {
            System.err.println("[RiskConfigService] 配置 JSON 格式錯誤: " + e.getMessage());
        }
    }

    /**
     * 取得當前快取配置
     *  - 若快取為空，則會自動 reload 一次
     */
    public JsonNode getOrReload() {
        var cur = cache.get();
        if (cur == null) {
            loadOnce();
            cur = cache.get();
        }
        return cur;
    }

    /**
     * 根據策略類型（type）查找對應的風控規則
     *
     * 預期配置結構範例：
     * {
     *   "highfreq": {
     *       "rules": [
     *           { "type": "CancelRateLimit", "threshold": 90, ... },
     *           { "type": "HighFrequencyTrade", "threshold": 5, ... }
     *       ]
     *   }
     * }
     */
    public Optional<JsonNode> findRule(String type) {
        var root = getOrReload();
        if (root == null) return Optional.empty();
        var arr = root.path("highfreq").path("rules");
        if (!arr.isArray()) return Optional.empty();
        for (JsonNode n : arr) {
            if (type.equals(n.path("type").asText())) {
                return Optional.of(n);
            }
        }
        return Optional.empty();
    }

    /**
     * 開啟 Redis Pub/Sub 訂閱，當收到更新通知時自動 reload
     *  - 使用 Jedis 原生訂閱，另開執行緒避免阻塞主線程
     *  - 注意：Cluster 模式下，Pub/Sub 只會發送到訂閱的那個節點
     */
    private void startSubscribe() {
        Executors.newSingleThreadExecutor().submit(() -> {
            try (var jedis = RedisPubSubHelper.createRawJedisFromConfig()) { // 直連單個節點
                jedis.subscribe(new JedisPubSub() {
                    @Override
                    public void onMessage(String channel, String message) {
                        if (CFG_CH.equals(channel)) {
                            System.out.println("[RiskConfigService] 收到配置更新通知，重新載入...");
                            loadOnce();
                        }
                    }
                }, CFG_CH);
            } catch (Exception e) {
                System.err.println("[RiskConfigService] Pub/Sub 訂閱失敗: " + e.getMessage());
            }
        });
    }
}
