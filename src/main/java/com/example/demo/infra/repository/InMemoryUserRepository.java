package com.example.demo.infra.repository;

import com.example.demo.domain.model.user.Users;
import com.example.demo.domain.repository.cache.UserRepository;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryUserRepository implements UserRepository {

    private final Map<String, Users> storage = new ConcurrentHashMap<>();

    @Override
    public void save(Users users) {
        storage.put(users.getUsername(), users);
    }

    @Override
    public Optional<Users> findByUsername(String username) {
        return Optional.ofNullable(storage.get(username));
    }

    @Override
    public void deleteByUsername(String username) {
        storage.remove(username);
    }

    public void clearAll() {
        storage.clear();
    }
}
