package com.pilarestilo.order.infrastructure.web.controllers;

import com.pilarestilo.order.application.commands.CreateOrderCommand;
import com.pilarestilo.order.application.dto.OrderDto;
import com.pilarestilo.order.application.usecases.CreateOrderUseCase;
import com.pilarestilo.order.application.usecases.GetOrderUseCase;
import com.pilarestilo.order.application.usecases.ListOrdersUseCase;
import com.pilarestilo.order.application.usecases.UpdateOrderStatusUseCase;
import com.pilarestilo.dispatch.application.usecases.ConfirmOrderDeliveryUseCase;
import com.pilarestilo.order.domain.enums.PaymentMethod;
import com.pilarestilo.order.infrastructure.web.requests.CreateOrderRequest;
import com.pilarestilo.order.infrastructure.web.requests.UpdateOrderStatusRequest;
import com.pilarestilo.shared.application.Money;
import com.pilarestilo.shared.auth.domain.AuthenticatedUser;
import com.pilarestilo.user.domain.enums.UserRole;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final CreateOrderUseCase createOrderUseCase;
    private final GetOrderUseCase getOrderUseCase;
    private final ListOrdersUseCase listOrdersUseCase;
    private final UpdateOrderStatusUseCase updateOrderStatusUseCase;
    private final ConfirmOrderDeliveryUseCase confirmOrderDeliveryUseCase;

    public OrderController(CreateOrderUseCase createOrderUseCase,
                            GetOrderUseCase getOrderUseCase,
                            ListOrdersUseCase listOrdersUseCase,
                            UpdateOrderStatusUseCase updateOrderStatusUseCase,
                            ConfirmOrderDeliveryUseCase confirmOrderDeliveryUseCase) {
        this.createOrderUseCase = createOrderUseCase;
        this.getOrderUseCase = getOrderUseCase;
        this.listOrdersUseCase = listOrdersUseCase;
        this.updateOrderStatusUseCase = updateOrderStatusUseCase;
        this.confirmOrderDeliveryUseCase = confirmOrderDeliveryUseCase;
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<OrderDto> create(@Valid @RequestBody CreateOrderRequest request,
                                           @AuthenticationPrincipal AuthenticatedUser currentUser) {
        if (currentUser == null) {
            throw new AccessDeniedException("Authentication required");
        }

        List<CreateOrderCommand.OrderItemCommand> items = request.items().stream()
                .map(i -> new CreateOrderCommand.OrderItemCommand(
                        i.productId(),
                        i.quantity(),
                        i.variantColor(),
                        i.variantSize()
                ))
                .toList();

        CreateOrderCommand command = new CreateOrderCommand(
                currentUser.id(),
                items,
                PaymentMethod.valueOf(request.paymentMethod()),
                request.notes(),
                Money.zero(),
                currentUser.role() == UserRole.SELLER,
                request.discountCode()
        );

        OrderDto dto = createOrderUseCase.execute(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SELLER')")
    public Page<OrderDto> list(@RequestParam(required = false) UUID customerId, Pageable pageable) {
        if (customerId != null) {
            return listOrdersUseCase.executeByCustomer(customerId, pageable);
        }
        return listOrdersUseCase.execute(pageable);
    }

    @GetMapping("/mine")
    @PreAuthorize("isAuthenticated()")
    public Page<OrderDto> listMine(@AuthenticationPrincipal AuthenticatedUser currentUser, Pageable pageable) {
        return listOrdersUseCase.executeByCustomer(currentUser.id(), pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public OrderDto getById(@PathVariable UUID id,
                            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        OrderDto dto = getOrderUseCase.execute(id);
        if (currentUser.role() == UserRole.CUSTOMER && !dto.customerId().equals(currentUser.id())) {
            throw new AccessDeniedException("You can only access your own orders");
        }
        return dto;
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','SELLER')")
    public OrderDto updateStatus(@PathVariable UUID id,
                                  @Valid @RequestBody UpdateOrderStatusRequest request) {
        return updateOrderStatusUseCase.execute(id, request.status());
    }

    @PatchMapping("/{id}/confirm-delivery")
    @PreAuthorize("isAuthenticated()")
    public OrderDto confirmDelivery(@PathVariable UUID id,
                                    @AuthenticationPrincipal AuthenticatedUser currentUser) {
        if (currentUser == null) {
            throw new AccessDeniedException("Authentication required");
        }
        return confirmOrderDeliveryUseCase.execute(id, currentUser.id());
    }
}
