package com.pilarestilo.user.application.mappers;

import com.pilarestilo.user.application.dto.UserDto;
import com.pilarestilo.user.domain.model.User;

public class UserMapper {

    private UserMapper() {}

    public static UserDto toDto(User user) {
        return new UserDto(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole().name(),
                user.getCreatedAt()
        );
    }
}
