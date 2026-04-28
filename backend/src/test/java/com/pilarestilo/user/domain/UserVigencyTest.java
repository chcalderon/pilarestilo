package com.pilarestilo.user.domain;

import com.pilarestilo.user.domain.enums.UserRole;
import com.pilarestilo.user.domain.model.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class UserVigencyTest {

    @Test
    void new_user_has_null_vigency_fields() {
        User u = User.create("a@b.com", "Test", UserRole.SELLER, "hash");
        assertNull(u.getWorkerVigencyStart());
        assertNull(u.getWorkerVigencyEnd());
    }

    @Test
    void can_set_vigency_dates() {
        User u = User.create("a@b.com", "Test", UserRole.SELLER, "hash");
        LocalDate start = LocalDate.of(2026, 5, 1);
        LocalDate end = LocalDate.of(2026, 12, 31);
        u.setWorkerVigencyStart(start);
        u.setWorkerVigencyEnd(end);
        assertEquals(start, u.getWorkerVigencyStart());
        assertEquals(end, u.getWorkerVigencyEnd());
    }

    @Test
    void vigency_end_can_be_null_for_open_ended() {
        User u = User.create("a@b.com", "Test", UserRole.SELLER, "hash");
        u.setWorkerVigencyStart(LocalDate.of(2026, 5, 1));
        assertNull(u.getWorkerVigencyEnd());
    }
}
