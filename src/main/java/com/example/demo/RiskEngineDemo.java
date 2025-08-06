package com.example.demo;

import lombok.*;

import java.util.*;


// 事件定義
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
class RiskEvent {
    enum EventType {
        ORDER_PLACED, ORDER_CANCELED, TRADE_EXECUTED, POSITION_CLOSED,
        FUNDING_SETTLEMENT, API_ORDER_SUBMITTED, DAILY_ACCOUNT_SUMMARY
    }

    private EventType eventType;
    private String uid;
    private String symbol;
    private long timestamp;
    private Map<String, Object> payload;
}

// 告警定義
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
class RiskAlert {
    String uid, symbol, ruleCode, description;
    long triggerTime;
    Map<String, Object> metrics;
}

// 分發告警
class AlertDispatcher {
    public void dispatch(RiskAlert alert) {
        System.out.printf("[ALERT] %s - %s: %s | metrics=%s%n", alert.uid, alert.ruleCode, alert.description, alert.metrics);
    }
}

// 風控引擎與策略接口
interface RiskRuleHandler {
    boolean supports(RiskEvent.EventType type);
    void handle(RiskEvent event, AlertDispatcher dispatcher);
}

class RiskEngine {
    private final List<RiskRuleHandler> handlers;
    private final AlertDispatcher dispatcher = new AlertDispatcher();

    public RiskEngine(List<RiskRuleHandler> handlers) {
        this.handlers = handlers;
    }

    public void onEvent(RiskEvent event) {
        for (RiskRuleHandler handler : handlers) {
            if (handler.supports(event.getEventType())) {
                handler.handle(event, dispatcher);
            }
        }
    }
}

// === 風控規則實作 ===

// ✅ 業務邏輯：高頻“掛撤掛”行為：同一合約連續掛單後快速撤單，1min內掛撤次數≥10次
// - 指標來源：「高频交易」→「高频“挂撤挂”行为」
// - 觸發條件：使用者在 60 秒內累積 10 筆撤單紀錄
// - 處理方式：觸發後寫入告警並清空統計
class CancelFrequencyRule implements RiskRuleHandler {
    private final Map<String, List<Long>> cancelRecords = new HashMap<>();

    public boolean supports(RiskEvent.EventType type) {
        return type == RiskEvent.EventType.ORDER_CANCELED;
    }

    public void handle(RiskEvent event, AlertDispatcher dispatcher) {
        String uid = event.getUid();
        long now = event.getTimestamp();

        cancelRecords.computeIfAbsent(uid, k -> new ArrayList<>()).add(now);
        cancelRecords.get(uid).removeIf(t -> t < now - 60_000);

        if (cancelRecords.get(uid).size() >= 10) {
            dispatcher.dispatch(new RiskAlert(
                    uid, event.getSymbol(), "HF_CANCEL",
                    "高頻撤單", now,
                    Map.of("count", cancelRecords.get(uid).size())
            ));
            cancelRecords.get(uid).clear(); // 告警後清空
        }
    }
}

// ✅ 業務邏輯：跨合約套利交易：兩筆成交委託的時間間隔時間≤60s的次數大於5次
// - 用戶對不同合約在極短時間內進行成交，可能為套利行為
// - 須檢查成交事件中是否為不同 symbol
// - 滿足條件觸發報警，並清空滑動窗口
class CrossSymbolArbitrageRule implements RiskRuleHandler {
    private final Map<String, List<RiskEvent>> recentTrades = new HashMap<>();

    public boolean supports(RiskEvent.EventType type) {
        return type == RiskEvent.EventType.TRADE_EXECUTED;
    }

    public void handle(RiskEvent event, AlertDispatcher dispatcher) {
        String uid = event.getUid();
        long now = event.getTimestamp();
        String symbol = event.getSymbol();

        List<RiskEvent> trades = recentTrades.computeIfAbsent(uid, k -> new ArrayList<>());
        trades.add(event);
        trades.removeIf(e -> now - e.getTimestamp() > 60_000);

        long count = trades.stream()
                .filter(e -> !e.getSymbol().equals(symbol) && Math.abs(now - e.getTimestamp()) <= 60_000)
                .count();

        if (count > 5) {
            dispatcher.dispatch(new RiskAlert(
                    uid, symbol, "HF_CROSS_SYMBOL_ARBITRAGE",
                    "跨合約套利疑似行為", now,
                    Map.of("crossTradesIn60s", count)
            ));
            trades.clear();
        }
    }
}

// ✅ 業務邏輯：同合約超短線：同一合約上買賣成交間隔時間≤120s的次數大於5次
// ✅ 業務邏輯：同一合約上買賣成交間隔時間≤20s的次數大於2次
// - 同一用戶在同一合約上極短時間內頻繁買賣為高風險短線操作
class SameSymbolShortIntervalRule implements RiskRuleHandler {
    private final Map<String, List<Long>> tradeTimestamps = new HashMap<>();

    public boolean supports(RiskEvent.EventType type) {
        return type == RiskEvent.EventType.TRADE_EXECUTED;
    }

