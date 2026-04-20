package com.pilarestilo.user.infrastructure.persistence.repositories;

import com.pilarestilo.user.infrastructure.persistence.entities.UserEntity;
import com.pilarestilo.user.domain.enums.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface UserJpaRepository extends JpaRepository<UserEntity, UUID> {
    Optional<UserEntity> findByEmail(String email);
    boolean existsByEmail(String email);
    Page<UserEntity> findByRole(UserRole role, Pageable pageable);
    Page<UserEntity> findByActive(boolean active, Pageable pageable);
    Page<UserEntity> findByRoleAndActive(UserRole role, boolean active, Pageable pageable);
}
