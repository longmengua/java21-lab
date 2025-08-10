package com.example.demo.infra.config;

import com.example.demo.infra.props.DatasourceProps;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
@EnableConfigurationProperties(DatasourceProps.class)
public class MysqlDataSourceConfig {

    @Bean(name = "mysqlDataSource")
    @Primary
    @ConditionalOnProperty(
            prefix = "spring.datasource",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = false
    )
    public DataSource mysqlDataSource(DatasourceProps props) {
        var m = props.getMysql();
        HikariConfig cfg = new HikariConfig();
        // 一個一個欄位 mapping
        cfg.setJdbcUrl(m.getUrl());
        cfg.setUsername(m.getUsername());
        if (m.getPassword() != null) cfg.setPassword(m.getPassword());
        if (m.getDriverClassName() != null) cfg.setDriverClassName(m.getDriverClassName());

        // 可選：一些安全的預設
        cfg.setMaximumPoolSize(10);
        cfg.setMinimumIdle(2);
        cfg.setIdleTimeout(300_000);      // 5min
        cfg.setConnectionTimeout(30_000); // 30s
        cfg.setPoolName("mysql-hikari");

        return new HikariDataSource(cfg);
    }

    @Bean(name = "mysqlJdbcTemplate")
    @ConditionalOnProperty(
            prefix = "spring.datasource",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = false
    )
    public JdbcTemplate mysqlJdbcTemplate(@Qualifier("mysqlDataSource") DataSource ds) {
        return new JdbcTemplate(ds);
    }
}