    public void handle(RiskEvent event, AlertDispatcher dispatcher) {
        String key = event.getUid() + "_" + event.getSymbol();
        long now = event.getTimestamp();

        List<Long> times = tradeTimestamps.computeIfAbsent(key, k -> new ArrayList<>());
        times.add(now);
        times.removeIf(t -> now - t > 120_000);

        long within20s = times.stream().filter(t -> now - t <= 20_000).count();
        long within120s = times.size();

        if (within120s > 5) {
            dispatcher.dispatch(new RiskAlert(
                    event.getUid(), event.getSymbol(), "HF_120S_SHORT",
                    "同合約超短線120秒內頻繁交易", now,
                    Map.of("count", within120s)
            ));
            times.clear();
        } else if (within20s > 2) {
            dispatcher.dispatch(new RiskAlert(
                    event.getUid(), event.getSymbol(), "HF_20S_SHORT",
                    "同合約超短線20秒內頻繁交易", now,
                    Map.of("count", within20s)
            ));
            times.clear();
        }
    }
}

// ✅ 業務邏輯：同合約高頻：相鄰開/平倉委託時間間隔≤30s的次數≥5次
class FrequentOpenCloseRule implements RiskRuleHandler {
    private final Map<String, Long> lastOrderTime = new HashMap<>();
    private final Map<String, Integer> intervalCount = new HashMap<>();

    public boolean supports(RiskEvent.EventType type) {
        return type == RiskEvent.EventType.ORDER_PLACED;
    }

    public void handle(RiskEvent event, AlertDispatcher dispatcher) {
        String key = event.getUid() + "_" + event.getSymbol();
        long now = event.getTimestamp();

        long last = lastOrderTime.getOrDefault(key, 0L);
        if (last != 0 && now - last <= 30_000) {
            int count = intervalCount.getOrDefault(key, 0) + 1;
            intervalCount.put(key, count);

            if (count >= 5) {
                dispatcher.dispatch(new RiskAlert(
                        event.getUid(), event.getSymbol(), "HF_FAST_OPEN_CLOSE",
                        "高頻開平倉行為", now,
                        Map.of("count", count)
                ));
                intervalCount.put(key, 0);
            }
        }

        lastOrderTime.put(key, now);
    }
}

// ✅ 業務邏輯：高频反向交易行为：同一合约连续1min内来回建仓和反向平仓，次数≥3次（疑似洗单行为）
class ReverseTradePatternRule implements RiskRuleHandler {
    private final Map<String, List<String>> directions = new HashMap<>();
    private final Map<String, List<Long>> timestamps = new HashMap<>();

    public boolean supports(RiskEvent.EventType type) {
        return type == RiskEvent.EventType.POSITION_CLOSED;
    }

    public void handle(RiskEvent event, AlertDispatcher dispatcher) {
        String key = event.getUid() + "_" + event.getSymbol();
        long now = event.getTimestamp();
        String dir = (String) event.getPayload().getOrDefault("direction", "LONG");

        directions.computeIfAbsent(key, k -> new ArrayList<>()).add(dir);
        timestamps.computeIfAbsent(key, k -> new ArrayList<>()).add(now);

        // 保留 60 秒內的資料
        while (!timestamps.get(key).isEmpty() && now - timestamps.get(key).get(0) > 60_000) {
            timestamps.get(key).remove(0);
            directions.get(key).remove(0);
        }

        List<String> dirList = directions.get(key);
        int flips = 0;
        for (int i = 1; i < dirList.size(); i++) {
            if (!dirList.get(i).equals(dirList.get(i - 1))) flips++;
        }

        if (flips >= 3) {
            dispatcher.dispatch(new RiskAlert(
                    event.getUid(), event.getSymbol(), "HF_REVERSE_TRADE",
                    "反向洗單行為", now,
                    Map.of("flipCount", flips)
            ));
            directions.get(key).clear();
            timestamps.get(key).clear();
        }
    }
}

// ✅ 業務邏輯：高频API多通道接入识别：同一 IP 1min内通过不同API key连续下单，≥100次/min
class IpMultiChannelRule implements RiskRuleHandler {
    private final Map<String, Map<String, Long>> ipKeyMap = new HashMap<>();

    public boolean supports(RiskEvent.EventType type) {
        return type == RiskEvent.EventType.API_ORDER_SUBMITTED;
    }

    public void handle(RiskEvent event, AlertDispatcher dispatcher) {
        String ip = (String) event.getPayload().getOrDefault("ip", "unknown");
        String apiKey = (String) event.getPayload().getOrDefault("apiKey", "default");
        long now = event.getTimestamp();

        var keyMap = ipKeyMap.computeIfAbsent(ip, k -> new HashMap<>());
        keyMap.put(apiKey, now);
        keyMap.values().removeIf(t -> now - t > 60_000);

        if (keyMap.size() >= 100) {
            dispatcher.dispatch(new RiskAlert(
                    event.getUid(), event.getSymbol(), "HF_IP_API",
                    "IP 多通道接入識別", now,
                    Map.of("apiKeyCount", keyMap.size())
            ));
            ipKeyMap.put(ip, new HashMap<>());
        }
    }
}

// ✅ 業務邏輯：同 UID 在 1 分鐘內透過不同 API key 下單超過 100 次（多通道套利）
class ApiMultiChannelRule implements RiskRuleHandler {
    private final Map<String, Map<String, Long>> uidKeyTimestamps = new HashMap<>();

    public boolean supports(RiskEvent.EventType type) {
        return type == RiskEvent.EventType.API_ORDER_SUBMITTED;
    }

