package com.pilarestilo.discount.application.usecases;

import com.pilarestilo.discount.application.dto.WelcomeDiscountDto;
import com.pilarestilo.discount.domain.model.Discount;
import com.pilarestilo.discount.domain.ports.DiscountRepository;
import com.pilarestilo.systemsettings.domain.model.SystemSettings;
import com.pilarestilo.systemsettings.domain.model.WelcomeDiscountSettings;
import com.pilarestilo.systemsettings.domain.ports.SystemSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IssueWelcomeDiscountUseCaseTest {

    @Mock DiscountRepository discountRepository;
    @Mock SystemSettingsRepository systemSettingsRepository;

    IssueWelcomeDiscountUseCase useCase;

    final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new IssueWelcomeDiscountUseCase(discountRepository, systemSettingsRepository);
    }

    private SystemSettings settingsWith(WelcomeDiscountSettings welcomeDiscount) {
        SystemSettings settings = SystemSettings.createDefault();
        settings.update(
                "+56900000000", null, null,
                null, null, null, null, null,
                false, true, "MERCADO_PAGO",
                null, null, null, null, null, null, null,
                null, null, null, null, null, null, false, null,
                null, null, null, null, null, true, true,
                null, null, null, null,
                null, null, null, null, null, null, null, null,
                null, null, null, null, null,
                null, null, null, null,
                null, null, null,
                true, 30, "0 */15 * * * *",
                com.pilarestilo.systemsettings.domain.model.StoreTaxSettings.empty(),
                welcomeDiscount,
                com.pilarestilo.systemsettings.domain.model.PolicyVersions.initial(),
                "test");
        return settings;
    }

    @Test
    void doesNothingWhenTheWelcomeDiscountIsOff() {
        when(systemSettingsRepository.get()).thenReturn(settingsWith(WelcomeDiscountSettings.disabled()));

        Optional<WelcomeDiscountDto> result = useCase.issueFor(userId, true);

        assertTrue(result.isEmpty());
        verify(discountRepository, never()).save(any());
    }

    @Test
    void doesNothingWhenMarketingIsRequiredButNotAccepted() {
        WelcomeDiscountSettings enabled = WelcomeDiscountSettings.of(
                true, "PERCENTAGE", BigDecimal.TEN, BigDecimal.ZERO, true);
        when(systemSettingsRepository.get()).thenReturn(settingsWith(enabled));

        Optional<WelcomeDiscountDto> result = useCase.issueFor(userId, false);

        assertTrue(result.isEmpty());
        verify(discountRepository, never()).save(any());
    }

    @Test
    void issuesACodeWhenMarketingIsNotRequired() {
        WelcomeDiscountSettings enabled = WelcomeDiscountSettings.of(
                true, "PERCENTAGE", BigDecimal.TEN, BigDecimal.ZERO, false);
        when(systemSettingsRepository.get()).thenReturn(settingsWith(enabled));
        when(discountRepository.findByCode(anyString())).thenReturn(Optional.empty());
        lenient().when(discountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Optional<WelcomeDiscountDto> result = useCase.issueFor(userId, false);

        assertTrue(result.isPresent());
    }

    @Test
    void issuesASingleUseCodeAssignedToTheNewAccount() {
        WelcomeDiscountSettings enabled = WelcomeDiscountSettings.of(
                true, "PERCENTAGE", BigDecimal.TEN, BigDecimal.ZERO, true);
        when(systemSettingsRepository.get()).thenReturn(settingsWith(enabled));
        when(discountRepository.findByCode(anyString())).thenReturn(Optional.empty());
        when(discountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Optional<WelcomeDiscountDto> result = useCase.issueFor(userId, true);

        assertTrue(result.isPresent());
        WelcomeDiscountDto dto = result.get();
        assertTrue(dto.code().startsWith("BIENVENIDA-"));
        assertEquals(0, BigDecimal.TEN.compareTo(dto.value()));
        assertEquals(LocalDate.now().plusDays(30), dto.validUntil());

        ArgumentCaptor<Discount> captor = ArgumentCaptor.forClass(Discount.class);
        verify(discountRepository).save(captor.capture());
        Discount saved = captor.getValue();
        assertEquals(userId, saved.getAssignedUserId());
        assertEquals(1, saved.getMaxUses());
    }

    @Test
    void retriesWhenTheGeneratedCodeAlreadyExists() {
        WelcomeDiscountSettings enabled = WelcomeDiscountSettings.of(
                true, "PERCENTAGE", BigDecimal.TEN, BigDecimal.ZERO, true);
        when(systemSettingsRepository.get()).thenReturn(settingsWith(enabled));
        when(discountRepository.findByCode(anyString()))
                .thenReturn(Optional.of(Discount.create("TAKEN", com.pilarestilo.discount.domain.enums.DiscountType.PERCENTAGE,
                        BigDecimal.TEN, com.pilarestilo.shared.application.Money.zero(),
                        LocalDate.now(), LocalDate.now().plusDays(1), 1)))
                .thenReturn(Optional.empty());
        when(discountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Optional<WelcomeDiscountDto> result = useCase.issueFor(userId, true);

        assertTrue(result.isPresent());
        verify(discountRepository, times(2)).findByCode(anyString());
    }
}
