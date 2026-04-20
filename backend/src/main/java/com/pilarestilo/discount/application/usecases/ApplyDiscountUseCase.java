package com.pilarestilo.discount.application.usecases;

import com.pilarestilo.discount.domain.events.DiscountApplied;
import com.pilarestilo.discount.domain.model.Discount;
import com.pilarestilo.discount.domain.ports.DiscountRepository;
import com.pilarestilo.shared.application.Money;
import com.pilarestilo.shared.domain.DomainEventPublisher;
import com.pilarestilo.shared.domain.DomainException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class ApplyDiscountUseCase {

    private final DiscountRepository discountRepository;
    private final DomainEventPublisher eventPublisher;

    public ApplyDiscountUseCase(DiscountRepository discountRepository,
                                 DomainEventPublisher eventPublisher) {
        this.discountRepository = discountRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Money execute(String code, BigDecimal subtotalAmount, UUID orderId) {
        Discount discount = discountRepository.findByCode(code.toUpperCase())
                .orElseThrow(() -> new DomainException("Discount code not found: " + code));
        Money subtotal = Money.of(subtotalAmount);
        Money discountAmount = discount.apply(subtotal);
        discountRepository.save(discount);

        eventPublisher.publish(new DiscountApplied(
                discount.getId(), discount.getCode(), orderId, discountAmount.amount()
        ));

        return discountAmount;
    }
}
