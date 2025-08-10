package com.example.demo.infra.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "rocketmq", name = "enabled", havingValue = "true", matchIfMissing = false)
@ConditionalOnBean(RocketMQTemplate.class) // 模板存在才註冊，避免注入失敗
public class RocketProducer {

    private final RocketMQTemplate rocketMQTemplate;

    /** 最基本同步發送；topic 也可寫成 "topic:tag" */
    public SendResult sendMessage(String topic, String message) {
        SendResult rs = rocketMQTemplate.syncSend(topic, message); // 預設 3s timeout，可在 yml 配置
        log.info("RocketMQ sent: topic={}, msgId={}, status={}", topic,
                rs != null ? rs.getMsgId() : "n/a",
                rs != null ? rs.getSendStatus() : "n/a");
        return rs;
    }

    /** 需要 tag 時用 */
    public SendResult sendWithTag(String topic, String tag, String message) {
        String dest = tag == null || tag.isBlank() ? topic : topic + ":" + tag;
        return rocketMQTemplate.syncSend(dest, message);
    }

    /** 非阻塞異步 */
    public void sendAsync(String topic, String message,
                          org.apache.rocketmq.client.producer.SendCallback cb) {
        rocketMQTemplate.asyncSend(topic, message, cb);
    }

    /** 單向，不關心結果 */
    public void sendOneWay(String topic, String message) {
        rocketMQTemplate.sendOneWay(topic, message);
    }
}
