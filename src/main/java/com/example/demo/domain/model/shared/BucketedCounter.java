package com.example.demo.domain.model.shared;

import java.util.Arrays;

/**
 * BucketedCounter
 *
 * 功能：
 *   - 以「秒」為單位的滑動窗口計數器 (Sliding Window Counter)
 *   - 用固定大小的陣列 (buckets) 儲存最近 N 秒內的事件數量
 *   - 支援事件計數累加 (incr) 與時間窗口內統計 (sum)
 *
 * 使用場景：
 *   - 計算最近 X 秒內的某種行為次數，例如：
 *       - 最近 60 秒內的下單次數
 *       - 最近 30 秒內的撤單次數
 *   - 可與 TwoBucketedCounters 搭配計算比例
 */
public class BucketedCounter {
    // 支援的最大秒數（即陣列大小）
    private final int buckets;

    // 儲存每秒的計數值，arr[0] 是最早的秒數，arr[buckets-1] 是最新的秒數
    private final long[] arr;

    // 當前窗口的起始時間（毫秒，且對齊到秒）
    private long startMs;

    /**
     * 建構子
     *
     * @param maxSeconds 最大統計秒數（陣列大小）
     * @param nowMs      當前時間（毫秒）
     */
    public BucketedCounter(int maxSeconds, long nowMs) {
        buckets = maxSeconds;
        arr = new long[buckets];
        startMs = align(nowMs); // 對齊到秒
    }

    /**
     * 將時間對齊到秒（毫秒部分歸零）
     *
     * @param t 時間（毫秒）
     * @return 對齊後的時間（毫秒）
     */
    private long align(long t) {
        return t - (t % 1000);
    }

    /**
     * 滾動窗口（根據現在時間移動計數陣列）
     *
     * @param nowMs 當前時間（毫秒）
     */
    private void roll(long nowMs) {
        long aligned = align(nowMs);               // 對齊到秒
        long diff = aligned - startMs;             // 計算與起始時間的差距（毫秒）

        // 如果還沒進入下一秒（diff <= 0），不用滾動
        if (diff <= 0) return;

        // 計算需要滾動的秒數（步數）
        long steps = Math.min(diff / 1000, buckets);

        if (steps >= buckets) {
            // 如果超過陣列大小，全部清零
            Arrays.fill(arr, 0);
        } else {
            // 將舊數據往前搬移 steps 個位置，空出的部分清零
            System.arraycopy(arr, (int) steps, arr, 0, buckets - (int) steps);
            Arrays.fill(arr, buckets - (int) steps, buckets, 0);
        }

        // 更新起始時間
        startMs = aligned;
    }

    /**
     * 在當前秒累加 1 次事件
     *
     * @param nowMs 當前時間（毫秒）
     */
    public void incr(long nowMs) {
        roll(nowMs);              // 確保窗口更新到當前時間
        arr[buckets - 1]++;       // 在最新的一秒位置 +1
    }

    /**
     * 計算最近 lastSeconds 秒內的總計數
     *
     * @param lastSeconds 要計算的秒數（不能超過 buckets）
     * @param nowMs       當前時間（毫秒）
     * @return 指定時間窗口內的事件總數
     */
    public long sum(int lastSeconds, long nowMs) {
        roll(nowMs); // 確保窗口更新
        int s = Math.min(lastSeconds, buckets);
        long sum = 0;
        // 從最新的秒數往回累加
        for (int i = 0; i < s; i++) {
            sum += arr[buckets - 1 - i];
        }
        return sum;
    }
}
