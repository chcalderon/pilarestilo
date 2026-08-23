package com.pilarestilo.discount.application.usecases;

import com.pilarestilo.discount.application.dto.WelcomeDiscountDto;
import com.pilarestilo.discount.domain.enums.DiscountType;
import com.pilarestilo.discount.domain.model.Discount;
import com.pilarestilo.discount.domain.ports.DiscountRepository;
import com.pilarestilo.shared.application.Money;
import com.pilarestilo.systemsettings.domain.model.WelcomeDiscountSettings;
import com.pilarestilo.systemsettings.domain.ports.SystemSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

/**
 * Mints the one-time coupon a new account gets, if the shop is running one.
 *
 * <p>Deliberately does not publish {@code DiscountCodeAssigned} — that event is the admin's manual
 * assignment and already sends its own "código exclusivo para ti" email. Publishing it here would
 * fire a second email alongside the welcome one, in the same second.
 */
@Service
public class IssueWelcomeDiscountUseCase {

    /** Fixed on purpose: the owner asked for 30 days flat, not a configurable value. */
    static final int VALIDITY_DAYS = 30;

    /** The coupon's calendar days are the shop's own, not whatever zone the server happens to run in. */
    static final ZoneId STORE_ZONE = ZoneId.of("America/Santiago");

    private static final String CODE_PREFIX = "BIENVENIDA-";
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 6;
    private static final int MAX_CODE_ATTEMPTS = 5;

    private final DiscountRepository discountRepository;
    private final SystemSettingsRepository systemSettingsRepository;
    private final SecureRandom random = new SecureRandom();

    public IssueWelcomeDiscountUseCase(DiscountRepository discountRepository,
                                        SystemSettingsRepository systemSettingsRepository) {
        this.discountRepository = discountRepository;
        this.systemSettingsRepository = systemSettingsRepository;
    }

    @Transactional
    public Optional<WelcomeDiscountDto> issueFor(UUID userId, boolean acceptedMarketing) {
        WelcomeDiscountSettings settings = systemSettingsRepository.get().getWelcomeDiscount();
        if (!settings.enabled()) {
            return Optional.empty();
        }
        if (settings.requiresMarketingConsent() && !acceptedMarketing) {
            return Optional.empty();
        }

        LocalDate today = LocalDate.now(STORE_ZONE);
        LocalDate validUntil = today.plusDays(VALIDITY_DAYS);
        Discount discount = Discount.create(
                nextAvailableCode(),
                DiscountType.valueOf(settings.type()),
                settings.value(),
                Money.of(settings.minOrderAmount()),
                today,
                validUntil,
                1);
        discount.setAssignedUserId(userId);
        Discount saved = discountRepository.save(discount);

        return Optional.of(new WelcomeDiscountDto(
                saved.getCode(), saved.getType().name(), saved.getValue(),
                saved.getMinOrderAmount().amount(), saved.getValidUntil()));
    }

    private String nextAvailableCode() {
        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            String candidate = generateCode();
            if (discountRepository.findByCode(candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not generate a unique welcome discount code");
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder(CODE_PREFIX);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
        }
        return sb.toString();
    }
}
