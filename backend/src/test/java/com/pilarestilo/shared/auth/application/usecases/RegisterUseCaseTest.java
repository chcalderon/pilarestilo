package com.pilarestilo.shared.auth.application.usecases;

import com.pilarestilo.discount.application.dto.WelcomeDiscountDto;
import com.pilarestilo.discount.application.usecases.IssueWelcomeDiscountUseCase;
import com.pilarestilo.privacy.application.usecases.RecordConsentUseCase;
import com.pilarestilo.privacy.domain.enums.ConsentType;
import com.pilarestilo.shared.application.AfterCommitPublisher;
import com.pilarestilo.shared.auth.domain.ports.PasswordEncoder;
import com.pilarestilo.shared.auth.infrastructure.JwtTokenProvider;
import com.pilarestilo.user.domain.enums.UserRole;
import com.pilarestilo.user.domain.events.UserRegistered;
import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.user.domain.model.User;
import com.pilarestilo.user.domain.ports.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegisterUseCaseTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtTokenProvider jwtTokenProvider;
    @Mock RecordConsentUseCase recordConsentUseCase;
    @Mock AfterCommitPublisher afterCommitPublisher;
    @Mock IssueWelcomeDiscountUseCase issueWelcomeDiscountUseCase;

    RegisterUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RegisterUseCase(
                userRepository, passwordEncoder, jwtTokenProvider, recordConsentUseCase,
                afterCommitPublisher, issueWelcomeDiscountUseCase);

        when(userRepository.existsByEmail("camila@example.com")).thenReturn(false);
        // lenient: the duplicate-email test throws before ever reaching this call.
        lenient().when(passwordEncoder.encode("secret123")).thenReturn("hash");
        lenient().when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(jwtTokenProvider.generateAccessToken(
                        any(), any(), any(UserRole.class), anyList(), anyList(), anyInt()))
                .thenReturn("access-token");
        lenient().when(jwtTokenProvider.generateRefreshToken(any(), anyInt())).thenReturn("refresh-token");
        lenient().when(issueWelcomeDiscountUseCase.issueFor(any(), anyBoolean()))
                .thenReturn(Optional.empty());
    }

    /** A new account should tell the customer it exists, same as an order does. */
    @Test
    void publishesUserRegisteredAfterCreatingTheAccount() {
        useCase.execute("camila@example.com", "secret123", "Camila Torres", "127.0.0.1", "Mozilla", false);

        verify(afterCommitPublisher).publish(argThat(event ->
                event instanceof UserRegistered registered && registered.userId() != null));
    }

    @Test
    void recordsMarketingConsentWhenAccepted() {
        useCase.execute("camila@example.com", "secret123", "Camila Torres", "127.0.0.1", "Mozilla", true);

        verify(recordConsentUseCase).execute(any(), eq(ConsentType.MARKETING), eq("127.0.0.1"), eq("Mozilla"));
    }

    @Test
    void doesNotRecordMarketingConsentWhenNotAccepted() {
        useCase.execute("camila@example.com", "secret123", "Camila Torres", "127.0.0.1", "Mozilla", false);

        verify(recordConsentUseCase, never()).execute(any(), eq(ConsentType.MARKETING), any(), any());
    }

    @Test
    void includesTheIssuedCouponInThePublishedEvent() {
        WelcomeDiscountDto issued = new WelcomeDiscountDto(
                "BIENVENIDA-ABC123", "PERCENTAGE", BigDecimal.TEN, BigDecimal.ZERO,
                LocalDate.now().plusDays(30));
        when(issueWelcomeDiscountUseCase.issueFor(any(), eq(true))).thenReturn(Optional.of(issued));

        useCase.execute("camila@example.com", "secret123", "Camila Torres", "127.0.0.1", "Mozilla", true);

        verify(afterCommitPublisher).publish(argThat(event -> {
            UserRegistered registered = (UserRegistered) event;
            return registered.welcomeDiscount() != null
                    && "BIENVENIDA-ABC123".equals(registered.welcomeDiscount().code());
        }));
    }

    @Test
    void publishesNoCouponWhenNoneWasIssued() {
        useCase.execute("camila@example.com", "secret123", "Camila Torres", "127.0.0.1", "Mozilla", true);

        verify(afterCommitPublisher).publish(argThat(event ->
                ((UserRegistered) event).welcomeDiscount() == null));
    }

    @Test
    void issuesTheTokensWithTheNewUsersSessionVersion() {
        useCase.execute("camila@example.com", "secret123", "Camila Torres", "127.0.0.1", "Mozilla", false);

        verify(jwtTokenProvider).generateAccessToken(any(), any(), any(UserRole.class), anyList(), anyList(), eq(1));
        verify(jwtTokenProvider).generateRefreshToken(any(), eq(1));
    }

    /**
     * The duplicate-email check has to compare against the same normalized form
     * {@link User#create} stores, or a retry under different letter-casing sails past this
     * friendly check and hits the DB's unique constraint on save instead -- an unhandled 500 in
     * place of this method's own message.
     */
    @Test
    void rejectsARegistrationWhoseEmailOnlyDiffersByCase() {
        when(userRepository.existsByEmail("camila@example.com")).thenReturn(true);

        assertThatThrownBy(() ->
                useCase.execute("Camila@Example.com", "secret123", "Camila Torres", "127.0.0.1", "Mozilla", false))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("camila@example.com");
    }

    /** A coupon that fails to issue must never take the account down with it. */
    @Test
    void registrationSucceedsEvenWhenIssuingTheCouponFails() {
        when(issueWelcomeDiscountUseCase.issueFor(any(), anyBoolean()))
                .thenThrow(new RuntimeException("boom"));

        var result = useCase.execute("camila@example.com", "secret123", "Camila Torres", "127.0.0.1", "Mozilla", true);

        assertNotNull(result.accessToken());
        verify(afterCommitPublisher).publish(argThat(event ->
                ((UserRegistered) event).welcomeDiscount() == null));
    }
}
