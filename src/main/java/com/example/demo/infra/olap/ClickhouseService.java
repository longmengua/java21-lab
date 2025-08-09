package com.example.demo.infra.olap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ClickhouseService {

//    @Autowired
//    @Qualifier("mysqlJdbcTemplate")
//    private JdbcTemplate mysqlJdbcTemplate;

    @Autowired
    @Qualifier("clickhouseJdbcTemplate")
    private JdbcTemplate clickhouseJdbcTemplate;

    public Object fetchData() {
//        List<Map<String, Object>> mysqlData = mysqlJdbcTemplate.queryForList("SELECT * FROM user");
        List<Map<String, Object>> clickData = clickhouseJdbcTemplate.queryForList("SELECT * FROM flink_sink ORDER BY event_time DESC LIMIT 100;");

        return clickData;
    }
}

