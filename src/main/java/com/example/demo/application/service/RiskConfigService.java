package com.example.demo.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * RiskConfigService
 *
 * 功能：
 *   - 從 Redis 載入並快取風控配置（JSON 格式）
 *   - 提供根據策略類型（type）查詢對應的風控規則
 *   - 若本地快取為空，會自動從 Redis 重新載入
 *   - 搭配 Pub/Sub（risk:cfg:channel）可實現熱更新
 *
 * 資料來源：
 *   - Redis key: risk:cfg:all        （完整風控配置 JSON）
 *   - Redis channel: risk:cfg:channel（配置更新通知）
 *
 * 使用場景：
 *   - 系統啟動時自動載入一次配置
 *   - 業務邏輯需讀取配置時，直接從快取取得，避免頻繁訪問 Redis
 *   - 配置更新後，可透過 Pub/Sub 通知本服務 reload
 */
@Service
public class RiskConfigService {

    /** Spring 提供的 Redis 文字操作模板（key/value 均為 String） */
    private final StringRedisTemplate redis;

    /** Jackson 物件映射器，用於將 JSON 字串轉為 JsonNode 物件 */
    private final ObjectMapper om = new ObjectMapper();

    /** 本地風控配置快取，AtomicReference 保證多執行緒下的可見性與原子性 */
    private final AtomicReference<JsonNode> cache = new AtomicReference<>();

    /** Redis 存放完整風控配置的 Key */
    public static final String CFG_KEY = "risk:cfg:all";

    /** Redis Pub/Sub 頻道名稱，用於接收配置更新通知 */
    public static final String CFG_CH  = "risk:cfg:channel";

    /**
     * 建構子，注入 Redis 文字操作模板
     *
     * @param redis Spring 自動注入的 StringRedisTemplate
     */
    public RiskConfigService(StringRedisTemplate redis) { this.redis = redis; }

    /**
     * 初始化方法，Spring 容器啟動後自動執行
     * - 在系統啟動時立即載入一次配置到快取
     */
    @PostConstruct
    public void init() { loadOnce(); }

    /**
     * 從 Redis 載入一次配置並更新快取
     * - 若 Redis 中無資料或資料為空，則不更新
     * - 若 JSON 格式錯誤，則忽略並保留舊快取
     */
    public void loadOnce() {
        String json = redis.opsForValue().get(CFG_KEY);
        if (json == null || json.isBlank()) return;
        try {
            cache.set(om.readTree(json)); // 將 JSON 字串解析為 JsonNode 並存入快取
        } catch (Exception e) {
            // 可以加 log.warn("Bad risk config JSON", e);
        }
    }

    /**
     * 取得當前快取配置
     * - 若快取為空，則會自動 reload 一次
     *
     * @return JsonNode 配置樹（可能為 null）
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
     *
     * @param type 策略類型（大小寫必須完全匹配）
     * @return 對應規則的 JsonNode，若未找到則回傳 Optional.empty()
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
}
