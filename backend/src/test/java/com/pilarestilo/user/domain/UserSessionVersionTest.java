package com.pilarestilo.user.domain;

import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.user.domain.enums.UserRole;
import com.pilarestilo.user.domain.model.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserSessionVersionTest {

    @Test
    void a_new_user_starts_at_session_version_1() {
        User u = User.create("a@b.com", "A B", UserRole.CUSTOMER, "hash");
        assertEquals(1, u.getSessionVersion());
    }

    @Test
    void incrementing_bumps_it() {
        User u = User.create("a@b.com", "A B", UserRole.CUSTOMER, "hash");
        u.incrementSessionVersion();
        u.incrementSessionVersion();
        assertEquals(3, u.getSessionVersion());
    }

    @Test
    void restore_rejects_below_1() {
        User u = User.create("a@b.com", "A B", UserRole.CUSTOMER, "hash");
        assertThrows(DomainException.class, () -> u.setSessionVersion(0));
    }

    @Test
    void restore_carries_a_persisted_value_back() {
        User u = User.create("a@b.com", "A B", UserRole.CUSTOMER, "hash");
        u.setSessionVersion(7);
        assertEquals(7, u.getSessionVersion());
    }
}