    public void handle(RiskEvent event, AlertDispatcher dispatcher) {
        String uid = event.getUid();
        String apiKey = (String) event.getPayload().get("apiKey");
        long now = event.getTimestamp();

        var apiMap = uidKeyTimestamps.computeIfAbsent(uid, k -> new HashMap<>());
        apiMap.put(apiKey, now);
        apiMap.values().removeIf(t -> t < now - 60_000);

        if (apiMap.size() >= 100) {
            dispatcher.dispatch(new RiskAlert(
                    uid, event.getSymbol(), "HF_API",
                    "API 多通道接入", now,
                    Map.of("apiKeyCount", apiMap.size())
            ));
            uidKeyTimestamps.put(uid, new HashMap<>()); // 清空重新統計
        }
    }
}

// ✅ 業務邏輯：所有合約連續盈利筆數達到 10 次，即觸發告警 (所有合約連續盈利 ≥10次)
class ProfitStreakRule implements RiskRuleHandler {
    private final Map<String, Integer> profitStreak = new HashMap<>();

    public boolean supports(RiskEvent.EventType type) {
        return type == RiskEvent.EventType.POSITION_CLOSED;
    }

    public void handle(RiskEvent event, AlertDispatcher dispatcher) {
        String uid = event.getUid();
        double pnl = (Double) event.getPayload().getOrDefault("pnl", 0.0);

        if (pnl > 0) {
            profitStreak.put(uid, profitStreak.getOrDefault(uid, 0) + 1);
        } else {
            profitStreak.put(uid, 0); // 若出現虧損則重置
        }

        if (profitStreak.get(uid) >= 10) {
            dispatcher.dispatch(new RiskAlert(
                    uid, event.getSymbol(), "MKT_STREAK",
                    "連續盈利", event.getTimestamp(),
                    Map.of("streak", profitStreak.get(uid))
            ));
            profitStreak.put(uid, 0); // 告警後清空
        }
    }
}

// ✅ 業務邏輯：當撤單率 ≥ 90% 且委託總數 ≥ 20，觸發高撤單風控
// 撤單率 = 撤單數 / 總委託數
class CancelRateRule implements RiskRuleHandler {
    private final Map<String, Integer> cancelMap = new HashMap<>();
    private final Map<String, Integer> orderMap = new HashMap<>();

    public boolean supports(RiskEvent.EventType type) {
        return type == RiskEvent.EventType.ORDER_PLACED || type == RiskEvent.EventType.ORDER_CANCELED;
    }

    public void handle(RiskEvent event, AlertDispatcher dispatcher) {
        String uid = event.getUid();

        if (event.getEventType() == RiskEvent.EventType.ORDER_PLACED) {
            orderMap.put(uid, orderMap.getOrDefault(uid, 0) + 1);
        } else {
            cancelMap.put(uid, cancelMap.getOrDefault(uid, 0) + 1);
        }

        int total = orderMap.getOrDefault(uid, 1); // 避免除以0
        double rate = (double) cancelMap.getOrDefault(uid, 0) / total;

        if (total >= 20 && rate >= 0.9) {
            dispatcher.dispatch(new RiskAlert(
                    uid, event.getSymbol(), "HF_CANCEL_RATE",
                    "高撤單比例", event.getTimestamp(),
                    Map.of("cancelRate", rate)
            ));
            cancelMap.put(uid, 0);
            orderMap.put(uid, 0);
        }
    }
}

// ✅ 業務邏輯：1 分鐘內成交筆數超過 5 次即告警，用於識別刷量或高頻交易
class ShortIntervalTradeRule implements RiskRuleHandler {
    private final Map<String, List<Long>> lastTradeTimes = new HashMap<>();

    public boolean supports(RiskEvent.EventType type) {
        return type == RiskEvent.EventType.TRADE_EXECUTED;
    }

    public void handle(RiskEvent event, AlertDispatcher dispatcher) {
        String uid = event.getUid();
        long now = event.getTimestamp();

        var times = lastTradeTimes.computeIfAbsent(uid, k -> new ArrayList<>());
        times.add(now);
        times.removeIf(t -> t < now - 60_000); // 只保留近 1 分鐘內的紀錄

        if (times.size() > 5) {
            dispatcher.dispatch(new RiskAlert(
                    uid, event.getSymbol(), "HF_SHORT_TRADE",
                    "短時間多筆成交", now,
                    Map.of("count", times.size())
            ));
            times.clear();
        }
    }
}

// ✅ 業務邏輯：最近 10 筆持倉的平均持有時間 < 60 秒，表示使用者可能為極短線操作
class HoldingDurationRule implements RiskRuleHandler {
    private final Map<String, List<Long>> durations = new HashMap<>();

    public boolean supports(RiskEvent.EventType type) {
        return type == RiskEvent.EventType.POSITION_CLOSED;
    }

    public void handle(RiskEvent event, AlertDispatcher dispatcher) {
        String uid = event.getUid();
        long duration = (Long) event.getPayload().getOrDefault("duration", 0L);

        var list = durations.computeIfAbsent(uid, k -> new ArrayList<>());
        list.add(duration);

        if (list.size() >= 10) {
            long avg = list.stream().mapToLong(Long::longValue).sum() / list.size();

            if (avg < 60_000) {
                dispatcher.dispatch(new RiskAlert(
                        uid, event.getSymbol(), "HF_HOLD_SHORT",
                        "平均持倉過短", event.getTimestamp(),
                        Map.of("avg", avg)
                ));
            }
            list.clear();
        }
    }
}

