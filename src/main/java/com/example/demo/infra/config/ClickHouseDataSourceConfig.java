// ClickHouseDataSourceConfig.java
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
public class ClickHouseDataSourceConfig {

    @Bean(name = "clickhouseDataSource")
    @ConditionalOnProperty(
            prefix = "spring.clickhouse",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true   // 讓沒設定 enabled 也能啟用（預設 true）
    )
    public DataSource clickHouseDataSource(ClickHouseProps props) throws Exception {
        return new ClickHouseDataSource(props.getUrl());
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
