package com.example.demo.infra.props;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter @Setter
@ConfigurationProperties(prefix = "spring.clickhouse")
public class ClickHouseProps {
    /** spring.clickhouse.enabled； */
    private boolean enabled;
    /** spring.clickhouse.url */
    private String url;
}

