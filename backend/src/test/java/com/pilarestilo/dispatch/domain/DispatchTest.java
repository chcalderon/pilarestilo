package com.pilarestilo.dispatch.domain;

import com.pilarestilo.dispatch.domain.enums.DispatchStatus;
import com.pilarestilo.dispatch.domain.model.Dispatch;
import com.pilarestilo.shared.domain.DomainException;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DispatchTest {

    @Test
    void new_dispatch_is_pending() {
        Dispatch d = Dispatch.create(UUID.randomUUID());
        assertEquals(DispatchStatus.PENDING, d.getStatus());
        assertNull(d.getDispatcherId());
    }

    @Test
    void can_claim_pending_dispatch() {
        Dispatch d = Dispatch.create(UUID.randomUUID());
        UUID dispatcher = UUID.randomUUID();
        d.claim(dispatcher);
        assertEquals(DispatchStatus.IN_PROGRESS, d.getStatus());
        assertEquals(dispatcher, d.getDispatcherId());
    }

    @Test
    void can_unclaim_in_progress_dispatch() {
        Dispatch d = Dispatch.create(UUID.randomUUID());
        d.claim(UUID.randomUUID());
        d.unclaim();
        assertEquals(DispatchStatus.PENDING, d.getStatus());
        assertNull(d.getDispatcherId());
    }

    @Test
    void cannot_claim_already_claimed_dispatch() {
        Dispatch d = Dispatch.create(UUID.randomUUID());
        d.claim(UUID.randomUUID());
        UUID anotherDispatcher = UUID.randomUUID();

        assertThrows(DomainException.class, () -> d.claim(anotherDispatcher));
    }

    @Test
    void can_dispatch_from_in_progress() {
        Dispatch d = Dispatch.create(UUID.randomUUID());
        d.claim(UUID.randomUUID());
        d.dispatch("Chilexpress", "CH123456", null, null);
        assertEquals(DispatchStatus.DISPATCHED, d.getStatus());
        assertEquals("CH123456", d.getTrackingCode());
    }

    @Test
    void cannot_dispatch_from_pending() {
        Dispatch d = Dispatch.create(UUID.randomUUID());
        assertThrows(DomainException.class, () -> d.dispatch("Chilexpress", "X", null, null));
    }

    @Test
    void can_deliver_from_dispatched() {
        Dispatch d = Dispatch.create(UUID.randomUUID());
        d.claim(UUID.randomUUID());
        d.dispatch("Chilexpress", "X", null, null);
        d.deliver();
        assertEquals(DispatchStatus.DELIVERED, d.getStatus());
    }

    @Test
    void can_fail_from_dispatched() {
        Dispatch d = Dispatch.create(UUID.randomUUID());
        d.claim(UUID.randomUUID());
        d.dispatch("Chilexpress", "X", null, null);
        d.fail("Dirección incorrecta");
        assertEquals(DispatchStatus.FAILED, d.getStatus());
    }
}
