package com.example.demo.application.usecase;

import com.example.demo.domain.model.user.User;
import com.example.demo.domain.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class AuthUsecase {

    private final UserRepository userRepository;

    public AuthUsecase(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public void register(String username, String password, String phone) {
        if (userRepository.findByUsername(username).isPresent()) {
            throw new RuntimeException("使用者已存在");
        }
        userRepository.save(new User(username, password, phone));
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
