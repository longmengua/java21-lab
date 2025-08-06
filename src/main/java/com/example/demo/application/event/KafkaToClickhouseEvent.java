package com.example.demo.application.event;

import lombok.*;

import java.time.Instant;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KafkaToClickhouseEvent {

    // Getter and Setter
    private long userId;
    private String action;
    private Instant eventTime;
}
