package com.pilarestilo.discount.application.usecases;

import com.pilarestilo.discount.application.dto.DiscountDto;
import com.pilarestilo.discount.application.mappers.DiscountMapper;
import com.pilarestilo.discount.domain.enums.DiscountType;
import com.pilarestilo.discount.domain.events.DiscountCodeAssigned;
import com.pilarestilo.discount.domain.model.Discount;
import com.pilarestilo.discount.domain.ports.DiscountRepository;
import com.pilarestilo.shared.application.Money;
import com.pilarestilo.shared.domain.DomainEventPublisher;
import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.user.domain.ports.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Service
public class CreateDiscountUseCase {

    private final DiscountRepository discountRepository;
    private final UserRepository userRepository;
    private final DomainEventPublisher eventPublisher;

    public CreateDiscountUseCase(DiscountRepository discountRepository,
                                  UserRepository userRepository,
                                  DomainEventPublisher eventPublisher) {
        this.discountRepository = discountRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public DiscountDto execute(String code, String type, BigDecimal value,
                                BigDecimal minOrderAmount, LocalDate validFrom,
                                LocalDate validUntil, int maxUses, UUID assignedUserId) {
        if (discountRepository.findByCode(code.toUpperCase()).isPresent()) {
            throw new DomainException("Discount code already exists: " + code);
        }
        if (assignedUserId != null && !userRepository.existsById(assignedUserId)) {
            throw new DomainException("Assigned user not found: " + assignedUserId);
        }

        DiscountType discountType = DiscountType.valueOf(type);
        Money minAmount = Money.of(minOrderAmount != null ? minOrderAmount : BigDecimal.ZERO);

        Discount discount = Discount.create(code, discountType, value, minAmount, validFrom, validUntil, maxUses);
        discount.setAssignedUserId(assignedUserId);
        DiscountDto dto = DiscountMapper.toDto(discountRepository.save(discount));

        if (assignedUserId != null) {
            eventPublisher.publish(new DiscountCodeAssigned(discount.getId(), discount.getCode(), assignedUserId));
        }

        return dto;
    }
}
