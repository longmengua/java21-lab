package com.example.demo.application.service;

import com.example.demo.application.util.RedisPubSubHelper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;
import redis.clients.jedis.commands.JedisCommands;

import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * <p>RiskConfigService</p>
 *
 * 功能：
 *  - 從 Redis 載入並快取風控配置（JSON 格式）
 *  - 提供根據策略類型（type）查詢對應的風控規則
 *  - 支援 Redis Pub/Sub，當有新配置發佈時自動重新載入（熱更新）
 * 資料來源：
 *  - Redis key: risk:cfg:all        （完整風控配置 JSON）
 *  - Redis channel: risk:cfg:channel（配置更新通知）
 */
@Service
@Slf4j
public class RiskConfigService {

    /** Redis 文字操作介面（單機/Cluster 模式皆可透過 JedisCommands 實現） */
    private final JedisCommands redis;

    /** JSON 解析器 */
    private final ObjectMapper om = new ObjectMapper();

    /** 本地風控配置快取（多執行緒安全與可見性） */
    private final AtomicReference<JsonNode> cache = new AtomicReference<>();

    /** Redis Key/Channel 常量 */
    public static final String CFG_KEY = "risk:cfg:all";
    public static final String CFG_CH  = "risk:cfg:channel";

    // === Pub/Sub 背景執行相關（為了優雅關閉避免 IDE 警告） ===
    /** 單執行緒排程器（daemon），負責跑長連線的訂閱任務 */
    private ScheduledExecutorService subExecutor;
    /** 訂閱任務的 Future，用於關閉時取消 */
    private Future<?> subFuture;
    /** 訂閱者實例，用於關閉時呼叫 unsubscribe() */
    private volatile JedisPubSub subscriberRef;

    public RiskConfigService(JedisCommands redis) {
        this.redis = redis;
    }

    /** 系統啟動：載入一次配置 + 啟動 Pub/Sub 監聽 */
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
            log.info("[風控配置服務] 配置已載入/更新");
        } catch (Exception e) {
            log.error("[風控配置服務] 配置 JSON 格式錯誤: {}", e.getMessage(), e);
        }
    }

    /** 取得當前快取配置；若快取為空會嘗試 reload 一次 */
    public JsonNode getOrReload() {
        var cur = cache.get();
        if (cur == null) {
            loadOnce();
            cur = cache.get();
        }
        return cur;
    }

    /**
     * <p>根據策略類型（type）查找對應的風控規則</p>
     *
     * 預期配置結構：
     * {
     *   "highfreq": {
     *       "rules": [
     *           { "type": "CancelRateLimit", ... },
     *           { "type": "HighFrequencyTrade", ... }
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
     * 啟動 Redis Pub/Sub 訂閱
     *  - 使用單獨 daemon 執行緒跑阻塞式 subscribe()
     *  - 關閉時透過 unsubscribe() + 關閉 executor 達成優雅停止
     */
    private void startSubscribe() {
        // 單執行緒排程器（daemon），方便之後 awaitTermination 與優雅關閉
        this.subExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "risk-cfg-subscriber");
            t.setDaemon(true);
            return t;
        });

        // 提交阻塞式訂閱任務
        this.subFuture = subExecutor.submit(() -> {
            // 每次訂閱都用新的 Jedis 連線（避免與 JedisCommands 共用）
            try (Jedis jedis = RedisPubSubHelper.createRawJedisFromConfig()) {
                JedisPubSub subscriber = new JedisPubSub() {
                    @Override
                    public void onMessage(String channel, String message) {
                        if (CFG_CH.equals(channel)) {
                            log.info("[風控配置服務] 收到配置更新通知，重新載入...");
                            loadOnce();
                        }
                    }
                };
                // 保存實例以便關閉時呼叫 unsubscribe()
                subscriberRef = subscriber;

                log.info("[風控配置服務] 開始訂閱 Redis channel: {}", CFG_CH);
                // 阻塞直到 unsubscribe() 被呼叫或連線中斷
                jedis.subscribe(subscriber, CFG_CH);
                log.info("[風控配置服務] 已結束訂閱（可能是 unsubscribe 或連線關閉）");
            } catch (Exception e) {
                // 這裡通常是連線中斷或關閉時拋出；記錄後讓執行緒結束
                log.warn("[風控配置服務] Pub/Sub 訂閱結束/失敗: {}", e.getMessage(), e);
            } finally {
                subscriberRef = null; // 幫 GC 一把
            }
        });
    }

    /**
     * Bean 銷毀前：
     *  - 取消訂閱（unsubscribe）讓 subscribe() 返回
     *  - 優雅關閉執行緒池（shutdown → awaitTermination → 必要時 shutdownNow）
     */
    @PreDestroy
    public void shutdown() {
        // 1) 嘗試取消訂閱，讓 jedis.subscribe() 能退出
        JedisPubSub sub = this.subscriberRef;
        if (sub != null) {
            try {
                sub.unsubscribe(); // 觸發 subscribe() 返回
            } catch (Exception e) {
                log.warn("[RiskConfigService] 取消訂閱時發生錯誤: {}", e.getMessage(), e);
            }
        }

        // 2) 取消未完成的任務（不打斷正在執行的 I/O）
        if (subFuture != null && !subFuture.isDone()) {
            subFuture.cancel(false);
        }

        // 3) 優雅關閉執行緒池
        if (subExecutor != null && !subExecutor.isShutdown()) {
            subExecutor.shutdown();
            try {
                boolean ok = subExecutor.awaitTermination(2, TimeUnit.SECONDS);
                if (!ok) {
                    log.warn("[RiskConfigService] 訂閱執行緒未在期限內結束，強制關閉");
                    subExecutor.shutdownNow();
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt(); // 恢復中斷狀態
                log.warn("[RiskConfigService] 等待訂閱執行緒結束時被中斷", ie);
                subExecutor.shutdownNow();
            } catch (Exception e) {
                log.warn("[RiskConfigService] 關閉訂閱執行緒池時發生錯誤: {}", e.getMessage(), e);
                subExecutor.shutdownNow();
            }
        }
    }
}
