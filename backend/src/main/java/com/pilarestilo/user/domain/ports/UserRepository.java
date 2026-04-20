package com.pilarestilo.user.domain.ports;

import com.pilarestilo.user.domain.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository {

    User save(User user);

    Optional<User> findById(UUID id);

    Optional<User> findByEmail(String email);

    Page<User> findAll(Pageable pageable);

    boolean existsByEmail(String email);
}
