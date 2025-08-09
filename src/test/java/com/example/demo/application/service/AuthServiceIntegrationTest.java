package com.example.demo.application.service;

import com.example.demo.application.usecase.AuthUsecase;
import com.example.demo.domain.model.user.User;
import com.example.demo.infra.repository.InMemoryUserRepository;
import com.example.demo.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ContextConfiguration(classes = {AuthServiceIntegrationTest.Config.class})
class AuthServiceIntegrationTest {

    @Configuration
    static class Config {
        @Bean
        public UserRepository userRepository() {
            return new InMemoryUserRepository();
        }

        @Bean
        public AuthUsecase authService(UserRepository userRepository) {
            return new AuthUsecase(userRepository);
        }
    }

    @Autowired
    private AuthUsecase authUsecase;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void resetRepository() {
        if (userRepository instanceof InMemoryUserRepository memoryRepo) {
            memoryRepo.clearAll(); // 🧹 清除資料，確保測試隔離
        }
    }

    @Test
    void testRegister_thenLogin_thenLogout() {
        authUsecase.register("waltor", "123456", "0912345678");

        var user = userRepository.findByUsername("waltor");
        assertTrue(user.isPresent());
        assertEquals("123456", user.get().getPassword());

        assertDoesNotThrow(() -> authUsecase.login("waltor", "123456"));
        assertDoesNotThrow(() -> authUsecase.logout("waltor"));
    }

    @Test
    void testRegister_shouldFail_whenUserExists() {
        userRepository.save(new User("waltor", "pw", "0912"));

        RuntimeException e = assertThrows(RuntimeException.class, () ->
                authUsecase.register("waltor", "pw", "0912")
        );

        assertEquals("使用者已存在", e.getMessage());
    }

    @Test
    void testLogin_shouldFail_withWrongPassword() {
        userRepository.save(new User("waltor", "correct", "0912"));

        RuntimeException e = assertThrows(RuntimeException.class, () ->
                authUsecase.login("waltor", "wrong")
        );

        assertEquals("密碼錯誤", e.getMessage());
    }
}
