package com.pilarestilo.notificationservice.infrastructure.adapters;

import com.pilarestilo.notificationservice.shared.DomainException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SystemSettingsCryptoServiceTest {

    private final SystemSettingsCryptoService crypto =
            new SystemSettingsCryptoService("U2VjcmV0U3lzdGVtU2V0dGluZ3MxMjM0NTY3ODkw");

    @Test
    void round_trips_a_secret() {
        String cipher = crypto.encrypt("smtp-password-123");
        assertThat(cipher).isNotEqualTo("smtp-password-123");
        assertThat(crypto.decrypt(cipher)).isEqualTo("smtp-password-123");
    }

    @Test
    void a_shared_key_decrypts_what_the_monolith_encrypted() {
        var monolithSideKey = new SystemSettingsCryptoService("U2VjcmV0U3lzdGVtU2V0dGluZ3MxMjM0NTY3ODkw");
        String fromMonolith = monolithSideKey.encrypt("twilio-token");
        assertThat(crypto.decrypt(fromMonolith)).isEqualTo("twilio-token");
    }

    @Test
    void blank_input_decrypts_to_empty() {
        assertThat(crypto.decrypt(null)).isEmpty();
        assertThat(crypto.decrypt("")).isEmpty();
    }

    @Test
    void tampered_ciphertext_is_rejected() {
        assertThatThrownBy(() -> crypto.decrypt("not-real-ciphertext"))
                .isInstanceOf(DomainException.class);
    }
}
