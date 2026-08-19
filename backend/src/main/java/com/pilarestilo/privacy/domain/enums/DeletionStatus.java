package com.pilarestilo.privacy.domain.enums;

/**
 * Where a deletion request stands.
 *
 * <p>There is no "DELETED": the shop anonymises, because the boleta behind a sale has its own
 * retention and outlives the account that made it.
 */
public enum DeletionStatus {
    REQUESTED,
    ANONYMISED,
    REFUSED
}
