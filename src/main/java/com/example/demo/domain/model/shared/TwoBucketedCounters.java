package com.example.demo.domain.model.shared;

/**
 * TwoBucketedCounters
 *
 * 功能：
 *   - 用來統計「比例型」的滑動時間窗計數，例如：
 *       - 撤單比率 = 撤單次數 / 下單次數
 *       - 成交比率 = 成交次數 / 下單次數
 *   - 內部維護兩個獨立的滑動計數器（分子 num、分母 den）
 *   - 可以在指定的時間窗口內計算分子 / 分母的比例
 *
 * 使用場景：
 *   - 風控策略中需要計算某行為的比例，例如：
 *       - 同一帳號在 60 秒內撤單次數 / 下單次數 ≥ 90%
 */
public class TwoBucketedCounters {

    // 分子計數器 (Numerator)，用 BucketedCounter 統計分子行為次數
    private final BucketedCounter num;

    // 分母計數器 (Denominator)，用 BucketedCounter 統計分母行為次數
    private final BucketedCounter den;

    /**
     * 建構子
     *
     * @param maxWinSec 最大統計時間窗口（秒）
     * @param nowMs     當前時間（毫秒）
     */
    public TwoBucketedCounters(int maxWinSec, long nowMs) {
        num = new BucketedCounter(maxWinSec, nowMs);
        den = new BucketedCounter(maxWinSec, nowMs);
    }

    /**
     * 分子計數 +1
     *
     * @param now 當前時間（毫秒）
     */
    public void incNum(long now) {
        num.incr(now);
    }

    /**
     * 分母計數 +1
     *
     * @param now 當前時間（毫秒）
     */
    public void incDen(long now) {
        den.incr(now);
    }

    /**
     * 計算在指定時間窗口內的比例
     *
     * @param sec 計算窗口（秒）
     * @param now 當前時間（毫秒）
     * @return 比例值 (num / den)，若分母為 0 則回傳 0.0
     */
    public double ratio(int sec, long now) {
        long d = den.sum(sec, now);
        return d <= 0 ? 0.0 : (num.sum(sec, now) * 1.0 / d);
    }

    /**
     * 取得指定時間窗口內的分母累計值
     *
     * @param sec 計算窗口（秒）
     * @param now 當前時間（毫秒）
     * @return 分母累計值
     */
    public long denCount(int sec, long now) {
        return den.sum(sec, now);
    }
}
