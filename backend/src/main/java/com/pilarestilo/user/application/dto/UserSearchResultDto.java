package com.pilarestilo.user.application.dto;

import java.util.UUID;

public record UserSearchResultDto(UUID id, String fullName, String email) {}
