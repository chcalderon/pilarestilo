package com.pilarestilo.discount.domain.ports;

import com.pilarestilo.discount.domain.model.Discount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * No delete method by design. {@code discount_code_usages.discount_id} cascades, so removing a
 * discount row destroys the redemption ledger behind it. Retiring a code is
 * {@link com.pilarestilo.discount.application.usecases.DeleteDiscountUseCase}, which deactivates.
 */
public interface DiscountRepository {

    Discount save(Discount discount);

    Optional<Discount> findById(UUID id);

    Optional<Discount> findByCode(String code);

    Page<Discount> findAll(Pageable pageable);

    List<Discount> findAllByStatus(String status);




    long countByCodePattern(String pattern);
}
