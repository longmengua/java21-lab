package com.example.demo.infra.props;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter @Setter
@ConfigurationProperties(prefix = "topics")
public class TopicsProps {
    /** topics.events */
    private String events;

    /** topics.alerts */
    private String alerts;
}

