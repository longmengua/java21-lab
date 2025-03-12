package com.example.demo.service;

import org.springframework.stereotype.Service;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class MultiThreadService {

    private final ExecutorService executorService = Executors.newFixedThreadPool(10);

    public CompletableFuture<String> asyncMethod() {
        return CompletableFuture.supplyAsync(() -> {
            // Simulate a long-running task
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "Hello from Async Method!";
        }, executorService);
    }

    public CompletableFuture<String> anotherAsyncMethod() {
        return CompletableFuture.supplyAsync(() -> {
            // Simulate another long-running task
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "Hello from Another Async Method!";
        }, executorService);
    }
}