// ✅ 業務邏輯：日統計資料判斷是否為高手：(平倉盈利次數 ≥20 且勝率 ≥55%, 平倉盈利次數 ≥10 且勝率 ≥70%, )
// 1. 勝場數 ≥ 20
// 2. 勝率 ≥ 55%
// 3. 總盈虧 ≥ 5000 USDT
class TopTraderRule implements RiskRuleHandler {
    public boolean supports(RiskEvent.EventType type) {
        return type == RiskEvent.EventType.DAILY_ACCOUNT_SUMMARY;
    }

    public void handle(RiskEvent event, AlertDispatcher dispatcher) {
        String uid = event.getUid();
        int wins = (int) event.getPayload().getOrDefault("winCount", 0);
        int total = (int) event.getPayload().getOrDefault("totalTrades", 1);
        double pnl = (Double) event.getPayload().getOrDefault("totalPnL", 0.0);
        double winRate = (double) wins / total;

        if (wins >= 20 && winRate >= 0.55 && pnl >= 5000.0) {
            dispatcher.dispatch(new RiskAlert(
                    uid, event.getSymbol(), "PRO_TOP",
                    "高手識別", event.getTimestamp(),
                    Map.of("winRate", winRate, "pnl", pnl)
            ));
        }
    }
}

// ✅ 業務邏輯：統計平倉時 PnL > 0 的次數，超過 10 次觸發告警
class WinningCountRule implements RiskRuleHandler {
    private final Map<String, Integer> winCount = new HashMap<>();

    public boolean supports(RiskEvent.EventType type) {
        return type == RiskEvent.EventType.POSITION_CLOSED;
    }

    public void handle(RiskEvent event, AlertDispatcher dispatcher) {
        String uid = event.getUid();
        double pnl = (Double) event.getPayload().getOrDefault("pnl", 0.0);

        if (pnl > 0) {
            winCount.put(uid, winCount.getOrDefault(uid, 0) + 1);
        }

        if (winCount.getOrDefault(uid, 0) >= 10) {
            dispatcher.dispatch(new RiskAlert(
                    uid, event.getSymbol(), "TOP_WIN_COUNT",
                    "平倉盈利次數達標", event.getTimestamp(),
                    Map.of("count", winCount.get(uid))
            ));
            winCount.put(uid, 0); // 告警後歸零
        }
    }
}

// ✅ 業務邏輯：統計平倉筆數與獲利筆數，勝率達 60% 觸發告警（最少有 20 筆）
class WinRateRule implements RiskRuleHandler {
    private final Map<String, Integer> totalClosed = new HashMap<>();
    private final Map<String, Integer> winClosed = new HashMap<>();

    public boolean supports(RiskEvent.EventType type) {
        return type == RiskEvent.EventType.POSITION_CLOSED;
    }

    public void handle(RiskEvent event, AlertDispatcher dispatcher) {
        String uid = event.getUid();
        double pnl = (Double) event.getPayload().getOrDefault("pnl", 0.0);

        totalClosed.put(uid, totalClosed.getOrDefault(uid, 0) + 1);
        if (pnl > 0) {
            winClosed.put(uid, winClosed.getOrDefault(uid, 0) + 1);
        }

        int total = totalClosed.get(uid);
        if (total >= 20) {
            double winRate = (double) winClosed.getOrDefault(uid, 0) / total;
            if (winRate >= 0.6) {
                dispatcher.dispatch(new RiskAlert(
                        uid, event.getSymbol(), "TOP_WIN_RATE",
                        "平倉勝率達標", event.getTimestamp(),
                        Map.of("winRate", winRate)
                ));
                totalClosed.put(uid, 0);
                winClosed.put(uid, 0);
            }
        }
    }
}

// ✅ 業務邏輯：總持倉時間 / 平倉筆數，平均持倉時間超過 30 分鐘視為穩健型交易者
class AvgHoldingTimeRule implements RiskRuleHandler {
    private final Map<String, Long> totalDuration = new HashMap<>();
    private final Map<String, Integer> totalCount = new HashMap<>();

    public boolean supports(RiskEvent.EventType type) {
        return type == RiskEvent.EventType.POSITION_CLOSED;
    }

    public void handle(RiskEvent event, AlertDispatcher dispatcher) {
        String uid = event.getUid();
        long duration = (Long) event.getPayload().getOrDefault("duration", 0L);

        totalDuration.put(uid, totalDuration.getOrDefault(uid, 0L) + duration);
        totalCount.put(uid, totalCount.getOrDefault(uid, 0) + 1);

        int count = totalCount.get(uid);
        if (count >= 10) {
            long avg = totalDuration.get(uid) / count;
            if (avg >= 30 * 60 * 1000) { // >= 30 分鐘
                dispatcher.dispatch(new RiskAlert(
                        uid, event.getSymbol(), "TOP_AVG_HOLD_TIME",
                        "平均持倉時間達標", event.getTimestamp(),
                        Map.of("avgHoldTimeMs", avg)
                ));
                totalDuration.put(uid, 0L);
                totalCount.put(uid, 0);
            }
        }
    }
}

// ✅ 業務邏輯：每筆平倉交易的平均盈利，達到 100 USDT 視為高手
class AvgPnLPerTradeRule implements RiskRuleHandler {
    private final Map<String, Double> totalPnl = new HashMap<>();
    private final Map<String, Integer> tradeCount = new HashMap<>();

    public boolean supports(RiskEvent.EventType type) {
        return type == RiskEvent.EventType.POSITION_CLOSED;
    }

