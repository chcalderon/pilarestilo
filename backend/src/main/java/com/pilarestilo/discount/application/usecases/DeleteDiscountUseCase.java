package com.pilarestilo.discount.application.usecases;

import com.pilarestilo.discount.domain.model.Discount;
import com.pilarestilo.discount.domain.ports.DiscountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Retires a discount code.
 *
 * <p>Deactivates rather than deletes. {@code discount_code_usages.discount_id} is
 * {@code ON DELETE CASCADE}, so removing the row took the whole redemption ledger with it:
 * every record of who had used the code, and — since the reserve-then-settle model landed —
 * the PENDING rows of orders still in flight. Those orders would then reach PAID or CANCELLED
 * with nothing left to settle or release, quietly stranding a redemption that had already
 * consumed a usage slot.
 *
 * <p>{@code Discount.validate} refuses an inactive code, so the effect the caller wants —
 * nobody can use it any more — is immediate. The row and its history stay.
 */
@Service
public class DeleteDiscountUseCase {

    private final DiscountRepository discountRepository;

    public DeleteDiscountUseCase(DiscountRepository discountRepository) {
        this.discountRepository = discountRepository;
    }

    @Transactional
    public void execute(UUID id) {
        Discount discount = discountRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Discount not found: " + id));

        /* Already retired: nothing to do, and re-saving would only touch updated_at. */
        if (!discount.isActive()) {
            return;
        }

        discount.deactivate();
        discountRepository.save(discount);
    }
}
