package com.example.demo.interfaces.web.controller;

import com.example.demo.interfaces.web.dto.ApiResponse;
import com.example.demo.interfaces.web.dto.LoginRequest;
import com.example.demo.interfaces.web.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class AuthControllerIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String baseUrl(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    void testRegisterLoginLogoutFlow() {
        // --- 註冊 ---
        RegisterRequest register = new RegisterRequest();
        register.setUsername("waltor");
        register.setPassword("123456");
        register.setPhone("0912345678");

        ResponseEntity<ApiResponse> registerResponse = restTemplate.postForEntity(
                baseUrl("/auth/register"),
                new HttpEntity<>(register, jsonHeader()),
                ApiResponse.class
        );

        assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(registerResponse.getBody()).isNotNull();
        assertThat(registerResponse.getBody().isSuccess()).isTrue();

        // --- 登入 ---
        LoginRequest login = new LoginRequest();
        login.setUsername("waltor");
        login.setPassword("123456");

        ResponseEntity<ApiResponse> loginResponse = restTemplate.postForEntity(
                baseUrl("/auth/login"),
                new HttpEntity<>(login, jsonHeader()),
                ApiResponse.class
        );

        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loginResponse.getBody()).isNotNull();
        assertThat(loginResponse.getBody().isSuccess()).isTrue();

        // --- 登出 ---
        ResponseEntity<ApiResponse> logoutResponse = restTemplate.postForEntity(
                baseUrl("/auth/logout?username=waltor"),
                null,
                ApiResponse.class
        );

        assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(logoutResponse.getBody()).isNotNull();
        assertThat(logoutResponse.getBody().isSuccess()).isTrue();
    }

    private HttpHeaders jsonHeader() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}

