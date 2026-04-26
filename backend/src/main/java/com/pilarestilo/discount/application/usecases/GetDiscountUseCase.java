package com.pilarestilo.discount.application.usecases;

import com.pilarestilo.discount.application.dto.DiscountDto;
import com.pilarestilo.discount.application.mappers.DiscountMapper;
import com.pilarestilo.discount.domain.ports.DiscountRepository;
import com.pilarestilo.user.domain.ports.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class GetDiscountUseCase {

    private final DiscountRepository discountRepository;
    private final UserRepository userRepository;

    public GetDiscountUseCase(DiscountRepository discountRepository,
                               UserRepository userRepository) {
        this.discountRepository = discountRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public DiscountDto execute(UUID id) {
        var d = discountRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Discount not found: " + id));
        if (d.getAssignedUserId() == null) return DiscountMapper.toDto(d);
        return userRepository.findById(d.getAssignedUserId())
            .map(u -> DiscountMapper.toDto(d, u.getFullName(), u.getEmail()))
            .orElse(DiscountMapper.toDto(d));
    }
}
