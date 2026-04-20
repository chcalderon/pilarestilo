package com.pilarestilo.user.infrastructure.web.controllers;

import com.pilarestilo.user.application.dto.UserDto;
import com.pilarestilo.user.application.usecases.CreateUserUseCase;
import com.pilarestilo.user.application.usecases.GetUserUseCase;
import com.pilarestilo.user.application.usecases.ListUsersUseCase;
import com.pilarestilo.user.infrastructure.web.requests.CreateUserRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final CreateUserUseCase createUserUseCase;
    private final GetUserUseCase getUserUseCase;
    private final ListUsersUseCase listUsersUseCase;

    public UserController(CreateUserUseCase createUserUseCase,
                           GetUserUseCase getUserUseCase,
                           ListUsersUseCase listUsersUseCase) {
        this.createUserUseCase = createUserUseCase;
        this.getUserUseCase = getUserUseCase;
        this.listUsersUseCase = listUsersUseCase;
    }

    @PostMapping
    public ResponseEntity<UserDto> create(@Valid @RequestBody CreateUserRequest request) {
        UserDto dto = createUserUseCase.execute(
                request.email(), request.fullName(), request.role(), request.passwordHash()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @GetMapping
    public Page<UserDto> list(Pageable pageable) {
        return listUsersUseCase.execute(pageable);
    }

    @GetMapping("/{id}")
    public UserDto getById(@PathVariable UUID id) {
        return getUserUseCase.execute(id);
    }
}