    public void handle(RiskEvent event, AlertDispatcher dispatcher) {
        String uid = event.getUid();
        double pnl = (Double) event.getPayload().getOrDefault("pnl", 0.0);

        totalPnl.put(uid, totalPnl.getOrDefault(uid, 0.0) + pnl);
        tradeCount.put(uid, tradeCount.getOrDefault(uid, 0) + 1);

        int count = tradeCount.get(uid);
        if (count >= 10) {
            double avg = totalPnl.get(uid) / count;
            if (avg >= 100.0) {
                dispatcher.dispatch(new RiskAlert(
                        uid, event.getSymbol(), "TOP_AVG_PNL",
                        "平均單筆盈利達標", event.getTimestamp(),
                        Map.of("avgPnl", avg)
                ));
                totalPnl.put(uid, 0.0);
                tradeCount.put(uid, 0);
            }
        }
    }
}

// ✅ 業務邏輯：止盈止損實際觸發成交次數 / 設定止盈止損的次數 ≥ 80% 則視為高手
class SLTriggerRateRule implements RiskRuleHandler {
    private final Map<String, Integer> slTotal = new HashMap<>();
    private final Map<String, Integer> slTriggered = new HashMap<>();

    public boolean supports(RiskEvent.EventType type) {
        return type == RiskEvent.EventType.POSITION_CLOSED;
    }

    public void handle(RiskEvent event, AlertDispatcher dispatcher) {
        String uid = event.getUid();
        boolean hasSL = (Boolean) event.getPayload().getOrDefault("hasSL", false);
        boolean slTriggeredFlag = (Boolean) event.getPayload().getOrDefault("slTriggered", false);

        if (hasSL) {
            slTotal.put(uid, slTotal.getOrDefault(uid, 0) + 1);
            if (slTriggeredFlag) {
                slTriggered.put(uid, slTriggered.getOrDefault(uid, 0) + 1);
            }
        }

        int total = slTotal.getOrDefault(uid, 0);
        if (total >= 10) {
            double rate = (double) slTriggered.getOrDefault(uid, 0) / total;
            if (rate >= 0.8) {
                dispatcher.dispatch(new RiskAlert(
                        uid, event.getSymbol(), "TOP_SL_EXEC",
                        "止盈止損執行率達標", event.getTimestamp(),
                        Map.of("slRate", rate)
                ));
                slTotal.put(uid, 0);
                slTriggered.put(uid, 0);
            }
        }
    }
}

// ✅ 業務邏輯：平倉虧損筆數 / 總平倉筆數 ≥ 70% 則視為風險偏高，反推高手者虧損比需低於該閾值
class LossRateRule implements RiskRuleHandler {
    private final Map<String, Integer> lossCount = new HashMap<>();
    private final Map<String, Integer> totalCount = new HashMap<>();

    public boolean supports(RiskEvent.EventType type) {
        return type == RiskEvent.EventType.POSITION_CLOSED;
    }

    public void handle(RiskEvent event, AlertDispatcher dispatcher) {
        String uid = event.getUid();
        double pnl = (Double) event.getPayload().getOrDefault("pnl", 0.0);

        totalCount.put(uid, totalCount.getOrDefault(uid, 0) + 1);
        if (pnl < 0) {
            lossCount.put(uid, lossCount.getOrDefault(uid, 0) + 1);
        }

        int total = totalCount.get(uid);
        if (total >= 10) {
            double lossRate = (double) lossCount.getOrDefault(uid, 0) / total;
            if (lossRate <= 0.3) { // 虧損比例低視為高手
                dispatcher.dispatch(new RiskAlert(
                        uid, event.getSymbol(), "TOP_LOSS_RATE",
                        "虧損單比例達標", event.getTimestamp(),
                        Map.of("lossRate", lossRate)
                ));
                lossCount.put(uid, 0);
                totalCount.put(uid, 0);
            }
        }
    }
}

// ✅ 業務邏輯：加權平均槓桿倍數 = sum(leverage × price × qty) / sum(price × qty)，達標則觸發
class AvgLeverageRule implements RiskRuleHandler {
    private final Map<String, Double> weightedLeverageSum = new HashMap<>();
    private final Map<String, Double> weightedQtySum = new HashMap<>();

    public boolean supports(RiskEvent.EventType type) {
        return type == RiskEvent.EventType.POSITION_CLOSED;
    }

    public void handle(RiskEvent event, AlertDispatcher dispatcher) {
        String uid = event.getUid();
        double leverage = (Double) event.getPayload().getOrDefault("leverage", 1.0);
        double price = (Double) event.getPayload().getOrDefault("price", 0.0);
        double qty = (Double) event.getPayload().getOrDefault("qty", 0.0);

        double leverageWeighted = leverage * price * qty;
        weightedLeverageSum.put(uid, weightedLeverageSum.getOrDefault(uid, 0.0) + leverageWeighted);
        weightedQtySum.put(uid, weightedQtySum.getOrDefault(uid, 0.0) + (price * qty));

        double denom = weightedQtySum.get(uid);
        if (denom > 0) {
            double avgLeverage = weightedLeverageSum.get(uid) / denom;
            if (avgLeverage >= 10.0) {
                dispatcher.dispatch(new RiskAlert(
                        uid, event.getSymbol(), "TOP_AVG_LEVERAGE",
                        "平均槓桿倍數達標", event.getTimestamp(),
                        Map.of("avgLeverage", avgLeverage)
                ));
                weightedLeverageSum.put(uid, 0.0);
                weightedQtySum.put(uid, 0.0);
            }
        }
    }
}

