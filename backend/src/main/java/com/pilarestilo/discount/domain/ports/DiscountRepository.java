package com.pilarestilo.discount.domain.ports;

import com.pilarestilo.discount.domain.model.Discount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface DiscountRepository {

    Discount save(Discount discount);

    Optional<Discount> findById(UUID id);

    Optional<Discount> findByCode(String code);

    Page<Discount> findAll(Pageable pageable);

    void deleteById(UUID id);
}
