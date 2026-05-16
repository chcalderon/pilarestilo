package com.pilarestilo.payment.application.usecases;

import com.pilarestilo.order.domain.enums.PaymentMethod;
import com.pilarestilo.order.domain.ports.OrderRepository;
import com.pilarestilo.payment.application.dto.PaymentGatewayCheckoutDto;
import com.pilarestilo.payment.domain.enums.PaymentStatus;
import com.pilarestilo.payment.domain.ports.PaymentGatewayPort;
import com.pilarestilo.payment.domain.ports.PaymentRepository;
import com.pilarestilo.shared.domain.DomainException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class CreatePaymentGatewayCheckoutUseCase {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final PaymentGatewayPort paymentGatewayPort;

    public CreatePaymentGatewayCheckoutUseCase(PaymentRepository paymentRepository,
                                               OrderRepository orderRepository,
                                               PaymentGatewayPort paymentGatewayPort) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.paymentGatewayPort = paymentGatewayPort;
    }

    @Transactional(readOnly = true)
    public PaymentGatewayCheckoutDto execute(UUID paymentId) {
        var payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new NoSuchElementException("Payment not found: " + paymentId));

        if (payment.getMethod() != PaymentMethod.WEBPAY && payment.getMethod() != PaymentMethod.MERCADOPAGO) {
            throw new DomainException("Payment method is not a gateway method");
        }

        if (payment.getStatus() == PaymentStatus.APPROVED) {
            throw new DomainException("Payment is already approved");
        }

        if (payment.getStatus() == PaymentStatus.REJECTED) {
            throw new DomainException("Payment was rejected and cannot start checkout");
        }

        var order = orderRepository.findById(payment.getOrderId())
                .orElseThrow(() -> new NoSuchElementException("Order not found: " + payment.getOrderId()));

        if (order.getPaymentMethod() != PaymentMethod.WEBPAY && order.getPaymentMethod() != PaymentMethod.MERCADOPAGO) {
            throw new DomainException("Order payment method is not a gateway method");
        }

        var session = paymentGatewayPort.initiatePayment(order.getId(), order.getTotalAmount());
        return new PaymentGatewayCheckoutDto(
                payment.getId(),
                payment.getOrderId(),
                session.gatewayReference(),
                session.checkoutUrl(),
                session.expiresAt()
        );
    }
}

