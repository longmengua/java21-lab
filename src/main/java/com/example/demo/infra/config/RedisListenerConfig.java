package com.example.demo.infra.config;

import com.example.demo.application.service.RiskConfigService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.util.Collections;

/**
 * Redis 訂閱監聽器配置類
 *
 * 這個配置類的作用：
 *  - 啟動一個 Redis 訊息監聽容器（RedisMessageListenerContainer）
 *  - 當指定的 Redis 頻道（channel/pattern）收到訊息時，觸發對應的回調方法
 *  - 用於風控系統的配置熱更新，當有新配置發佈時自動重新載入
 */
@Configuration
public class RedisListenerConfig {

    /**
     * 配置 Redis 訊息監聽容器 Bean
     *
     * @param cf  Redis 連線工廠，用於建立 Redis 連線
     * @param cfg 風控配置服務，收到訊息後會透過該服務重新載入配置
     * @return RedisMessageListenerContainer 監聽容器
     */
    @Bean
    public RedisMessageListenerContainer container(RedisConnectionFactory cf, RiskConfigService cfg) {
        // 建立 Redis 訊息監聽容器
        var c = new RedisMessageListenerContainer();
        c.setConnectionFactory(cf); // 設定 Redis 連線工廠

        // 註冊訊息監聽器：
        // 當訂閱的頻道收到訊息時，執行 cfg.loadOnce() 重新載入風控配置
        c.addMessageListener(
                (message, pattern) -> cfg.loadOnce(),
                Collections.singleton(new PatternTopic(RiskConfigService.CFG_CH)) // 訂閱的 Redis 頻道 (PatternTopic)
        );

        return c; // 返回配置好的監聽容器
    }
}
