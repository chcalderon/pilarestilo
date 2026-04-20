package com.pilarestilo.customercredit.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CustomerCreditDto(
        UUID id,
        UUID customerId,
        BigDecimal balanceAmount,
        String balanceCurrency
) {}
