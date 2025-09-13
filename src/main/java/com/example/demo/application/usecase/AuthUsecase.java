package com.example.demo.application.usecase;

import com.example.demo.domain.model.user.Users;
import com.example.demo.domain.repository.cache.UserRepository;
import com.example.demo.domain.repository.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthUsecase {

    @Value("${datasource.enabled:false}") // 如果沒設，預設 false
    private boolean datasourceEnabled;

    private final UserRepository userRepository;

    private final UserMapper userMapper;

    public void register(String username, String password, String phone) {
        if (userRepository.findByUsername(username).isPresent()) {
            throw new RuntimeException("使用者已存在");
        }
        userRepository.save(
                Users.builder().username(username).phone(phone).password(password).build()
        );
    }

    public void login(String username, String password) {
        var user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("使用者不存在"));

        if (!user.getPassword().equals(password)) {
            throw new RuntimeException("密碼錯誤");
        }
    }

    public void logout(String username) {
        // 這裡只是 demo，不處理 token/session
        if (userRepository.findByUsername(username).isEmpty()) {
            throw new RuntimeException("尚未登入");
        }
    }
}