// ✅ 業務邏輯：總交易筆數 / 活躍天數 ≥ 50 筆，視為高手
class DailyAvgTradeCountRule implements RiskRuleHandler {
    private final Map<String, Set<String>> activeDays = new HashMap<>(); // UID -> Set of yyyyMMdd
    private final Map<String, Integer> totalTrades = new HashMap<>();

    public boolean supports(RiskEvent.EventType type) {
        return type == RiskEvent.EventType.ORDER_PLACED ||
                type == RiskEvent.EventType.TRADE_EXECUTED ||
                type == RiskEvent.EventType.POSITION_CLOSED;
    }

    public void handle(RiskEvent event, AlertDispatcher dispatcher) {
        String uid = event.getUid();
        long ts = event.getTimestamp();
        String day = new java.text.SimpleDateFormat("yyyyMMdd").format(new Date(ts));

        activeDays.computeIfAbsent(uid, k -> new HashSet<>()).add(day);
        totalTrades.put(uid, totalTrades.getOrDefault(uid, 0) + 1);

        if (activeDays.get(uid).size() >= 3) {
            double avg = (double) totalTrades.get(uid) / activeDays.get(uid).size();
            if (avg >= 50) {
                dispatcher.dispatch(new RiskAlert(
                        uid, event.getSymbol(), "TOP_DAILY_TRADE",
                        "日均交易筆數達標", ts,
                        Map.of("avgTrades", avg)
                ));
                totalTrades.put(uid, 0);
                activeDays.put(uid, new HashSet<>());
            }
        }
    }
}

// ✅ 業務邏輯：平倉累計盈虧總額 ≥ 10000 USD 觸發
class TotalPnLRule implements RiskRuleHandler {
    private final Map<String, Double> pnlMap = new HashMap<>();

    public boolean supports(RiskEvent.EventType type) {
        return type == RiskEvent.EventType.POSITION_CLOSED;
    }

    public void handle(RiskEvent event, AlertDispatcher dispatcher) {
        String uid = event.getUid();
        double pnl = (Double) event.getPayload().getOrDefault("pnl", 0.0);

        pnlMap.put(uid, pnlMap.getOrDefault(uid, 0.0) + pnl);

        if (pnlMap.get(uid) >= 10000.0) {
            dispatcher.dispatch(new RiskAlert(
                    uid, event.getSymbol(), "TOP_TOTAL_PNL",
                    "總盈利達標", event.getTimestamp(),
                    Map.of("totalPnL", pnlMap.get(uid))
            ));
            pnlMap.put(uid, 0.0);
        }
    }
}

// ✅ 業務邏輯：累計合約交易手續費 ≥ 500 USDT 觸發
class FeeTotalRule implements RiskRuleHandler {
    private final Map<String, Double> feeMap = new HashMap<>();

    public boolean supports(RiskEvent.EventType type) {
        return type == RiskEvent.EventType.POSITION_CLOSED;
    }

    public void handle(RiskEvent event, AlertDispatcher dispatcher) {
        String uid = event.getUid();
        double fee = (Double) event.getPayload().getOrDefault("fee", 0.0);

        feeMap.put(uid, feeMap.getOrDefault(uid, 0.0) + fee);

        if (feeMap.get(uid) >= 500.0) {
            dispatcher.dispatch(new RiskAlert(
                    uid, event.getSymbol(), "TOP_TOTAL_FEE",
                    "累計手續費達標", event.getTimestamp(),
                    Map.of("totalFee", feeMap.get(uid))
            ));
            feeMap.put(uid, 0.0);
        }
    }
}

// ✅ 業務邏輯：群體操纵：多用户集中大额方向性下单 (群體操縱：多帳號集中大單方向下單)
// - X 分鐘內，同合約出現 N 名不同用戶在相近時間進行大額、同方向的下單行為
// - 例如 3 分鐘內，10 名不同用戶針對 BTCUSDT 全部買單金額 > 10 萬 USDT
class GroupManipulationRule implements RiskRuleHandler {
    private static final long WINDOW_MS = 180_000; // 3 分鐘
    private static final int USER_THRESHOLD = 10;
    private static final double ORDER_VALUE_THRESHOLD = 10_000.0; // 每人至少下 >1 萬U

    private final Map<String, List<RiskEvent>> recentOrders = new HashMap<>();

    public boolean supports(RiskEvent.EventType type) {
        return type == RiskEvent.EventType.ORDER_PLACED;
    }

    public void handle(RiskEvent event, AlertDispatcher dispatcher) {
        String symbol = event.getSymbol();
        long now = event.getTimestamp();
        String direction = (String) event.getPayload().getOrDefault("side", "BUY"); // BUY or SELL
        double amount = (Double) event.getPayload().getOrDefault("value", 0.0);

        String key = symbol + "_" + direction;
        var list = recentOrders.computeIfAbsent(key, k -> new ArrayList<>());
        list.add(event);
        list.removeIf(e -> now - e.getTimestamp() > WINDOW_MS);

        Set<String> userSet = new HashSet<>();
        for (RiskEvent e : list) {
            double v = (Double) e.getPayload().getOrDefault("value", 0.0);
            if (v >= ORDER_VALUE_THRESHOLD) {
                userSet.add(e.getUid());
            }
        }

        if (userSet.size() >= USER_THRESHOLD) {
            dispatcher.dispatch(new RiskAlert(
                    "MULTI", symbol, "GROUP_MANIPULATION",
                    "疑似群體操縱下單行為", now,
                    Map.of("users", userSet.size(), "side", direction)
            ));
            list.clear();
        }
    }
}

