package com.pilarestilo.user.infrastructure.web.requests;

public record UpdateUserRequest(
        String fullName,
        String role,
        Boolean active
) {}
