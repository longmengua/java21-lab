package com.example.demo.domain.event;

import com.example.demo.domain.model.risk.EventType;
import com.example.demo.domain.model.risk.Side;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * RiskEvent
 *
 * 功能：
 *   - 風控系統中的「原始事件模型」，用來表示一次交易相關的行為事件
 *   - 會由 Kafka / MQ 等事件流傳送進來，供風控策略引擎分析
 *
 * 使用場景：
 *   - 交易撮合系統、訂單系統等服務將事件封裝成 RiskEvent
 *   - 傳送到 Kafka (EVENTS topic)
 *   - Kafka Streams / RiskProcessor 消費並進行風控檢測
 */
@Setter // Lombok 自動生成 Setter 方法
@Getter // Lombok 自動生成 Getter 方法
public class RiskEvent {

    /** 帳號 ID，識別該事件屬於哪個用戶 */
    private String accountId;

    /** 用戶來源 IP，用於檢測多帳號共用 / 集中下單等風控場景 */
    private String ip;

    /** 交易標的（合約或交易對，例如 "BTC-USDT"） */
    private String symbol;

    /** 事件類型（下單、撤單、成交等），對應 EventType 列舉 */
    private EventType type;

    /** 訂單方向（買入 BUY / 賣出 SELL 等），對應 Side 列舉 */
    private Side side;

    /**
     * 事件發生時間
     * - 使用 Instant 儲存 UTC 時間
     * - @JsonFormat 指定序列化為字串格式，方便 JSON 傳輸與解析
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant ts;
}
