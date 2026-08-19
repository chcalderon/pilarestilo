package com.pilarestilo.privacy.domain.ports;

import com.pilarestilo.privacy.domain.enums.ConsentType;
import com.pilarestilo.privacy.domain.model.DataConsent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DataConsentRepository {

    DataConsent save(DataConsent consent);

    /** Everything this customer ever agreed to, newest first: the history is the evidence. */
    List<DataConsent> findByUserId(UUID userId);

    /** The standing consent of one kind, if any. Absent means never given or since withdrawn. */
    Optional<DataConsent> findLive(UUID userId, ConsentType type);
}
