package com.example.demo.infra.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class ClickHouseDataSourceConfig {

    @Value("${spring.clickhouse.url}")
    private String url;

    @Bean(name = "clickhouseDataSource")
    public DataSource clickHouseDataSource() throws Exception {
        return new ru.yandex.clickhouse.ClickHouseDataSource(url);
    }

    @Bean(name = "clickhouseJdbcTemplate")
    public JdbcTemplate clickhouseJdbcTemplate(@Qualifier("clickhouseDataSource") DataSource ds) {
        return new JdbcTemplate(ds);
    }
}

