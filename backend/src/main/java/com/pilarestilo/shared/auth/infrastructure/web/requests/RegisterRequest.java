package com.pilarestilo.shared.auth.infrastructure.web.requests;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8) String password,
        @NotBlank String fullName,
        Boolean acceptsMarketing
) {
    /** A caller that omits the field (every register call before this one) means "no". */
    public RegisterRequest {
        if (acceptsMarketing == null) {
            acceptsMarketing = Boolean.FALSE;
        }
    }
}
