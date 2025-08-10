package com.example.demo.infra.config;


import com.example.demo.infra.props.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        TopicsProps.class,
        ClickHouseProps.class,
        DatasourceProps.class,
        RocketMQProps.class,
        KafkaProps.class,
        RedisProps.class
})
public class AppPropsConfig {
    // 這裡不用寫 @Bean，Spring 會自動綁定並註冊上述類別
}
