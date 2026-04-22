package com.pilarestilo.orderservice.web;

import com.pilarestilo.orderservice.application.OrderCommandService;
import com.pilarestilo.orderservice.application.OrderQueryService;
import com.pilarestilo.orderservice.auth.AuthenticatedUser;
import com.pilarestilo.orderservice.auth.UserRole;
import com.pilarestilo.orderservice.web.dto.CreateOrderRequest;
import com.pilarestilo.orderservice.web.dto.OrderDto;
import com.pilarestilo.orderservice.web.dto.UpdateOrderStatusRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderQueryService queryService;
    private final OrderCommandService commandService;

    public OrderController(OrderQueryService queryService,
                           OrderCommandService commandService) {
        this.queryService = queryService;
        this.commandService = commandService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SELLER')")
    public Page<OrderDto> list(
            @RequestParam(required = false) UUID customerId,
            Pageable pageable
    ) {
        return queryService.list(customerId, pageable)
                .map(OrderMapper::toDto);
    }

    @GetMapping("/mine")
    @PreAuthorize("isAuthenticated()")
    public Page<OrderDto> listMine(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                   Pageable pageable) {
        return queryService.list(currentUser.id(), pageable)
                .map(OrderMapper::toDto);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public OrderDto getById(@PathVariable UUID id,
                            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        try {
            OrderDto order = OrderMapper.toDto(queryService.getById(id));
            if (currentUser.role() == UserRole.CUSTOMER && !order.customerId().equals(currentUser.id())) {
                throw new AccessDeniedException("You can only access your own orders");
            }
            return order;
        } catch (NoSuchElementException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<OrderDto> create(@RequestBody CreateOrderRequest request,
                                           @AuthenticationPrincipal AuthenticatedUser currentUser) {
        try {
            if (!currentUser.internalCall() && (request.customerId() == null || !request.customerId().equals(currentUser.id()))) {
                throw new AccessDeniedException("You can only create orders for your own account");
            }
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(OrderMapper.toDto(commandService.create(request)));
        } catch (NoSuchElementException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','SELLER')")
    public OrderDto updateStatus(@PathVariable UUID id,
                                 @RequestBody UpdateOrderStatusRequest request) {
        try {
            return OrderMapper.toDto(commandService.updateStatus(id, request.status()));
        } catch (NoSuchElementException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @GetMapping("/_health")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void healthPing() {
    }
}
