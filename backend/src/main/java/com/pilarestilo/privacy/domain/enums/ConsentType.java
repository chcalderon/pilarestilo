package com.pilarestilo.privacy.domain.enums;

/**
 * What a customer can agree to, kept apart because they are not the same promise.
 *
 * <p>{@code TERMS} and {@code PRIVACY} are conditions of buying at all. {@code MARKETING} is not:
 * the Ley 21.719 asks for it separately and freely given, so bundling it with the other two would
 * make all three worthless as evidence.
 */
public enum ConsentType {
    TERMS,
    PRIVACY,
    MARKETING
}
