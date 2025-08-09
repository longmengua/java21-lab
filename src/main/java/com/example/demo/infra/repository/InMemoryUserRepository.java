package com.example.demo.infra.repository;

import com.example.demo.domain.model.user.User;
import com.example.demo.domain.repository.UserRepository;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryUserRepository implements UserRepository {

    private final Map<String, User> storage = new ConcurrentHashMap<>();

    @Override
    public void save(User user) {
        storage.put(user.getUsername(), user);
    }

    @Override
    public Optional<User> findByUsername(String username) {
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
