package com.example.demo.domain.event;

import lombok.*;

import java.io.Serializable;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class MatchEvent implements Serializable {
    private Long eventTime;         // 毫秒級時間戳
    private String eventType;       // place / cancel / match
    private Long userId;
    private String ip;
    private String symbol;          // BTCUSDT
    private String orderId;
    private String side;            // buy / sell
    private Double price;
    private Double quantity;
    private String apiKey;          // for 多通道檢測
    private Boolean isMaker;        // 是否 maker
    private Double pnl;             // 盈利，僅平倉填寫
    private Double fee;             // 手續費
    private Double leverage;        // 槓桿倍數
}
