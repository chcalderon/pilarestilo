package com.pilarestilo.paymentservice.application;

import com.pilarestilo.paymentservice.persistence.OrderEntity;
import com.pilarestilo.paymentservice.persistence.OrderRepository;
import com.pilarestilo.paymentservice.persistence.PaymentEntity;
import com.pilarestilo.paymentservice.persistence.PaymentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PaymentQueryService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;

    public PaymentQueryService(PaymentRepository paymentRepository,
                               OrderRepository orderRepository) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
    }

    public Page<PaymentEntity> list(String status, Pageable pageable) {
        if (status != null && !status.isBlank()) {
            return paymentRepository.findByStatus(status.trim().toUpperCase(), pageable);
        }
        return paymentRepository.findAll(pageable);
    }

    public PaymentEntity getById(UUID id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new java.util.NoSuchElementException("Payment not found: " + id));
    }

    public PaymentEntity getByOrderId(UUID orderId) {
        return paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new java.util.NoSuchElementException("Payment not found for order: " + orderId));
    }

    public OrderEntity getOrderById(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new java.util.NoSuchElementException("Order not found: " + orderId));
    }
}
