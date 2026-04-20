package com.pilarestilo.shared.auth.infrastructure;

import com.pilarestilo.shared.auth.domain.ports.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class BCryptPasswordEncoderAdapter implements PasswordEncoder {

    private final org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder bcrypt =
            new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();

    @Override
    public String encode(String rawPassword) {
        return bcrypt.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String encodedPassword) {
        return bcrypt.matches(rawPassword, encodedPassword);
    }
}
