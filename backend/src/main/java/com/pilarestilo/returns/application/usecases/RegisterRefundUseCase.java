package com.pilarestilo.returns.application.usecases;

import com.pilarestilo.returns.application.dto.ReturnRequestDto;
import com.pilarestilo.returns.application.mappers.ReturnRequestMapper;
import com.pilarestilo.returns.domain.enums.RefundMethod;
import com.pilarestilo.returns.domain.events.RefundRegistered;
import com.pilarestilo.returns.domain.model.RefundAccount;
import com.pilarestilo.returns.domain.model.ReturnRequest;
import com.pilarestilo.returns.domain.ports.ReturnRequestRepository;
import com.pilarestilo.shared.application.AfterCommitPublisher;
import com.pilarestilo.shared.application.Money;
import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.systemsettings.infrastructure.security.SystemSettingsCryptoService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The money going back, and the bank details it needs to get there.
 *
 * <p>Those details are asked for when the return is opened rather than at checkout: the great
 * majority of purchases are never returned, and the Ley 21.719 asks for no more data than the
 * purpose needs. The number is encrypted with the same AES/GCM that protects the shop's own secrets,
 * and {@link ReturnRequest#registerRefund} erases it once the money has moved — what identifies the
 * payment afterwards is the operation reference and the last four digits, not the account.
 */
@Service
public class RegisterRefundUseCase {

    private final ReturnRequestRepository returnRequestRepository;
    private final SystemSettingsCryptoService cryptoService;
    private final AfterCommitPublisher eventPublisher;

    public RegisterRefundUseCase(ReturnRequestRepository returnRequestRepository,
                                 SystemSettingsCryptoService cryptoService,
                                 AfterCommitPublisher eventPublisher) {
        this.returnRequestRepository = returnRequestRepository;
        this.cryptoService = cryptoService;
        this.eventPublisher = eventPublisher;
    }

    /** Attaches where a transfer refund should be sent. Only needed for {@link RefundMethod#TRANSFERENCIA}. */
    @Transactional
    public ReturnRequestDto attachAccount(UUID returnId, String holder, String rut, String bankName,
                                          String accountType, String accountNumber) {
        ReturnRequest request = load(returnId);
        if (accountNumber == null || accountNumber.isBlank()) {
            throw new DomainException("A transfer refund needs the account number");
        }
        String digits = accountNumber.trim();
        request.attachRefundAccount(RefundAccount.of(
                holder, rut, bankName, accountType,
                cryptoService.encrypt(digits),
                digits.length() <= 4 ? digits : digits.substring(digits.length() - 4)));
        return ReturnRequestMapper.toDto(returnRequestRepository.save(request));
    }

    @Transactional
    public ReturnRequestDto execute(UUID returnId, BigDecimal amount, String currency,
                                    RefundMethod method, String reference, String fileUrl) {
        ReturnRequest request = load(returnId);
        Money refund = currency == null || currency.isBlank()
                ? Money.of(amount)
                : Money.of(amount, currency);
        request.registerRefund(refund, method, reference, fileUrl);
        ReturnRequest saved = returnRequestRepository.save(request);
        eventPublisher.publish(new RefundRegistered(saved.getId(), saved.getOrderId(), Instant.now()));
        return ReturnRequestMapper.toDto(saved);
    }

    private ReturnRequest load(UUID returnId) {
        return returnRequestRepository.findById(returnId)
                .orElseThrow(() -> new DomainException("Return not found: " + returnId));
    }
}
