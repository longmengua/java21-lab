package com.example.demo.infra.olap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 優先從 MySQL 查詢；若 MySQL 未啟用或無資料/查詢失敗，則回退到 ClickHouse。
 */
@Service
public class ClickhouseService {

    private static final Logger log = LoggerFactory.getLogger(ClickhouseService.class);

    /** MySQL JdbcTemplate（依配置可不存在） */
    @Autowired
    @Qualifier("mysqlJdbcTemplate")
    private Optional<JdbcTemplate> mysqlJdbcTemplate;

    /** ClickHouse JdbcTemplate（依配置可不存在） */
    @Autowired
    @Qualifier("clickhouseJdbcTemplate")
    private Optional<JdbcTemplate> clickhouseJdbcTemplate;

    /**
     * 先查 MySQL（有啟動且有資料就回傳），否則查 ClickHouse。
     */
    public List<Map<String, Object>> fetchData() {
        // 1) MySQL 優先
        if (mysqlJdbcTemplate.isPresent()) {
            try {
                List<Map<String, Object>> mysqlData =
                        mysqlJdbcTemplate.get().queryForList(
                                "SELECT * FROM user ORDER BY id DESC LIMIT 100"
                        );
                if (!mysqlData.isEmpty()) {
                    log.info("返回 MySQL 資料，筆數={}", mysqlData.size());
                    return mysqlData;
                } else {
                    log.info("MySQL 啟用但無資料，改查 ClickHouse。");
                }
            } catch (Exception e) {
                log.warn("查 MySQL 發生例外，改查 ClickHouse：{}", e.getMessage());
            }
        } else {
            log.info("MySQL 未啟用，改查 ClickHouse。");
        }

        // 2) ClickHouse 回退
        if (clickhouseJdbcTemplate.isPresent()) {
            try {
                List<Map<String, Object>> clickData =
                        clickhouseJdbcTemplate.get().queryForList(
                                "SELECT * FROM flink_sink ORDER BY event_time DESC LIMIT 100"
                        );
                log.info("返回 ClickHouse 資料，筆數={}", clickData.size());
                return clickData;
            } catch (Exception e) {
                log.warn("查 ClickHouse 失敗：{}", e.getMessage());
                return Collections.emptyList();
            }
        } else {
            log.info("ClickHouse 未啟用。");
            return Collections.emptyList();
        }
    }
}
