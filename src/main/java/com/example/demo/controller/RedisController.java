package com.example.demo.controller;

import com.example.demo.service.RedisService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/redis")
public class RedisController {

    private final RedisService redisService;

    public RedisController(RedisService redisService) {
        this.redisService = redisService;
    }

    // curl -X POST "http://localhost:8080/redis/set?key=test&value=hello"
    @PostMapping("/set")
    public String set(@RequestParam String key, @RequestParam String value) {
        redisService.set(key, value);
        return "OK";
    }

    // curl "http://localhost:8080/redis/get?key=test"
    @GetMapping("/get")
    public String get(@RequestParam String key) {
        return redisService.get(key);
    }

    // curl -X POST http://localhost:8080/redis/reset
    @GetMapping("/reset")
    public String reset() {
        return redisService.reset();
    }
}
