package com.pilarestilo.systemsettings.domain.model;

/**
 * Which version of the published texts the shop is currently showing.
 *
 * <p>Every consent is stored against one of these. Bumping a version is what turns every consent
 * already given into "older than what we publish now", which is the question the Ley 21.719 asks
 * the shop to be able to answer — and the reason the number has to live somewhere the code reads
 * rather than in the wording of a page.
 *
 * <p>A record rather than two more positional parameters, for the reason
 * {@link StoreTaxSettings} gives: {@link SystemSettings#reconstruct} already takes more arguments
 * than anyone can check by eye.
 */
public record PolicyVersions(String privacyPolicy, String terms) {

    private static final String INITIAL = "2026-08";

    public static PolicyVersions of(String privacyPolicy, String terms) {
        return new PolicyVersions(
                normalize(privacyPolicy),
                normalize(terms));
    }

    /** What a row seeded before the column existed carries. */
    public static PolicyVersions initial() {
        return new PolicyVersions(INITIAL, INITIAL);
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return INITIAL;
        }
        return value.trim();
    }
}
