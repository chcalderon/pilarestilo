package com.pilarestilo.user.domain.ports;

import com.pilarestilo.user.domain.model.User;
import com.pilarestilo.user.domain.enums.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {

    User save(User user);

    Optional<User> findById(UUID id);

    Optional<User> findByEmail(String email);

    Page<User> findAll(Pageable pageable);

    Page<User> findByRoleAndActive(UserRole role, Boolean active, Pageable pageable);

    Page<User> findByRole(UserRole role, Pageable pageable);

    Page<User> findByActive(boolean active, Pageable pageable);

    boolean existsById(UUID id);

    void deleteById(UUID id);

    boolean existsByEmail(String email);

    List<User> searchByQuery(String query, int limit);
}
