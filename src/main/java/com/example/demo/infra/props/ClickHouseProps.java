package com.example.demo.infra.props;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter @Setter
@ConfigurationProperties(prefix = "clickhouse")
public class ClickHouseProps {
    @NotBlank(message = "ClickHouse URL 不可為空")
    private String url;
}