// ✅ 業務邏輯：資金費率套利行為 (集中時間點套利（資金費率前開倉 + 後平倉)
// - 在資金費率結算前 X 分鐘開倉，結算後 Y 分鐘內平倉，且多次發生，視為套利
// - 且資金費用收入占總收入比例 > X%
class FundingArbitrageRule implements RiskRuleHandler {
    private static final long BEFORE_SETTLEMENT_MS = 5 * 60_000; // 5分鐘前
    private static final long AFTER_SETTLEMENT_MS = 5 * 60_000;  // 5分鐘後
    private static final int TRIGGER_COUNT = 3;
    private static final double FUNDING_RATIO_THRESHOLD = 0.5;

    private final Map<String, Integer> countMap = new HashMap<>();
    private final Map<String, Double> fundingMap = new HashMap<>();
    private final Map<String, Double> totalProfitMap = new HashMap<>();

    // 這裡需要外部配置資金費率結算時間
    private final long settlementTime = System.currentTimeMillis(); // 實際應為定時更新

    public boolean supports(RiskEvent.EventType type) {
        return type == RiskEvent.EventType.POSITION_CLOSED;
    }

    public void handle(RiskEvent event, AlertDispatcher dispatcher) {
        String uid = event.getUid();
        long openTime = (Long) event.getPayload().getOrDefault("openTime", 0L);
        long closeTime = event.getTimestamp();

        boolean openedBefore = openTime >= settlementTime - BEFORE_SETTLEMENT_MS && openTime <= settlementTime;
        boolean closedAfter = closeTime <= settlementTime + AFTER_SETTLEMENT_MS;

        if (openedBefore && closedAfter) {
            countMap.put(uid, countMap.getOrDefault(uid, 0) + 1);
        }

        double fundingIncome = (Double) event.getPayload().getOrDefault("funding", 0.0);
        double pnl = (Double) event.getPayload().getOrDefault("pnl", 0.0);
        fundingMap.put(uid, fundingMap.getOrDefault(uid, 0.0) + fundingIncome);
        totalProfitMap.put(uid, totalProfitMap.getOrDefault(uid, 0.0) + pnl);

        double ratio = totalProfitMap.get(uid) == 0 ? 0 : fundingMap.get(uid) / totalProfitMap.get(uid);

        if (countMap.getOrDefault(uid, 0) >= TRIGGER_COUNT && ratio >= FUNDING_RATIO_THRESHOLD) {
            dispatcher.dispatch(new RiskAlert(
                    uid, event.getSymbol(), "FUNDING_ARBITRAGE",
                    "資金費率套利行為", event.getTimestamp(),
                    Map.of("fundingRatio", ratio, "count", countMap.get(uid))
            ));
            countMap.put(uid, 0);
            fundingMap.put(uid, 0.0);
            totalProfitMap.put(uid, 0.0);
        }
    }
}

// ✅ 業務邏輯：白名單內的合約與用戶不計入風控
class WhiteListFilter implements RiskRuleHandler {
    private final RiskRuleHandler delegate;
    private final Set<String> uidWhiteList;
    private final Set<String> symbolWhiteList;

    public WhiteListFilter(RiskRuleHandler delegate, Set<String> uidWhiteList, Set<String> symbolWhiteList) {
        this.delegate = delegate;
        this.uidWhiteList = uidWhiteList;
        this.symbolWhiteList = symbolWhiteList;
    }

    public boolean supports(RiskEvent.EventType type) {
        return delegate.supports(type);
    }

    public void handle(RiskEvent event, AlertDispatcher dispatcher) {
        if (uidWhiteList.contains(event.getUid())) return;
        if (symbolWhiteList.contains(event.getSymbol())) return;
        delegate.handle(event, dispatcher);
    }
}

// ✅ 業務邏輯：對交易特徵明顯的用戶（委託量佔比、撤單率高、盈利率高）限速下單
// - 限速：每秒最多 M 筆（例如 1 秒最多 5 筆）
// - 偵測是否為高活躍用戶，若是則進行限速
class DynamicOrderThrottleRule implements RiskRuleHandler {
    private static final int MAX_QPS = 5;

    private final Map<String, List<Long>> orderTimestamps = new HashMap<>();
    private final Set<String> throttledUids = new HashSet<>(); // 假設已標記為高風險者

    public boolean supports(RiskEvent.EventType type) {
        return type == RiskEvent.EventType.ORDER_PLACED;
    }

    public void handle(RiskEvent event, AlertDispatcher dispatcher) {
        String uid = event.getUid();
        long now = event.getTimestamp();

        if (!throttledUids.contains(uid)) return;

        List<Long> list = orderTimestamps.computeIfAbsent(uid, k -> new ArrayList<>());
        list.add(now);
        list.removeIf(t -> now - t > 1000);

        if (list.size() > MAX_QPS) {
            dispatcher.dispatch(new RiskAlert(
                    uid, event.getSymbol(), "ORDER_QPS_LIMIT",
                    "高風險帳號限速下單", now,
                    Map.of("ordersIn1s", list.size())
            ));
            list.clear();
        }
    }
}


