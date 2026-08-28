package com.pilarestilo.notificationservice.infrastructure.persistence.readonly;

import com.pilarestilo.notificationservice.domain.ports.CustomerReadPort;
import com.pilarestilo.notificationservice.domain.ports.PaymentReviewerReadPort;
import com.pilarestilo.notificationservice.domain.view.CustomerView;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class CustomerReadAdapter implements CustomerReadPort, PaymentReviewerReadPort {

    private final UserRoRepository repository;

    public CustomerReadAdapter(UserRoRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<CustomerView> findById(UUID userId) {
        return repository.findById(userId).map(CustomerReadAdapter::toView);
    }

    @Override
    public List<CustomerView> findActiveByRoles(List<String> roles) {
        return repository.findActiveByRoleIn(roles).stream()
                .map(CustomerReadAdapter::toView)
                .toList();
    }

    private static CustomerView toView(UserRoEntity e) {
        return new CustomerView(
                e.getId(), e.getEmail(), e.getPhone(), e.getFullName(),
                e.getRole(), e.isActive(), e.getNotificationChannelPreference());
    }
}
