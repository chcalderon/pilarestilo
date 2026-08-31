package com.pilarestilo.order.application.usecases;

import com.pilarestilo.order.application.dto.MoneyDto;
import com.pilarestilo.order.application.dto.OrderDto;
import com.pilarestilo.order.application.dto.OrderItemDto;
import com.pilarestilo.order.domain.enums.OrderStatus;
import com.pilarestilo.order.domain.events.OrderCreated;
import com.pilarestilo.order.domain.events.OrderStatusChanged;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.pilarestilo.shared.domain.ports.AnalyticsTracker;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The server side of the storefront conversion funnel: {@code order_created} when the order row
 * exists and {@code order_paid} when it reaches {@link OrderStatus#PAID}. These carry the money
 * numbers — total, item count, payment method — and, unlike the browser events, cannot be lost to
 * a closed tab. Keyed to the customer id so PostHog folds them into the same person the snippet
 * identified at login.
 *
 * <p>Behaviour lives here; the in-process and Kafka listeners are transports with none of their
 * own, the same shape as the dispatch and notification reactions.
 */
@Service
public class TrackOrderAnalyticsUseCase {

    private static final Logger log = LoggerFactory.getLogger(TrackOrderAnalyticsUseCase.class);

    private final GetOrderUseCase getOrderUseCase;
    private final AnalyticsTracker analyticsTracker;

    public TrackOrderAnalyticsUseCase(GetOrderUseCase getOrderUseCase, AnalyticsTracker analyticsTracker) {
        this.getOrderUseCase = getOrderUseCase;
        this.analyticsTracker = analyticsTracker;
    }

    public void onOrderCreated(OrderCreated event) {
        emit("order_created", event.orderId(), event.customerId(), Map.of());
    }

    public void onOrderStatusChanged(OrderStatusChanged event) {
        if (event.newStatus() != OrderStatus.PAID) {
            return;
        }
        emit("order_paid", event.orderId(), event.customerId(),
                Map.of("previous_status", String.valueOf(event.previousStatus())));
    }

    private void emit(String eventName, UUID orderId, UUID customerId, Map<String, Object> extra) {
        if (customerId == null) {
            return;
        }
        Map<String, Object> properties = new HashMap<>(extra);
        properties.put("order_id", orderId.toString());
        try {
            OrderDto order = getOrderUseCase.execute(orderId);
            properties.putAll(orderProperties(order));
        } catch (RuntimeException ex) {
            // The event still goes out with just the id — losing it would lose the money signal —
            // but flag why it is thin.
            log.warn("analytics event {} for order {} sent without order detail: {}",
                    eventName, orderId, ex.getMessage());
        }
        analyticsTracker.track(eventName, customerId.toString(), properties);
    }

    private static Map<String, Object> orderProperties(OrderDto order) {
        Map<String, Object> props = new HashMap<>();
        putIfNotNull(props, "public_reference", order.publicReference());
        putIfNotNull(props, "status", order.status());
        putIfNotNull(props, "payment_method", order.paymentMethod());
        putIfNotNull(props, "sales_channel", order.salesChannel());
        props.put("item_count", totalQuantity(order.items()));
        props.put("line_count", order.items() == null ? 0 : order.items().size());
        MoneyDto discount = order.discountAmount();
        if (discount != null && discount.amount() != null
                && discount.amount().compareTo(BigDecimal.ZERO) > 0) {
            props.put("discount_amount", discount.amount());
        }
        MoneyDto total = order.totalAmount();
        if (total != null) {
            props.put("total", total.amount());
            props.put("currency", total.currency());
        }
        return props;
    }

    private static int totalQuantity(List<OrderItemDto> items) {
        if (items == null) {
            return 0;
        }
        return items.stream().mapToInt(OrderItemDto::quantity).sum();
    }

    private static void putIfNotNull(Map<String, Object> props, String key, Object value) {
        if (value != null) {
            props.put(key, value instanceof String ? value : value.toString());
        }
    }
}
