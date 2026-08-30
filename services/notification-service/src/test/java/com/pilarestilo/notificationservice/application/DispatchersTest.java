package com.pilarestilo.notificationservice.application;

import com.pilarestilo.notificationservice.domain.model.NotificationMessage;
import com.pilarestilo.notificationservice.domain.ports.CustomerReadPort;
import com.pilarestilo.notificationservice.domain.ports.InAppNotificationPort;
import com.pilarestilo.notificationservice.domain.ports.MessagingSettingsPort;
import com.pilarestilo.notificationservice.domain.ports.NotificationSender;
import com.pilarestilo.notificationservice.domain.ports.OrderReadPort;
import com.pilarestilo.notificationservice.domain.ports.PaymentReadPort;
import com.pilarestilo.notificationservice.domain.ports.PaymentReviewerReadPort;
import com.pilarestilo.notificationservice.domain.ports.ReturnReadPort;
import com.pilarestilo.notificationservice.domain.ports.SalesDocumentReadPort;
import com.pilarestilo.notificationservice.domain.view.MessagingSettings;
import com.pilarestilo.notificationservice.events.Events;
import com.pilarestilo.notificationservice.events.PaymentConstants;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.pilarestilo.notificationservice.application.ViewFixtures.boleta;
import static com.pilarestilo.notificationservice.application.ViewFixtures.customer;
import static com.pilarestilo.notificationservice.application.ViewFixtures.order;
import static com.pilarestilo.notificationservice.application.ViewFixtures.returnRequest;
import static com.pilarestilo.notificationservice.application.ViewFixtures.reviewer;
import static com.pilarestilo.notificationservice.application.ViewFixtures.transferPayment;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DispatchersTest {

    final NotificationSender sender = mock(NotificationSender.class);
    final NotificationComposer composer = new NotificationComposer();
    final InAppNotificationPort inApp = mock(InAppNotificationPort.class);
    final OrderReadPort orders = mock(OrderReadPort.class);
    final CustomerReadPort customers = mock(CustomerReadPort.class);
    final PaymentReadPort payments = mock(PaymentReadPort.class);
    final SalesDocumentReadPort documents = mock(SalesDocumentReadPort.class);
    final ReturnReadPort returns = mock(ReturnReadPort.class);
    final PaymentReviewerReadPort reviewers = mock(PaymentReviewerReadPort.class);
    final MessagingSettingsPort settings = mock(MessagingSettingsPort.class);

    final UUID orderId = UUID.randomUUID();
    final UUID customerId = UUID.randomUUID();
    final UUID paymentId = UUID.randomUUID();

    @Nested
    class Order {
        final OrderNotificationDispatcher dispatcher =
                new OrderNotificationDispatcher(sender, composer, inApp, customers, orders);

        @Test
        void created_sends_the_confirmation_and_writes_the_in_app_row() {
            when(orders.findById(orderId)).thenReturn(Optional.of(order(orderId, customerId, "PE-1")));
            when(customers.findById(customerId)).thenReturn(Optional.of(customer(customerId)));

            dispatcher.onOrderCreated(new Events.OrderCreated(orderId, customerId, Instant.now()));

            verify(sender).send(argThat(m -> NotificationMessage.ORDER_CONFIRMATION.equals(m.templateKey())), any());
            verify(inApp).notifyOrderConfirmed(customerId, orderId);
        }

        @Test
        void status_change_to_shipped_notifies_the_customer() {
            when(orders.findById(orderId)).thenReturn(Optional.of(order(orderId, customerId, "PE-1")));
            when(customers.findById(customerId)).thenReturn(Optional.of(customer(customerId)));

            dispatcher.onOrderStatusChanged(new Events.OrderStatusChanged(
                    orderId, customerId, "PREPARING_ORDER", "SHIPPED", Instant.now()));

            verify(sender).send(argThat(m -> NotificationMessage.ORDER_SHIPPED.equals(m.templateKey())), any());
            verify(inApp).notifyOrderShipped(customerId, orderId);
        }

        @Test
        void an_uninteresting_status_change_is_ignored() {
            dispatcher.onOrderStatusChanged(new Events.OrderStatusChanged(
                    orderId, customerId, "CREATED", "PAID", Instant.now()));

            verifyNoInteractions(sender, orders);
        }
    }

    @Nested
    class Payment {
        final PaymentNotificationDispatcher dispatcher = new PaymentNotificationDispatcher(
                sender, composer, inApp, orders, customers, payments, reviewers);

        @Test
        void confirmed_sends_the_receipt_and_writes_the_in_app_row() {
            when(orders.findById(orderId)).thenReturn(Optional.of(order(orderId, customerId, "PE-1")));
            when(customers.findById(customerId)).thenReturn(Optional.of(customer(customerId)));

            dispatcher.onPaymentConfirmed(new Events.PaymentConfirmed(paymentId, orderId, Instant.now()));

            verify(sender).send(argThat(m -> NotificationMessage.PAYMENT_RECEIVED.equals(m.templateKey())), any());
            verify(inApp).notifyPaymentReceived(customerId, paymentId);
        }

        @Test
        void submitted_emails_every_active_reviewer() {
            when(payments.findById(paymentId)).thenReturn(Optional.of(transferPayment(paymentId, orderId)));
            when(orders.findById(orderId)).thenReturn(Optional.of(order(orderId, customerId, "PE-1")));
            when(customers.findById(customerId)).thenReturn(Optional.of(customer(customerId)));
            when(reviewers.findActiveByRoles(any())).thenReturn(List.of(
                    reviewer("admin@pilarestilo.com"), reviewer("finanzas@pilarestilo.com")));

            dispatcher.onPaymentSubmitted(new Events.PaymentSubmitted(paymentId, "comprobante.pdf", Instant.now()));

            verify(sender, times(2)).send(
                    argThat(m -> NotificationMessage.PAYMENT_PROOF_SUBMITTED.equals(m.templateKey())), any());
        }

        @Test
        void rejected_by_a_human_stays_quiet() {
            dispatcher.onPaymentRejected(new Events.PaymentRejected(
                    paymentId, orderId, UUID.randomUUID(), Instant.now()));

            verifyNoInteractions(sender);
        }

        @Test
        void rejected_by_the_auto_cancel_job_notifies() {
            when(payments.findById(paymentId)).thenReturn(Optional.of(transferPayment(paymentId, orderId)));
            when(orders.findById(orderId)).thenReturn(Optional.of(order(orderId, customerId, "PE-1")));
            when(customers.findById(customerId)).thenReturn(Optional.of(customer(customerId)));

            dispatcher.onPaymentRejected(new Events.PaymentRejected(
                    paymentId, orderId, PaymentConstants.SYSTEM_REVIEWER_ID, Instant.now()));

            verify(sender).send(argThat(m -> NotificationMessage.ORDER_CANCELLED.equals(m.templateKey())), any());
        }
    }

    @Nested
    class PaymentRegistered {
        final PaymentRegisteredNotificationDispatcher dispatcher = new PaymentRegisteredNotificationDispatcher(
                sender, composer, payments, orders, customers, settings);

        @Test
        void a_transfer_payment_gets_the_instructions() {
            when(payments.findById(paymentId)).thenReturn(Optional.of(transferPayment(paymentId, orderId)));
            when(orders.findById(orderId)).thenReturn(Optional.of(order(orderId, customerId, "PE-1")));
            when(customers.findById(customerId)).thenReturn(Optional.of(customer(customerId)));
            when(settings.current()).thenReturn(MessagingSettings.empty());

            dispatcher.onPaymentRegistered(new Events.PaymentRegistered(paymentId, orderId, Instant.now()));

            verify(sender).send(argThat(m -> NotificationMessage.TRANSFER_INSTRUCTIONS.equals(m.templateKey())), any());
        }

        @Test
        void a_non_transfer_payment_is_ignored() {
            when(payments.findById(paymentId)).thenReturn(Optional.of(new com.pilarestilo.notificationservice.domain.view.PaymentView(
                    paymentId, orderId, "WEBPAY", "CONFIRMED", null, null, Instant.now(),
                    null, null, null, null, null)));

            dispatcher.onPaymentRegistered(new Events.PaymentRegistered(paymentId, orderId, Instant.now()));

            verifyNoInteractions(sender);
        }
    }

    @Nested
    class Billing {
        final BillingNotificationDispatcher dispatcher =
                new BillingNotificationDispatcher(documents, orders, customers, sender, composer);

        @Test
        void issued_document_is_announced_to_the_customer() {
            UUID docId = UUID.randomUUID();
            when(documents.findById(docId)).thenReturn(Optional.of(boleta(docId)));
            when(orders.findById(orderId)).thenReturn(Optional.of(order(orderId, customerId, "PE-1")));
            when(customers.findById(customerId)).thenReturn(Optional.of(customer(customerId)));

            dispatcher.onSalesDocumentIssued(new Events.SalesDocumentIssued(docId, orderId, "12345", Instant.now()));

            verify(sender).send(argThat(m -> NotificationMessage.SALES_DOCUMENT_ISSUED.equals(m.templateKey())), any());
        }
    }

    @Nested
    class Discount {
        final DiscountNotificationDispatcher dispatcher =
                new DiscountNotificationDispatcher(inApp, sender, composer, customers);

        @Test
        void assigned_code_is_written_in_app_and_emailed() {
            when(customers.findById(customerId)).thenReturn(Optional.of(customer(customerId)));

            dispatcher.onDiscountCodeAssigned(new Events.DiscountCodeAssigned(
                    UUID.randomUUID(), "EXCLUSIVO10", customerId, Instant.now()));

            verify(inApp).notifyDiscountCodeAssigned(customerId, "EXCLUSIVO10");
            verify(sender).send(argThat(m -> NotificationMessage.DISCOUNT_CODE_ASSIGNED.equals(m.templateKey())), any());
        }
    }

    @Nested
    class Return {
        final ReturnNotificationDispatcher dispatcher = new ReturnNotificationDispatcher(
                returns, orders, customers, reviewers, sender, composer);

        @Test
        void requested_notifies_the_customer_and_the_handlers() {
            UUID returnId = UUID.randomUUID();
            when(returns.findById(returnId)).thenReturn(Optional.of(returnRequest(returnId, orderId)));
            when(orders.findById(orderId)).thenReturn(Optional.of(order(orderId, customerId, "PE-1")));
            when(customers.findById(customerId)).thenReturn(Optional.of(customer(customerId)));
            when(reviewers.findActiveByRoles(any())).thenReturn(List.of(reviewer("admin@pilarestilo.com")));

            dispatcher.onReturnRequested(new Events.ReturnRequested(returnId, orderId, "RETRACTO", Instant.now()));

            verify(sender).send(argThat(m -> NotificationMessage.RETURN_REQUESTED.equals(m.templateKey())), any());
            verify(sender).send(argThat(m -> NotificationMessage.RETURN_REQUESTED_STAFF.equals(m.templateKey())), any());
        }
    }

    @Nested
    class User {
        final UserNotificationDispatcher dispatcher =
                new UserNotificationDispatcher(sender, composer, inApp, customers);

        @Test
        void registered_sends_the_welcome_and_writes_the_in_app_row() {
            when(customers.findById(customerId)).thenReturn(Optional.of(customer(customerId)));

            dispatcher.onUserRegistered(new Events.UserRegistered(customerId, Instant.now(), null));

            verify(sender).send(argThat(m -> NotificationMessage.WELCOME.equals(m.templateKey())), any());
            verify(inApp).notifyWelcome(customerId, null);
        }
    }
}
