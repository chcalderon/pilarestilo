package com.pilarestilo.privacy.application.usecases;

import com.pilarestilo.privacy.domain.enums.ConsentType;
import com.pilarestilo.privacy.domain.model.DataConsent;
import com.pilarestilo.privacy.domain.ports.DataConsentRepository;
import com.pilarestilo.systemsettings.domain.ports.SystemSettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Writes down what a customer agreed to, under the version that was published at the time.
 *
 * <p>The version is read here rather than passed in: a caller that supplies it can supply the wrong
 * one, and the only version that means anything is the one the shop was showing when the customer
 * accepted.
 */
@Service
public class RecordConsentUseCase {

    private static final Logger log = LoggerFactory.getLogger(RecordConsentUseCase.class);

    private final DataConsentRepository consentRepository;
    private final SystemSettingsRepository systemSettingsRepository;

    public RecordConsentUseCase(DataConsentRepository consentRepository,
                                SystemSettingsRepository systemSettingsRepository) {
        this.consentRepository = consentRepository;
        this.systemSettingsRepository = systemSettingsRepository;
    }

    /**
     * Records one acceptance, unless the same version is already standing.
     *
     * <p>Re-accepting a version that was already accepted changes nothing: it is the same promise,
     * and a second row would only make the history harder to read. Accepting after the shop
     * published a new version is a new row, which is exactly the fact worth keeping.
     */
    @Transactional
    public void execute(UUID userId, ConsentType type, String ipAddress, String userAgent) {
        String version = currentVersion(type);
        Optional<DataConsent> live = consentRepository.findLive(userId, type);
        if (live.isPresent() && live.get().getPolicyVersion().equals(version)) {
            return;
        }
        consentRepository.save(DataConsent.accept(userId, type, version, ipAddress, userAgent));
        log.debug("Recorded {} consent for {} under version {}", type, userId, version);
    }

    /** Withdrawing marketing. Terms and privacy are conditions of buying, and are not withdrawable. */
    @Transactional
    public void revokeMarketing(UUID userId) {
        consentRepository.findLive(userId, ConsentType.MARKETING).ifPresent(consent -> {
            consent.revoke();
            consentRepository.save(consent);
        });
    }

    private String currentVersion(ConsentType type) {
        var settings = systemSettingsRepository.get();
        return type == ConsentType.PRIVACY
                ? settings.getPrivacyPolicyVersion()
                : settings.getTermsVersion();
    }
}
