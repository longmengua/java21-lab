package com.example.demo.infra.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import javax.sql.DataSource;

@Configuration
@ConditionalOnProperty(prefix = "datasource", name = "enabled", havingValue = "true")
public class MySqlConfig {

    @Bean(name = "mysqlDataSource")
    public DataSource mysqlDataSource(Environment env) throws Exception {
        // 必填
        String url = trimToNull(env.getProperty("datasource.mysql.url"));
        String username = trimToNull(env.getProperty("datasource.mysql.username"));
        // 可選（允許空字串）
        String password = env.getProperty("datasource.mysql.password", "");
        String driver = env.getProperty("datasource.mysql.driver-class-name", "com.mysql.cj.jdbc.Driver");

        if (url == null) {
            throw new IllegalArgumentException("配置缺失：datasource.mysql.url 未設定");
        }
        if (username == null) {
            throw new IllegalArgumentException("配置缺失：datasource.mysql.username 未設定");
        }

        Class<?> driverCls = Class.forName(driver);
        java.sql.Driver drv = (java.sql.Driver) driverCls.getDeclaredConstructor().newInstance();
        return new SimpleDriverDataSource(drv, url, username, password);
    }

    @Bean(name = "mysqlJdbcTemplate")
    public JdbcTemplate mysqlJdbcTemplate(@Qualifier("mysqlDataSource") DataSource ds) {
        return new JdbcTemplate(ds);
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
