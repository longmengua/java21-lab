package com.example.demo.service;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class HelloService {
    public Mono<String> getMessage() {
        return Mono.just("Hello from Service Layer!");
    }
}
