package com.pilarestilo.returns.application.usecases;

import com.pilarestilo.inventory.application.InventoryService;
import com.pilarestilo.order.domain.model.Order;
import com.pilarestilo.order.domain.model.OrderItem;
import com.pilarestilo.order.domain.ports.OrderRepository;
import com.pilarestilo.returns.application.dto.ReturnRequestDto;
import com.pilarestilo.returns.application.mappers.ReturnRequestMapper;
import com.pilarestilo.returns.domain.enums.ItemDisposition;
import com.pilarestilo.returns.domain.model.ReturnRequest;
import com.pilarestilo.returns.domain.ports.ReturnRequestRepository;
import com.pilarestilo.shared.domain.DomainException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Decides what became of the returned garment, once reconditioning is done.
 *
 * <p><strong>The only place in this module that moves stock.</strong> Receiving a return puts
 * nothing back: every returned garment is cleaned, pressed, sanitised and repaired first, so
 * restocking on arrival would put a dirty garment in the window. Here the garment is either back on
 * sale or gone, and only the first touches inventory.
 *
 * <p>Deliberately independent of the refund. The money has forty-five days by law; the workshop
 * takes as long as it takes, and making one wait for the other would let a delay breach a deadline.
 */
@Service
public class ResolveItemDispositionUseCase {

    private final ReturnRequestRepository returnRequestRepository;
    private final OrderRepository orderRepository;
    private final InventoryService inventoryService;

    public ResolveItemDispositionUseCase(ReturnRequestRepository returnRequestRepository,
                                         OrderRepository orderRepository,
                                         InventoryService inventoryService) {
        this.returnRequestRepository = returnRequestRepository;
        this.orderRepository = orderRepository;
        this.inventoryService = inventoryService;
    }

    @Transactional
    public ReturnRequestDto execute(UUID returnId, ItemDisposition disposition, String note) {
        ReturnRequest request = returnRequestRepository.findById(returnId)
                .orElseThrow(() -> new DomainException("Return not found: " + returnId));

        request.resolveDisposition(disposition, note);

        if (disposition == ItemDisposition.RESTOCKED) {
            Order order = orderRepository.findById(request.getOrderId())
                    .orElseThrow(() -> new DomainException("Order not found: " + request.getOrderId()));
            for (OrderItem item : order.getItems()) {
                // returnToStock, not release: by now the units were confirmed out of both on-hand
                // and reserved, so only on-hand goes back up.
                inventoryService.returnToStock(item.getProductId(), item.getQuantity(),
                        item.getVariantColor(), item.getVariantSize());
            }
        }

        return ReturnRequestMapper.toDto(returnRequestRepository.save(request));
    }
}
