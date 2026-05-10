package com.pilarestilo.shared.auth.infrastructure.web;

import com.pilarestilo.customeraddress.application.CustomerAddressBookService;
import com.pilarestilo.customeraddress.application.dto.CustomerAddressDto;
import com.pilarestilo.customeraddress.application.mappers.CustomerAddressMapper;
import com.pilarestilo.customeraddress.domain.model.CustomerAddress;
import com.pilarestilo.shared.auth.domain.AuthenticatedUser;
import com.pilarestilo.shared.auth.infrastructure.web.requests.CreateCustomerAddressRequest;
import com.pilarestilo.shared.auth.infrastructure.web.requests.UpdateCustomerAddressRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth/me/addresses")
public class CustomerAddressController {

    private final CustomerAddressBookService service;

    public CustomerAddressController(CustomerAddressBookService service) {
        this.service = service;
    }

    @GetMapping
    public List<CustomerAddressDto> list(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        return service.list(currentUser.id())
                .stream()
                .map(CustomerAddressMapper::toDto)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerAddressDto create(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                     @Valid @RequestBody CreateCustomerAddressRequest request) {
        CustomerAddress saved = service.create(
                currentUser.id(),
                request.label(),
                request.recipientName(),
                request.phone(),
                request.line1(),
                request.line2(),
                request.comuna(),
                request.city(),
                request.region(),
                request.reference(),
                request.isDefault() != null && request.isDefault()
        );
        return CustomerAddressMapper.toDto(saved);
    }

    @PatchMapping("/{addressId}")
    public CustomerAddressDto update(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                     @PathVariable UUID addressId,
                                     @Valid @RequestBody UpdateCustomerAddressRequest request) {
        CustomerAddress updated = service.update(
                currentUser.id(),
                addressId,
                request.label(),
                request.recipientName(),
                request.phone(),
                request.line1(),
                request.line2(),
                request.comuna(),
                request.city(),
                request.region(),
                request.reference()
        );
        return CustomerAddressMapper.toDto(updated);
    }

    @DeleteMapping("/{addressId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser currentUser,
                       @PathVariable UUID addressId) {
        service.delete(currentUser.id(), addressId);
    }

    @PatchMapping("/{addressId}/default")
    public CustomerAddressDto setDefault(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                         @PathVariable UUID addressId) {
        CustomerAddress updated = service.setDefault(currentUser.id(), addressId);
        return CustomerAddressMapper.toDto(updated);
    }
}

