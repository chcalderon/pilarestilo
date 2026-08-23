package com.pilarestilo.dispatch.infrastructure.web;

import com.pilarestilo.dispatch.application.dto.DispatchDto;
import com.pilarestilo.dispatch.application.dto.DispatchHistoryRowDto;
import com.pilarestilo.dispatch.application.usecases.ListDispatchesUseCase;
import com.pilarestilo.dispatch.application.usecases.ListDispatchHistoryUseCase;
import com.pilarestilo.dispatch.domain.model.Dispatch;
import com.pilarestilo.dispatch.domain.ports.DispatchRepository;
import com.pilarestilo.order.application.dto.OrderDto;
import com.pilarestilo.order.application.usecases.GetOrderUseCase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/despachos")
public class AdminDespachoController {

    private final ListDispatchesUseCase listUseCase;
    private final ListDispatchHistoryUseCase listDispatchHistoryUseCase;
    private final DispatchRepository dispatchRepository;
    private final GetOrderUseCase getOrderUseCase;

    public AdminDespachoController(ListDispatchesUseCase listUseCase,
                                    ListDispatchHistoryUseCase listDispatchHistoryUseCase,
                                    DispatchRepository dispatchRepository,
                                    GetOrderUseCase getOrderUseCase) {
        this.listUseCase = listUseCase;
        this.listDispatchHistoryUseCase = listDispatchHistoryUseCase;
        this.dispatchRepository = dispatchRepository;
        this.getOrderUseCase = getOrderUseCase;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR')")
    public Page<DispatchDto> list(Pageable pageable) {
        return listUseCase.executeForAdmin(pageable);
    }

    @GetMapping("/history")
    @PreAuthorize("hasRole('ADMIN') or (isAuthenticated() and principal.permissions.contains('despachos'))")
    public Page<DispatchHistoryRowDto> history(@RequestParam(required = false) LocalDate from,
                                               @RequestParam(required = false) LocalDate to,
                                               Pageable pageable) {
        Pageable safePageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return listDispatchHistoryUseCase.execute(safePageable, from, to);
    }

    @PostMapping("/seed")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public DispatchDto seed(@RequestBody Map<String, String> body) {
        UUID orderId = UUID.fromString(body.get("orderId"));
        if (dispatchRepository.existsByOrderId(orderId)) {
            return dispatchRepository.findByOrderId(orderId).map(DispatchDto::from).orElseThrow();
        }
        return DispatchDto.from(dispatchRepository.save(resolveDispatchToCreate(orderId)));
    }

    private Dispatch resolveDispatchToCreate(UUID orderId) {
        try {
            OrderDto order = getOrderUseCase.execute(orderId);
            return Dispatch.create(
                    orderId,
                    order.shippingZoneCode(),
                    order.shippingCourierId(),
                    order.shippingCourierName(),
                    order.shippingAddressReference()
            );
        } catch (Exception _) {
            return Dispatch.create(orderId);
        }
    }
}
