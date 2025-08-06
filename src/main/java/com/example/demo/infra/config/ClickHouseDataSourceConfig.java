package com.example.demo.infra.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.yandex.clickhouse.ClickHouseDataSource;

import javax.sql.DataSource;

@Configuration
public class ClickHouseDataSourceConfig {

    @Value("${spring.clickhouse.url}")
    private String url;

    @Bean(name = "clickhouseDataSource")
    @ConditionalOnProperty(
            prefix = "spring.clickhouse",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    public DataSource clickHouseDataSource() throws Exception {
        return new ClickHouseDataSource(url);
    }

    @Bean(name = "clickhouseJdbcTemplate")
    @ConditionalOnProperty(
            prefix = "spring.clickhouse",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    public JdbcTemplate clickhouseJdbcTemplate(@Qualifier("clickhouseDataSource") DataSource ds) {
        return new JdbcTemplate(ds);
    }
}
