package com.example.demo.interfaces.web.controller;

import com.example.demo.application.service.AsyncTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/multi-thread")
public class MultiThreadController {

    @Autowired
    private AsyncTaskService asyncTaskService;

    @GetMapping("/async-method")
    public CompletableFuture<String> asyncMethod() {
        return asyncTaskService.asyncMethod();
    }

    @GetMapping("/another-async-method")
    public CompletableFuture<String> anotherAsyncMethod() {
        return asyncTaskService.anotherAsyncMethod();
    }
}
