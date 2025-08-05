package com.example.demo.application.service;

import com.example.demo.domain.model.User;
import com.example.demo.domain.repository.InMemoryUserRepository;
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
        public AuthService authService(UserRepository userRepository) {
            return new AuthService(userRepository);
        }
    }

    @Autowired
    private AuthService authService;

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
        authService.register("waltor", "123456", "0912345678");

        var user = userRepository.findByUsername("waltor");
        assertTrue(user.isPresent());
        assertEquals("123456", user.get().getPassword());

        assertDoesNotThrow(() -> authService.login("waltor", "123456"));
        assertDoesNotThrow(() -> authService.logout("waltor"));
    }

    @Test
    void testRegister_shouldFail_whenUserExists() {
        userRepository.save(new User("waltor", "pw", "0912"));

        RuntimeException e = assertThrows(RuntimeException.class, () ->
                authService.register("waltor", "pw", "0912")
        );

        assertEquals("使用者已存在", e.getMessage());
    }

    @Test
    void testLogin_shouldFail_withWrongPassword() {
        userRepository.save(new User("waltor", "correct", "0912"));

        RuntimeException e = assertThrows(RuntimeException.class, () ->
                authService.login("waltor", "wrong")
        );

        assertEquals("密碼錯誤", e.getMessage());
    }
}
