package com.pilarestilo.user.infrastructure.persistence.repositories;

import com.pilarestilo.user.domain.model.User;
import com.pilarestilo.user.domain.ports.UserRepository;
import com.pilarestilo.user.domain.enums.UserRole;
import com.pilarestilo.user.infrastructure.persistence.entities.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
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
    public Page<User> findByRoleAndActive(UserRole role, Boolean active, Pageable pageable) {
        if (active == null) {
            return jpaRepository.findByRole(role, pageable).map(this::toDomain);
        }
        return jpaRepository.findByRoleAndActive(role, active, pageable).map(this::toDomain);
    }

    @Override
    public Page<User> findByRole(UserRole role, Pageable pageable) {
        return jpaRepository.findByRole(role, pageable).map(this::toDomain);
    }

    @Override
    public Page<User> findByActive(boolean active, Pageable pageable) {
        return jpaRepository.findByActive(active, pageable).map(this::toDomain);
    }

    @Override
    public boolean existsById(UUID id) {
        return jpaRepository.existsById(id);
    }

    @Override
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public boolean existsByEmail(String email) {
        return jpaRepository.existsByEmail(email);
    }

    @Override
    public List<User> searchByQuery(String query, int limit) {
        return jpaRepository.searchByNameOrEmail(query, PageRequest.of(0, limit))
            .stream().map(this::toDomain).toList();
    }

    private UserEntity toEntity(User user) {
        UserEntity entity = new UserEntity();
        entity.setId(user.getId());
        entity.setEmail(user.getEmail());
        entity.setFullName(user.getFullName());
        entity.setPhone(user.getPhone());
        entity.setNotificationChannelPreference(user.getNotificationChannelPreference().name());
        entity.setRole(user.getRole());
        entity.setActive(user.isActive());
        entity.setPasswordHash(user.getPasswordHash());
        entity.setCreatedAt(user.getCreatedAt());
        return entity;
    }

    private User toDomain(UserEntity entity) {
        return User.reconstruct(
                entity.getId(),
                entity.getEmail(),
                entity.getFullName(),
                entity.getPhone(),
                entity.getNotificationChannelPreference(),
                entity.getRole(),
                entity.isActive(),
                entity.getPasswordHash(),
                entity.getCreatedAt()
        );
    }
}
