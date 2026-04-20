package com.pilarestilo.user.infrastructure.persistence.repositories;

import com.pilarestilo.user.domain.model.User;
import com.pilarestilo.user.domain.ports.UserRepository;
import com.pilarestilo.user.infrastructure.persistence.entities.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class UserRepositoryAdapter implements UserRepository {

    private final UserJpaRepository jpaRepository;

    public UserRepositoryAdapter(UserJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public User save(User user) {
        UserEntity entity = toEntity(user);
        return toDomain(jpaRepository.save(entity));
    }

    @Override
    public Optional<User> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return jpaRepository.findByEmail(email).map(this::toDomain);
    }

    @Override
    public Page<User> findAll(Pageable pageable) {
        return jpaRepository.findAll(pageable).map(this::toDomain);
    }

    @Override
    public boolean existsByEmail(String email) {
        return jpaRepository.existsByEmail(email);
    }

    private UserEntity toEntity(User user) {
        UserEntity entity = new UserEntity();
        entity.setId(user.getId());
        entity.setEmail(user.getEmail());
        entity.setFullName(user.getFullName());
        entity.setRole(user.getRole());
        entity.setPasswordHash(user.getPasswordHash());
        entity.setCreatedAt(user.getCreatedAt());
        return entity;
    }

    private User toDomain(UserEntity entity) {
        User user = User.create(entity.getEmail(), entity.getFullName(),
                entity.getRole(), entity.getPasswordHash());
        user.setId(entity.getId());
        user.setCreatedAt(entity.getCreatedAt());
        return user;
    }
}
