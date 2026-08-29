package com.pilarestilo.notificationservice.domain.ports;

import com.pilarestilo.notificationservice.domain.view.CustomerView;

import java.util.List;

/**
 * Backs the {@code findByRoleIn} query the payment and return dispatchers use to email every active
 * staff member who can act on what just happened.
 */
public interface PaymentReviewerReadPort {
    List<CustomerView> findActiveByRoles(List<String> roles);
}
