package com.example.demo.infra.config;

import com.example.demo.application.service.RiskConfigService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * <p>RiskSubscriberConfig</p>
 *
 * 功能：
 *   - 監聽 Redis Pub/Sub 頻道（risk:cfg:channel）
 *   - 當收到配置更新通知時，自動觸發 RiskSubscriberConfig.reload() 更新本地快取
 * 背景：
 *   - RiskSubscriberConfig 負責從 Redis 載入配置並快取
 *   - 若配置在 Redis 中被外部修改（例如後台管理系統更新了風控規則），
 *     可透過 Redis PUBLISH 命令發送通知
 *   - 本監聽器會收到通知並立即刷新快取，達成「熱更新」
 *
 * 使用方式：
 *   1. 在 Redis 中更新配置，例如：
 *      SET risk:cfg:all "{...新JSON...}"
 *   2. 發送更新通知：
 *      PUBLISH risk:cfg:channel reload
 *   3. 所有訂閱該頻道的服務實例會自動重新載入最新配置
 */
@Configuration
public class RiskSubscriberConfig {

    /**
     * 建立 RedisMessageListenerContainer 以監聽指定頻道
     *
     * @param cf  Redis 連線工廠（由 Spring Boot 自動配置）
     * @param svc RiskConfigService，用於重新載入配置
     * @return RedisMessageListenerContainer Bean，隨 Spring 啟動
     */
    @Bean
    public RedisMessageListenerContainer riskCfgListenerContainer(
            RedisConnectionFactory cf,
            RiskConfigService svc) {

        // 建立 Redis 訊息監聽容器
        var container = new RedisMessageListenerContainer();
        container.setConnectionFactory(cf);

        // 新增訊息監聽器
        container.addMessageListener((message, channel) -> {
            // 取得頻道名稱
            String ch = new String(channel);
            // 取得訊息內容
            String payload = new String(message.getBody());

            // 如果需要可根據訊息內容判斷是否刷新
            // 例如僅當 payload 為 "reload" 時才觸發
            // if ("reload".equalsIgnoreCase(payload)) { ... }

            // 重新載入配置
            svc.loadOnce();

            // 可選：記錄日誌
            // log.info("Risk config reloaded via Redis channel: {}, payload: {}", ch, payload);

        }, new ChannelTopic(RiskConfigService.CFG_CH));

        return container;
    }
}
