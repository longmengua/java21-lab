package com.example.demo.interfaces.consumer.kafka;

import com.example.demo.application.service.RiskConfigService;
import com.example.demo.domain.event.RiskEvent;
import com.example.demo.domain.model.shared.TwoBucketedCounters;
import com.example.demo.domain.model.shared.BucketedCounter;
import com.example.demo.domain.event.RiskAlertEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <p>RiskProcessor</p>
 *
 * 功能：
 *   - Kafka Streams Processor 實作
 *   - 處理來自 Kafka 的交易事件（RiskEvent）
 *   - 根據配置檢測風控策略（快撤單 / 撤單率）
 *   - 觸發告警事件（RiskAlertEvent）並輸出到下游
 * 檢測策略：
 *   1) FAST_CANCEL：在短時間內連續「下單 → 撤單」的次數超過門檻
 *   2) CANCEL_RATE：在時間窗口內撤單次數 / 下單次數 ≥ 閾值
 */
@Slf4j
public class RiskProcessor implements Processor<String, RiskEvent, String, String> {

    /** 風控配置服務（讀取 Redis 快取） */
    private final RiskConfigService cfg;

    /** Kafka Streams 處理上下文，用於 forward 輸出 */
    private ProcessorContext<String, String> ctx;

    /** 記錄帳號 + 合約最後一次下單的時間（毫秒） */
    private final Map<String, Long> lastPlaceTs = new ConcurrentHashMap<>();

    /** 快撤單計數器（帳號+合約為 key） */
    private final Map<String, BucketedCounter> fastCancelMap = new ConcurrentHashMap<>();

    /** 撤單率計數器（帳號為 key，TwoBucketedCounters 用分子/分母計算比例） */
    private final Map<String, TwoBucketedCounters> cancelRateMap = new ConcurrentHashMap<>();

    /** JSON 轉換器，用於輸出告警事件 */
    private final ObjectMapper om;

    /** 最大統計秒數（用於滑動窗口計數器的容量） */
    private static final int MAX_SEC = 3600;

    public RiskProcessor(RiskConfigService cfg, ObjectMapper om) {
        this.cfg = cfg;
        this.om = om;
    }

    /** 初始化處理器，保存 Kafka Streams 的上下文 */
    @Override
    public void init(ProcessorContext<String, String> context) {
        this.ctx = context;
    }

    /**
     * 處理單筆事件
     *
     * @param record Kafka Streams 的輸入紀錄（key=accountId, value=RiskEvent）
     */
    @Override
    public void process(Record<String, RiskEvent> record) {
        final String acc = record.key();   // 帳號 ID
        final RiskEvent ev = record.value(); // 事件內容
        if (ev == null) return;

        // 取得事件時間（若無 ts 則用當前時間）
        final long now = ev.getTs() == null ? System.currentTimeMillis() : ev.getTs().toEpochMilli();
        // 取得交易對，若無則預設 "ALL"
        final String sym = ev.getSymbol() == null ? "ALL" : ev.getSymbol();

        // ✅ 每次事件開始處理時打印 log
        log.info("[風控處理器] Start processing event: key={}, type={}, symbol={}, ts={}",
                acc, ev.getType(), sym, now);

        // 1) FAST_CANCEL 快撤單檢測
        switch (ev.getType().name()) {
            case "PLACE" -> {
                // 記錄最後一次下單時間（帳號+合約為 key）
                lastPlaceTs.put(acc + "|" + sym, now);

                // 撤單率分母 +1
                cancelRateMap
                        .computeIfAbsent("ACC:" + acc, k -> new TwoBucketedCounters(MAX_SEC, now))
                        .incDen(now);
            }
            case "CANCEL" -> {
                // 撤單率分子 +1
                cancelRateMap
                        .computeIfAbsent("ACC:" + acc, k -> new TwoBucketedCounters(MAX_SEC, now))
                        .incNum(now);

                // 取得最後一次下單時間
                Long last = lastPlaceTs.get(acc + "|" + sym);
                if (last != null) {
                    // 從配置讀取快撤單檢測參數
                    int win = cfg.findRule("FAST_CANCEL")
                            .map(n -> n.path("windowSec").asInt(60))
                            .orElse(60);
                    long th = cfg.findRule("FAST_CANCEL")
                            .map(n -> n.path("threshold").asLong(10))
                            .orElse(10L);

                    // 如果撤單發生在窗口時間內
                    if (now - last <= win * 1000L) {
                        // 取得對應計數器並累加
                        var counter = fastCancelMap.computeIfAbsent(
                                "ACC:" + acc + "|SYM:" + sym,
                                k -> new BucketedCounter(MAX_SEC, now)
                        );
                        counter.incr(now);

                        // 計算窗口內的快撤次數
                        long cnt = counter.sum(win, now);

                        // 超過門檻 → 發出告警
                        if (cnt >= th) {
                            emitAlert(record, "highfreq_fast_cancel", "ACC:" + acc + "|SYM:" + sym,
                                    cnt, th, win, "FAST_CANCEL");
                        }
                    }
                }
            }
            default -> {
                // 其他事件類型忽略
            }
        }

        // 2) CANCEL_RATE 撤單率檢測
        var cr = cfg.findRule("CANCEL_RATE");
        if (cr.isPresent()) {
            int win = cr.get().path("windowSec").asInt(60);  // 時間窗口
            double thr = cr.get().path("threshold").asDouble(90) / 100.0; // 閾值百分比

            var tbc = cancelRateMap.computeIfAbsent("ACC:" + acc, k -> new TwoBucketedCounters(MAX_SEC, now));

            // 至少有 10 筆掛單數據才檢測
            if (tbc.denCount(win, now) >= 10) {
                double ratio = tbc.ratio(win, now); // 撤單比率
                if (ratio >= thr) {
                    emitAlert(record, "highfreq_cancel_rate", "ACC:" + acc,
                            Math.round(ratio * 100), Math.round(thr * 100), win, "CANCEL_RATE");
                }
            }
        }
    }

    /**
     * 發出告警事件
     *
     * @param in       原始事件紀錄
     * @param strategy 策略代碼
     * @param key      告警唯一標識
     * @param count    當前統計值
     * @param thr      閾值
     * @param win      檢測窗口秒數
     * @param msg      告警訊息
     */
    private void emitAlert(Record<String, RiskEvent> in,
                           String strategy, String key, long count, long thr, int win, String msg) {
        try {
            var a = RiskAlertEvent.simple(strategy, key, count, thr, win, msg);

            // 沒有 timestamp 時用當前時間
            long ts = (in.timestamp() != -1L) ? in.timestamp() : System.currentTimeMillis();

            // 生成 Kafka Streams 輸出紀錄
            var out = new Record<>(strategy + "|" + key, om.writeValueAsString(a), ts);

            // ✅ 在寫入 ALERTS topic 前打印 log
            log.info("[風控處理器] Emitting alert to ALERTS topic: key={}, strategy={}, details={}",
                    out.key(), strategy, a);

            // 發送到下游（例如 ALERTS topic）
            ctx.forward(out);
        } catch (Exception e) {
            // 忽略序列化或發送錯誤
            log.error("{}", e.getMessage());
        }
    }
}
