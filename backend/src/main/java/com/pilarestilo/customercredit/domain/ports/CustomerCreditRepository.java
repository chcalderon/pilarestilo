package com.pilarestilo.customercredit.domain.ports;

import com.pilarestilo.customercredit.domain.model.CustomerCredit;

import java.util.Optional;
import java.util.UUID;

public interface CustomerCreditRepository {

    CustomerCredit save(CustomerCredit customerCredit);

    Optional<CustomerCredit> findByCustomerId(UUID customerId);
}
