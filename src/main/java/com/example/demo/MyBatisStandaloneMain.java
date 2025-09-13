package com.example.demo;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.java.Log;

import javax.sql.DataSource;
import java.io.*;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 以 CSV 串流 + LOAD DATA LOCAL INFILE 直接寫入 MySQL 的獨立執行程式
 */
@Log
public class MyBatisStandaloneMain {

    // 目標資料表清單
    private static final List<String> TABLES = Arrays.asList(
            "ex_order_collection_0",
            "ex_order_collection_1",
            "ex_order_collection_2",
            "ex_order_collection_3"
    );

    public static void main(String[] args) throws Exception {
        // ===== 1) 建立資料來源（JDBC URL 必須加 allowLoadLocalInfile=true 才能用 LOAD DATA LOCAL INFILE）=====
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(
                "jdbc:mysql://35.197.158.214:3306/exchange" +
                        "?characterEncoding=utf-8" +
                        "&zeroDateTimeBehavior=convertToNull" +
                        "&autoReconnect=true" +
                        "&rewriteBatchedStatements=true" +
                        "&useSSL=false" +
                        "&allowMultiQueries=true" +
                        "&allowLoadLocalInfile=true"
        );
        hc.setUsername("chainup_user");
        hc.setPassword("huPhe2I4ucRa");
        hc.setMaximumPoolSize(4);
        DataSource ds = new HikariDataSource(hc);

        // ===== 2) 匯入參數設定 =====
        long totalRows = 50_000_000L;              // 總筆數
        long perTable = totalRows / TABLES.size();  // 每張表的筆數
        long startOrderId = 20_0000_0000L;         // 起始 order_id，避免與既有資料衝突
        int rowsPerChunk = 1_000_000;              // 每次 LOAD 的 chunk 大小
        boolean includeHeader = true;              // 是否包含表頭（LOAD 時會 IGNORE 1 LINES）

        // ===== 3) 逐表匯入 =====
        for (int i = 0; i < TABLES.size(); i++) {
            String table = TABLES.get(i);
            long thisStart = startOrderId + (i * perTable);
            System.out.println(">> 開始匯入表: " + table + ", 筆數=" + perTable + ", 起始 orderId=" + thisStart);
            generateCsvDataToDb(ds, table, perTable, thisStart, rowsPerChunk, includeHeader);
            System.out.println(">> 完成表: " + table);
        }

        System.out.println("全部表格匯入完成.");
    }

