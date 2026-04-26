package com.pilarestilo.discount.application.usecases;

import com.pilarestilo.discount.application.dto.DiscountDto;
import com.pilarestilo.discount.application.mappers.DiscountMapper;
import com.pilarestilo.discount.domain.model.Discount;
import com.pilarestilo.discount.domain.ports.DiscountRepository;
import com.pilarestilo.user.domain.ports.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ListDiscountsUseCase {

    private final DiscountRepository discountRepository;
    private final UserRepository userRepository;

    public ListDiscountsUseCase(DiscountRepository discountRepository,
                                 UserRepository userRepository) {
        this.discountRepository = discountRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Page<DiscountDto> execute(Pageable pageable) {
        return discountRepository.findAll(pageable).map(this::enrich);
    }

    @Transactional(readOnly = true)
    public List<DiscountDto> executeByStatus(String status) {
        return discountRepository.findAllByStatus(status).stream().map(this::enrich).toList();
    }

    private DiscountDto enrich(Discount d) {
        if (d.getAssignedUserId() == null) return DiscountMapper.toDto(d);
        return userRepository.findById(d.getAssignedUserId())
            .map(u -> DiscountMapper.toDto(d, u.getFullName(), u.getEmail()))
            .orElse(DiscountMapper.toDto(d));
    }
}
