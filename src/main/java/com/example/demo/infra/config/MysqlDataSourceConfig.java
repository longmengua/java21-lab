package com.example.demo.infra.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class MysqlDataSourceConfig {

    @Bean(name = "mysqlDataSource")
    @Primary
    @ConditionalOnProperty(
            prefix = "spring.datasource.mysql",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    @ConfigurationProperties("spring.datasource.mysql")
    public DataSource mysqlDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean(name = "mysqlJdbcTemplate")
    @ConditionalOnProperty(
            prefix = "spring.datasource.mysql",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = false
    )
    public JdbcTemplate mysqlJdbcTemplate(@Qualifier("mysqlDataSource") DataSource ds) {
        return new JdbcTemplate(ds);
    }
}
