package com.pilarestilo.shared.auth.domain.ports;

/**
 * Sends the password-reset code. Deliberately its own port, not the shop's notification pipeline:
 * account recovery must not depend on that pipeline being up or on an admin's channel toggle.
 */
public interface PasswordResetMailer {

    void sendResetCode(String toEmail, String fullName, String code);
}
