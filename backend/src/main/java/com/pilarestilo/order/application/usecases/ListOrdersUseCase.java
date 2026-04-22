package com.pilarestilo.order.application.usecases;

import com.pilarestilo.order.application.dto.OrderDto;
import com.pilarestilo.order.application.mappers.OrderMapper;
import com.pilarestilo.order.application.remote.OrderRemoteQueryClient;
import com.pilarestilo.order.domain.ports.OrderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ListOrdersUseCase {

    private final OrderRepository orderRepository;
    private final OrderRemoteQueryClient orderRemoteQueryClient;

    public ListOrdersUseCase(OrderRepository orderRepository,
                             OrderRemoteQueryClient orderRemoteQueryClient) {
        this.orderRepository = orderRepository;
        this.orderRemoteQueryClient = orderRemoteQueryClient;
    }

    @Transactional(readOnly = true)
    public Page<OrderDto> execute(Pageable pageable) {
        if (orderRemoteQueryClient.isEnabled()) {
            return orderRemoteQueryClient.list(null, pageable);
        }
        return orderRepository.findAll(pageable).map(OrderMapper::toDto);
    }

    @Transactional(readOnly = true)
    public Page<OrderDto> executeByCustomer(UUID customerId, Pageable pageable) {
        if (orderRemoteQueryClient.isEnabled()) {
            return orderRemoteQueryClient.list(customerId, pageable);
        }
        return orderRepository.findByCustomerId(customerId, pageable).map(OrderMapper::toDto);
    }
}
