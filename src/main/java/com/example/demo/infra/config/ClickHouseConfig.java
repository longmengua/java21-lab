package com.example.demo.infra.config;

import com.example.demo.infra.props.ClickHouseProps;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.yandex.clickhouse.ClickHouseDataSource;

import javax.sql.DataSource;

@Configuration
@EnableConfigurationProperties(ClickHouseProps.class)
public class ClickHouseConfig {

    @Bean(name = "clickhouseDataSource")
    @ConditionalOnProperty(
            prefix = "clickhouse",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = false // 必須顯式開啟才建立
    )
    public DataSource clickHouseDataSource(ClickHouseProps props) throws Exception {
        if (props.getUrl() == null || props.getUrl().isBlank()) {
            throw new IllegalArgumentException("ClickHouse URL 不可為空，請檢查配置 clickhouse.url");
        }
        return new ClickHouseDataSource(props.getUrl());
    }

    @Bean(name = "clickhouseJdbcTemplate")
    @ConditionalOnProperty(
            prefix = "clickhouse",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = false
    )
    public JdbcTemplate clickhouseJdbcTemplate(@Qualifier("clickhouseDataSource") DataSource ds) {
        return new JdbcTemplate(ds);
    }
}
