package com.pilarestilo.notificationservice.application;

import com.pilarestilo.notificationservice.domain.model.NotificationRecipient;
import com.pilarestilo.notificationservice.domain.ports.CustomerReadPort;
import com.pilarestilo.notificationservice.domain.ports.NotificationSender;
import com.pilarestilo.notificationservice.domain.ports.OrderReadPort;
import com.pilarestilo.notificationservice.domain.ports.SalesDocumentReadPort;
import com.pilarestilo.notificationservice.domain.view.OrderView;
import com.pilarestilo.notificationservice.domain.view.SalesDocumentView;
import com.pilarestilo.notificationservice.events.Events;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

/** What happens when a tax document is registered. Ported from the monolith. */
@Service
public class BillingNotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(BillingNotificationDispatcher.class);

    private final SalesDocumentReadPort salesDocumentReadPort;
    private final OrderReadPort orderReadPort;
    private final CustomerReadPort customerReadPort;
    private final NotificationSender notificationSender;
    private final NotificationComposer composer;

    public BillingNotificationDispatcher(SalesDocumentReadPort salesDocumentReadPort,
                                         OrderReadPort orderReadPort,
                                         CustomerReadPort customerReadPort,
                                         NotificationSender notificationSender,
                                         NotificationComposer composer) {
        this.salesDocumentReadPort = salesDocumentReadPort;
        this.orderReadPort = orderReadPort;
        this.customerReadPort = customerReadPort;
        this.notificationSender = notificationSender;
        this.composer = composer;
    }

    public void onSalesDocumentIssued(Events.SalesDocumentIssued event) {
        Optional<SalesDocumentView> document = salesDocumentReadPort.findById(event.documentId());
        Optional<OrderView> order = orderReadPort.findById(event.orderId());
        if (document.isEmpty() || order.isEmpty()) {
            log.warn("Sales document {} was issued but could not be read back; no message sent",
                    event.documentId());
            return;
        }

        var message = composer.salesDocumentIssued(order.get(), document.get());
        customerReadPort.findById(order.get().customerId()).ifPresentOrElse(
                user -> notificationSender.send(message, NotificationRecipient.of(
                        user.phone(), user.email(), user.notificationChannelPreference())),
                () -> notificationSender.send(message, NotificationRecipient.unknown()));
    }
}