/**
 * <p>主程式與模擬事件</p>
 *
 * [ALERT] user1 - HF_CANCEL: 高頻撤單 | metrics={count=10}
 * [ALERT] user2 - HF_API: API 多通道接入 | metrics={apiKeyCount=100}
 * [ALERT] user3 - MKT_STREAK: 連續盈利 | metrics={streak=10}
 * [ALERT] user4 - HF_SHORT_TRADE: 短時間多筆成交 | metrics={count=6}
 * [ALERT] user5 - HF_CANCEL_RATE: 高撤單比例 | metrics={cancelRate=1.0}
 * [ALERT] user6 - HF_HOLD_SHORT: 平均持倉過短 | metrics={avg=30000}
 * [ALERT] user7 - PRO_TOP: 高手識別 | metrics={winRate=0.833..., pnl=6000.0}
 * */
public class RiskEngineDemo {
    public static void main(String[] args) {
        List<RiskRuleHandler> rules = List.of(
                // ===== 高頻交易識別 =====
                new CancelFrequencyRule(),             // 高頻“掛撤掛”行為：1分鐘內撤單 ≥ 10 次
                new CancelRateRule(),                  // 撤單率 ≥ 90% 且總委託數 ≥ 20
                new ShortIntervalTradeRule(),          // 1分鐘內成交筆數 ≥ 5（短時間多筆成交）
                new HoldingDurationRule(),             // 最近10筆平均持倉時間 < 60 秒（極短線操作）
                new ApiMultiChannelRule(),             // 同 UID 多通道 API key 下單 ≥ 100 次
                new IpMultiChannelRule(),              // 同 IP 多通道 API key 下單 ≥ 100 次
                new CrossSymbolArbitrageRule(),        // 跨合約套利：兩筆不同合約成交 ≤ 60 秒，超過 5 次
                new SameSymbolShortIntervalRule(),     // 同合約超短線：買賣成交間隔 ≤ 120s 超過 5次 或 ≤ 20s 超過 2次
                new FrequentOpenCloseRule(),           // 同合約相鄰開平倉時間間隔 ≤ 30s 次數 ≥ 5
                new ReverseTradePatternRule(),         // 高频反向交易行为（洗單）：1分鐘內連續反向交易 ≥ 3 次
                new DynamicOrderThrottleRule(),        // 對高風險帳戶進行 QPS 限速下單（每秒最多 M 筆）

                // ===== 操縱市場識別 =====
                new ProfitStreakRule(),                // 所有合約連續盈利 ≥ 10 次
                new FundingArbitrageRule(),            // 資金費率套利：結算前後建倉平倉 + 收益佔比 ≥ X%
                new GroupManipulationRule(),           // 群體操縱：多用戶同方向大額下單行為

                // ===== 高手識別指標 =====
                new TopTraderRule(),                   // 勝場 ≥ 20 且勝率 ≥ 55%、盈利 ≥ 5000U（日統計）
                new WinningCountRule(),                // 累計平倉盈利次數 ≥ 10
                new WinRateRule(),                     // 勝率 ≥ 60% 且平倉筆數 ≥ 20
                new AvgHoldingTimeRule(),              // 平均持倉時間 ≥ 30 分鐘
                new AvgPnLPerTradeRule(),              // 平均單筆盈利 ≥ 100 USDT
                new SLTriggerRateRule(),               // 止盈止損執行率 ≥ 80%
                new LossRateRule(),                    // 虧損單比例 ≤ 30%
                new AvgLeverageRule(),                 // 平均槓桿 ≤ 5X
                new DailyAvgTradeCountRule(),          // 日均交易 < 2 筆 且勝率 ≥ 75% 且總盈利 ≥ 3000U
                new TotalPnLRule(),                    // 累計總盈虧 ≥ 10000 USDT
                new FeeTotalRule()                     // 累計合約手續費 ≥ 500 USDT
        );

        RiskEngine engine = new RiskEngine(rules);
        long now = System.currentTimeMillis();

        // 模擬事件
        for (int i = 0; i < 12; i++) engine.onEvent(mock("user1", RiskEvent.EventType.ORDER_CANCELED, now + i * 1000));
        for (int i = 0; i < 101; i++) engine.onEvent(mock("user2", RiskEvent.EventType.API_ORDER_SUBMITTED, now + i * 200, Map.of("apiKey", "key" + i)));
        for (int i = 0; i < 11; i++) engine.onEvent(mock("user3", RiskEvent.EventType.POSITION_CLOSED, now + i * 500, Map.of("pnl", 100.0)));
        for (int i = 0; i < 6; i++) engine.onEvent(mock("user4", RiskEvent.EventType.TRADE_EXECUTED, now + i * 500));
        for (int i = 0; i < 22; i++) {
            engine.onEvent(mock("user5", RiskEvent.EventType.ORDER_PLACED, now + i * 100));
            engine.onEvent(mock("user5", RiskEvent.EventType.ORDER_CANCELED, now + i * 100 + 10));
        }
        for (int i = 0; i < 10; i++) engine.onEvent(mock("user6", RiskEvent.EventType.POSITION_CLOSED, now + i * 1000, Map.of("duration", 30_000L)));
        engine.onEvent(mock("user7", RiskEvent.EventType.DAILY_ACCOUNT_SUMMARY, now, Map.of("winCount", 25, "totalTrades", 30, "totalPnL", 6000.0)));
    }

    private static RiskEvent mock(String uid, RiskEvent.EventType type, long ts) {
        return mock(uid, type, ts, Map.of());
    }

    private static RiskEvent mock(String uid, RiskEvent.EventType type, long ts, Map<String, Object> payload) {
        RiskEvent e = new RiskEvent();
        e.setUid(uid); e.setEventType(type); e.setTimestamp(ts); e.setSymbol("BTCUSDT"); e.setPayload(payload);
        return e;
    }
}
