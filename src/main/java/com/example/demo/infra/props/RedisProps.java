package com.example.demo.infra.props;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

@Getter @Setter
@Validated
@ConfigurationProperties(prefix = "redis") // 對應你的 yml: redis: ...
public class RedisProps {

    /** redis.enabled */
    private boolean enabled = true;

    /** redis.clusterMode：true=cluster，false=單機（你自己的判斷邏輯用） */
    private boolean clusterMode = false;

    /** redis.nodes：host:port 列表；單機時建議只給一個；cluster 給多個 */
    @NotEmpty(message = "redis.nodes 不能為空")
    private List<String> nodes;

    /** redis.timeout：建議用 Duration，在 yml 可寫 2s / 2000ms */
    private Duration timeout = Duration.ofSeconds(2);

    /** redis.max-attempts（kebab 會自動綁到 camelCase） */
    @Min(1)
    private int maxAttempts = 3;

    /** redis.password（可選） */
    private String password;

    /* === 便捷方法（可選） === */

    /** 以毫秒返回 timeout，方便給 Jedis/Lettuce 使用 */
    public int getTimeoutMillis() {
        return (int) Math.min(Integer.MAX_VALUE, timeout.toMillis());
    }

    /** 是否走 cluster 初始化（你也可以直接用 clusterMode） */
    public boolean isCluster() {
        return clusterMode;
    }
}
