package com.pilarestilo.discount.application.usecases;

import com.pilarestilo.discount.application.dto.DiscountDto;
import com.pilarestilo.discount.application.mappers.DiscountMapper;
import com.pilarestilo.discount.domain.ports.DiscountRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListDiscountsUseCase {

    private final DiscountRepository discountRepository;

    public ListDiscountsUseCase(DiscountRepository discountRepository) {
        this.discountRepository = discountRepository;
    }

    @Transactional(readOnly = true)
    public Page<DiscountDto> execute(Pageable pageable) {
        return discountRepository.findAll(pageable).map(DiscountMapper::toDto);
    }
}
