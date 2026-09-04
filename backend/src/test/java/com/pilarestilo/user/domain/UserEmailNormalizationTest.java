package com.pilarestilo.user.domain;

import com.pilarestilo.user.domain.enums.UserRole;
import com.pilarestilo.user.domain.model.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link User#normalizeEmail} is the single form every lookup and write has to agree on --
 * RegisterUseCase, LoginUseCase, GoogleLoginUseCase and CreateUserUseCase all compare against it
 * instead of each doing its own trim/lowercase. These pin the normalization itself; the use
 * cases' own tests pin that they actually call it before comparing.
 */
class UserEmailNormalizationTest {

    @Test
    void lowercasesAndTrimsForComparison() {
        assertEquals("maria@gmail.com", User.normalizeEmail("  Maria@Gmail.com  "));
    }

    @Test
    void isIdempotentOnAnAlreadyNormalizedAddress() {
        assertEquals("maria@gmail.com", User.normalizeEmail("maria@gmail.com"));
    }

    @Test
    void returnsNullForNullRatherThanThrowing() {
        assertNull(User.normalizeEmail(null));
    }

    @Test
    void createStoresTheNormalizedForm() {
        User u = User.create(" Ana@Correo.CL ", "Ana", UserRole.CUSTOMER, "hash");
        assertEquals("ana@correo.cl", u.getEmail());
    }
}