    /**
     * 產生 CSV 串流並透過 LOAD DATA LOCAL INFILE 匯入 MySQL
     */
    public static void generateCsvDataToDb(
            DataSource ds,
            String tableName,     // 目標資料表
            long totalRows,       // 該表要寫入的總筆數
            long startOrderId,    // 起始 order_id
            int rowsPerChunk,     // 每批匯入的筆數
            boolean includeHeader // 是否輸出表頭
    ) throws Exception {

        final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS", Locale.US);
        final ZoneId zone = ZoneId.of("UTC"); // 使用 UTC 時區
        final String SEP = ",";               // CSV 分隔符號
        final String NEWLINE = "\n";          // 換行

        long written = 0L; // 已寫入筆數
        AtomicLong orderId = new AtomicLong(startOrderId);

        try (Connection conn = ds.getConnection()) {
            // 提速：關閉 unique/foreign key 檢查
            try (Statement s = conn.createStatement()) {
                s.execute("SET SESSION unique_checks=0");
                s.execute("SET SESSION foreign_key_checks=0");
            }

            // LOAD DATA LOCAL INFILE SQL 模板
            final String loadSql =
                    "LOAD DATA LOCAL INFILE 'stream.csv' " +
                            "INTO TABLE `" + tableName + "` " +
                            "CHARACTER SET utf8mb4 " +
                            "FIELDS TERMINATED BY ',' " +
                            "LINES TERMINATED BY '\\n' " +
                            (includeHeader ? "IGNORE 1 LINES " : "") +
                            "(" +
                            "symbol_id, order_id, user_id, side, price, volume," +
                            "fee_rate_maker, fee_rate_taker, fee, fee_coin_rate," +
                            "deal_volume, deal_money, avg_price, status, type, source, order_type," +
                            "ctime, mtime" +
                            ")";

            // 分批處理
            while (written < totalRows) {
                long remaining = totalRows - written;
                int thisChunk = (int) Math.min(remaining, rowsPerChunk);

                final long baseWritten = written; // 避免閉包捕捉錯誤

                // 建立 Piped 流管，Producer 寫，Consumer 讀
                PipedInputStream pis = new PipedInputStream(1 << 20); // 1MB buffer
                PipedOutputStream pos = new PipedOutputStream(pis);
                BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(pos, StandardCharsets.UTF_8), 1 << 20);

                // Producer 執行緒：動態產生 CSV
                Thread producer = new Thread(() -> {
                    try (BufferedWriter writer = bw) {
                        if (includeHeader) {
                            // 輸出表頭
                            writer.write(String.join(SEP, new String[]{
                                    "symbol_id", "order_id", "user_id", "side", "price", "volume",
                                    "fee_rate_maker", "fee_rate_taker", "fee", "fee_coin_rate",
                                    "deal_volume", "deal_money", "avg_price", "status", "type", "source", "order_type",
                                    "ctime", "mtime"
                            }));
                            writer.write(NEWLINE);
                        }

                        ThreadLocalRandom r = ThreadLocalRandom.current();
                        for (int i = 0; i < thisChunk; i++) {
                            // 隨機生成欄位資料
                            int symbolId = 1 + r.nextInt(1000);
                            long thisOrderId = orderId.getAndIncrement();
                            int userId = 1 + r.nextInt(5_000_000);
                            String side = r.nextBoolean() ? "BUY" : "SELL";

                            double priceD = r.nextDouble(0.0000000000000001, 50000.0);
                            double volumeD = r.nextDouble(0.0000000000000001, 200.0);
                            double feeRateMaker = r.nextDouble(0.0, 0.003);
                            double feeRateTaker = r.nextDouble(0.0, 0.003);
                            double feeCoinRate = (r.nextInt(10) == 0)
                                    ? new double[]{0.2, 0.5, 1.0}[r.nextInt(3)]
                                    : 0.0;

                            double dealVolumeD = r.nextDouble(0.0, volumeD);
                            double dealMoneyD = priceD * dealVolumeD;
                            double avgPriceD = (dealVolumeD > 0.0) ? priceD : 0.0;

                            int status = r.nextInt(0, 7);
                            int type = 1 + r.nextInt(3);
                            int source = 1 + r.nextInt(3);
                            int orderType = 1 + r.nextInt(2);

                            long backSeconds = r.nextLong(0, 365L * 24 * 3600);
                            int nanos = r.nextInt(1_000_000_000);
                            LocalDateTime ctime = LocalDateTime.now(zone).minusSeconds(backSeconds).withNano(nanos);
                            LocalDateTime mtime = ctime.plusSeconds(r.nextLong(0, 6 * 3600 + 1));

                            // 格式化數字輸出
                            String price = String.format(Locale.US, "%.16f", priceD);
                            String volume = String.format(Locale.US, "%.16f", volumeD);
                            String dealVolume = String.format(Locale.US, "%.16f", dealVolumeD);
                            String dealMoney = String.format(Locale.US, "%.16f", dealMoneyD);
                            String avgPrice = String.format(Locale.US, "%.16f", avgPriceD);

                            String fee = "0.0000000000000000"; // 固定 fee，可自行改成動態計算

                            // 寫入一行 CSV
                            writer
                                    .append(Integer.toString(symbolId)).append(SEP)
                                    .append(Long.toString(thisOrderId)).append(SEP)
                                    .append(Integer.toString(userId)).append(SEP)
                                    .append(side).append(SEP)
                                    .append(price).append(SEP)
                                    .append(volume).append(SEP)
                                    .append(String.format(Locale.US, "%.16f", feeRateMaker)).append(SEP)
                                    .append(String.format(Locale.US, "%.16f", feeRateTaker)).append(SEP)
                                    .append(fee).append(SEP)
                                    .append(String.format(Locale.US, "%.16f", feeCoinRate)).append(SEP)
                                    .append(dealVolume).append(SEP)
                                    .append(dealMoney).append(SEP)
                                    .append(avgPrice).append(SEP)
                                    .append(Integer.toString(status)).append(SEP)
                                    .append(Integer.toString(type)).append(SEP)
                                    .append(Integer.toString(source)).append(SEP)
                                    .append(Integer.toString(orderType)).append(SEP)
                                    .append(ctime.format(TS_FMT)).append(SEP)
                                    .append(mtime.format(TS_FMT))
                                    .append(NEWLINE);

                            if ((((long) i + 1) % 1_000_000L) == 0) {
                                System.out.println("  [producer] 已產生筆數: " + (baseWritten + i + 1));
                            }
                        }
                    } catch (Exception e) {
                        throw new UncheckedIOException(new java.io.IOException(e));
                    }
                }, "csv-producer-" + tableName);

                producer.start();

                // Consumer：MySQL JDBC Driver 讀取 CSV 串流
                try (PreparedStatement ps = conn.prepareStatement(loadSql)) {
                    executeLoadWithStream(ps, pis);
                } finally {
                    producer.join(); // 等待 Producer 完成
                    pis.close();
                }

                written += thisChunk;
                System.out.println("已匯入表 " + tableName + ": 本批=" + thisChunk + ", 累計=" + written);
            }

            // 還原檢查設定
            try (Statement s = conn.createStatement()) {
                s.execute("SET SESSION unique_checks=1");
                s.execute("SET SESSION foreign_key_checks=1");
            }
        }
    }

    /**
     * 利用反射呼叫 setLocalInfileInputStream，把 CSV 串流餵給 MySQL Driver
     */
    private static void executeLoadWithStream(PreparedStatement ps, InputStream csvStream) throws Exception {
        Throwable last = null;

        // 不同版本 Driver 的類名
        String[] unwrapTypes = new String[] {
                "com.mysql.cj.jdbc.ClientPreparedStatement", // 8.x 常見
                "com.mysql.cj.jdbc.JdbcStatement",           // 8.x 其他版本
                "com.mysql.jdbc.PreparedStatement",          // 5.1.x
                "com.mysql.jdbc.Statement"                   // 5.1.x
        };

        // 嘗試解包後找方法
        for (String typeName : unwrapTypes) {
            try {
                Class<?> clazz = Class.forName(typeName);
                Object target = ps.isWrapperFor(clazz) ? ps.unwrap(clazz) : ps;
                Method m = findMethod(target.getClass(), "setLocalInfileInputStream", InputStream.class);
                if (m != null) {
                    m.setAccessible(true);
                    m.invoke(target, csvStream);
                    ps.executeUpdate();
                    return;
                }
            } catch (Throwable t) {
                last = t;
            }
        }

        // 直接在 ps 上找方法
        try {
            Method m = findMethod(ps.getClass(), "setLocalInfileInputStream", InputStream.class);
            if (m != null) {
                m.setAccessible(true);
                m.invoke(ps, csvStream);
                ps.executeUpdate();
                return;
            }
        } catch (Throwable t) {
            last = t;
        }

        // 找不到方法，拋出例外
        throw new IllegalStateException(
                "Driver 未提供 setLocalInfileInputStream。請確認：\n" +
                        "1) 使用 MySQL Connector/J 8.x 或 5.1.x；\n" +
                        "2) JDBC URL 已加 allowLoadLocalInfile=true；\n" +
                        "3) 伺服器已 SET GLOBAL local_infile=1。",
                last
        );
    }

    /**
     * 在類別/父類別/介面中尋找指定方法
     */
    private static Method findMethod(Class<?> cls, String name, Class<?>... params) {
        // 往上找父類別
        Class<?> c = cls;
        while (c != null) {
            try {
                return c.getDeclaredMethod(name, params);
            } catch (NoSuchMethodException ignore) {
                c = c.getSuperclass();
            }
        }
        // 從介面找
        for (Class<?> ifc : cls.getInterfaces()) {
            try {
                return ifc.getMethod(name, params);
            } catch (NoSuchMethodException ignore) { }
        }
        return null;
    }
}
