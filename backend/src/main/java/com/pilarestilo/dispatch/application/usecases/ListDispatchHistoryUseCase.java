package com.pilarestilo.dispatch.application.usecases;

import com.pilarestilo.dispatch.application.dto.DispatchHistoryRowDto;
import com.pilarestilo.dispatch.domain.model.Dispatch;
import com.pilarestilo.dispatch.domain.ports.DispatchRepository;
import com.pilarestilo.order.application.dto.OrderDto;
import com.pilarestilo.order.application.usecases.GetOrderUseCase;
import com.pilarestilo.payment.application.dto.PaymentDto;
import com.pilarestilo.payment.application.usecases.GetPaymentByOrderUseCase;
import com.pilarestilo.user.domain.ports.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class ListDispatchHistoryUseCase {

    private final DispatchRepository dispatchRepository;
    private final GetOrderUseCase getOrderUseCase;
    private final GetPaymentByOrderUseCase getPaymentByOrderUseCase;
    private final UserRepository userRepository;

    public ListDispatchHistoryUseCase(DispatchRepository dispatchRepository,
                                      GetOrderUseCase getOrderUseCase,
                                      GetPaymentByOrderUseCase getPaymentByOrderUseCase,
                                      UserRepository userRepository) {
        this.dispatchRepository = dispatchRepository;
        this.getOrderUseCase = getOrderUseCase;
        this.getPaymentByOrderUseCase = getPaymentByOrderUseCase;
        this.userRepository = userRepository;
    }

    public Page<DispatchHistoryRowDto> execute(Pageable pageable, LocalDate from, LocalDate to) {
        LocalDate resolvedFrom = from != null ? from : YearMonth.now().atDay(1);
        LocalDate resolvedToInclusive = to != null ? to : resolvedFrom.plusMonths(1).minusDays(1);

        return dispatchRepository.findHistory(resolvedFrom, resolvedToInclusive, pageable)
                .map(this::toHistoryRow);
    }

    private DispatchHistoryRowDto toHistoryRow(Dispatch dispatch) {
        Instant orderCreatedAt = null;
        String orderReference = null;
        try {
            OrderDto order = getOrderUseCase.execute(dispatch.getOrderId());
            orderCreatedAt = order.createdAt();
            orderReference = order.publicReference();
        } catch (Exception _) {
            // Keep null when order cannot be resolved.
        }

        String soldBy = "Web";
        try {
            PaymentDto payment = getPaymentByOrderUseCase.execute(dispatch.getOrderId());
            if (payment.reviewedBy() != null) {
                soldBy = resolveSellerLabel(payment.reviewedBy());
            }
        } catch (NoSuchElementException _) {
            // Keep default value.
        } catch (Exception _) {
            // Keep default value.
        }

        String dispatchedBy = resolveDispatcherLabel(dispatch.getDispatcherId());

        return new DispatchHistoryRowDto(
                dispatch.getId(),
                dispatch.getOrderId(),
                dispatch.getDispatcherId(),
                dispatchedBy,
                dispatch.getStatus().name(),
                dispatch.getCarrier(),
                dispatch.getTrackingCode(),
                dispatch.getScheduledDate(),
                dispatch.getDispatchedAt(),
                dispatch.getDeliveredAt(),
                dispatch.getCarrierOverrideConfigured(),
                dispatch.getCarrierOverrideSelected(),
                dispatch.getCarrierOverrideBy(),
                dispatch.getCarrierOverrideAt(),
                dispatch.getNotes(),
                dispatch.getCreatedAt(),
                orderCreatedAt,
                orderReference,
                soldBy
        );
    }

    private String resolveDispatcherLabel(UUID dispatcherId) {
        if (dispatcherId == null) {
            return "Sin asignar";
        }
        return resolveUserLabel(dispatcherId);
    }

    private String resolveSellerLabel(UUID reviewedBy) {
        return resolveUserLabel(reviewedBy);
    }

    private String resolveUserLabel(UUID userId) {
        return userRepository.findById(userId)
                .map(user -> {
                    String fullName = user.getFullName();
                    if (fullName != null && !fullName.isBlank()) {
                        return fullName.trim();
                    }
                    return user.getEmail();
                })
                .orElse("Usuario " + userId.toString().substring(0, 8));
    }
}
