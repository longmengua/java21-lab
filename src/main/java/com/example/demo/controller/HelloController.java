package com.example.demo.controller;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import com.example.demo.application.service.HelloService;

@RestController
@RequestMapping("/api")
public class HelloController {

    @Autowired
    private HelloService helloService;

    @GetMapping("/hello")
    @ResponseStatus(HttpStatus.OK)
    public Mono<String> getHello() {
        return helloService.getMessage();
    }

    @GetMapping("/stream")
    public Flux<Integer> streamNumbers() {
        return Flux.range(1, 10) // 回傳 1 到 10 的數字流
                .delayElements(Duration.ofMillis(100)); // 每個數字之間延遲 100 毫秒
    }
}
