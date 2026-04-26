package com.pilarestilo.user.application.usecases;

import com.pilarestilo.user.application.dto.UserSearchResultDto;
import com.pilarestilo.user.domain.ports.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SearchUsersUseCase {

    private final UserRepository userRepository;

    public SearchUsersUseCase(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<UserSearchResultDto> execute(String query) {
        if (query == null || query.isBlank()) return List.of();
        return userRepository.searchByQuery(query.trim(), 10)
            .stream()
            .map(u -> new UserSearchResultDto(u.getId(), u.getFullName(), u.getEmail()))
            .toList();
    }
}
