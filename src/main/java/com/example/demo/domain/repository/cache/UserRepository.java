package com.example.demo.domain.repository.cache;

import com.example.demo.domain.model.user.Users;

import java.util.Optional;

public interface UserRepository {
    void save(Users users);
    Optional<Users> findByUsername(String username);
    void deleteByUsername(String username);
}

