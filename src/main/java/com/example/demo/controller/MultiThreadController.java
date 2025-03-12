package com.example.demo.controller;

import com.example.demo.service.MultiThreadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api")
public class MultiThreadController {

    @Autowired
    private MultiThreadService multiThreadService;

    @GetMapping("/message")
    public Mono<String> getMessage() {
        return multiThreadService.getMessage();
    }

    @GetMapping("/async-method")
    public CompletableFuture<String> asyncMethod() {
        return multiThreadService.asyncMethod();
    }

    @GetMapping("/another-async-method")
    public CompletableFuture<String> anotherAsyncMethod() {
        return multiThreadService.anotherAsyncMethod();
    }
}
