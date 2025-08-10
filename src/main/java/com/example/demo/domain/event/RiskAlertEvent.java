package com.example.demo.domain.event;

import lombok.Getter;
import java.time.Instant;

/**
 * RiskAlertEvent
 *
 * 功能：
 *   - 風控系統中用於表示一次觸發的「風控告警事件」
 *   - 包含觸發策略、關鍵標識、告警訊息、觸發數據等資訊
 *   - 可透過 simple() 工廠方法快速建立事件物件
 *
 * 使用場景：
 *   - 當風控策略判斷條件成立時，封裝為 RiskAlertEvent
 *   - 發送到 Kafka / MQ / 日誌系統，進行通知或後續處置
 */
@Getter // Lombok 自動生成 Getter 方法
public class RiskAlertEvent {

    /** 觸發告警的策略名稱，例如 "HighFrequencyTrade"、"CancelRateLimit" */
    private String strategy;

    /** 識別此告警的唯一 Key，例如帳號ID、IP、交易對等 */
    private String key;

    /** 告警詳細訊息，用於描述觸發的原因與細節 */
    private String message;

    /** 觸發時的計數值（實際觀測到的事件數量） */
    private long count;

    /** 觸發的閾值（策略設定的上限或臨界值） */
    private long threshold;

    /** 檢測使用的時間窗口（秒），例如「近 60 秒」 */
    private int windowSeconds;

    /** 告警事件的時間戳（建立時即為當下時間） */
    private Instant ts = Instant.now();

    /**
     * 簡易工廠方法，用於快速建立一個告警事件
     *
     * @param s 策略名稱
     * @param k 識別 Key
     * @param c 當前計數
     * @param t 閾值
     * @param w 檢測窗口（秒）
     * @param m 告警訊息
     * @return 建立好的 RiskAlertEvent 物件
     */
    public static RiskAlertEvent simple(String s, String k, long c, long t, int w, String m) {
        RiskAlertEvent a = new RiskAlertEvent();
        a.strategy = s;
        a.key = k;
        a.count = c;
        a.threshold = t;
        a.windowSeconds = w;
        a.message = m;
        return a;
    }
}
