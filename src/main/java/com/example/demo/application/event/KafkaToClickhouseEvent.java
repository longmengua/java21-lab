package com.example.demo.application.event;

import jdk.jfr.Event;
import lombok.*;


import java.time.Instant;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Setter
@Getter
public class KafkaToClickhouseEvent extends Event {
    private long userId;
    private String action;
    private Instant eventTime;
}