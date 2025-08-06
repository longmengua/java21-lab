package com.example.demo.interfaces.web.controller;

import com.example.demo.application.service.AuthService;
import com.example.demo.interfaces.web.dto.ApiResponse;
import com.example.demo.interfaces.web.dto.LoginRequest;
import com.example.demo.interfaces.web.dto.RegisterRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ApiResponse<?> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request.getUsername(), request.getPassword(), request.getPhone());
        return ApiResponse.ok("註冊成功");
    }

    @PostMapping("/login")
    public ApiResponse<?> login(@Valid @RequestBody LoginRequest request) {
        authService.login(request.getUsername(), request.getPassword());
        return ApiResponse.ok("登入成功");
    }

    @PostMapping("/logout")
    public ApiResponse<?> logout(@RequestParam String username) {
        authService.logout(username);
        return ApiResponse.ok("已登出");
    }
}
