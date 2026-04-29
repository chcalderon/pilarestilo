package com.pilarestilo.orderservice.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "system_settings")
public class SystemSettingsEntity {

    @Id
    private Short id;

    @Column(name = "bank_transfer_account_holder", length = 160)
    private String bankTransferAccountHolder;

    @Column(name = "bank_transfer_contact_email", length = 255)
    private String bankTransferContactEmail;

    @Column(name = "bank_transfer_account_number", length = 120)
    private String bankTransferAccountNumber;

    @Column(name = "bank_transfer_bank_name", length = 120)
    private String bankTransferBankName;

    @Column(name = "bank_transfer_account_type", length = 80)
    private String bankTransferAccountType;

    public Short getId() {
        return id;
    }

    public String getBankTransferAccountHolder() {
        return bankTransferAccountHolder;
    }

    public String getBankTransferContactEmail() {
        return bankTransferContactEmail;
    }

    public String getBankTransferAccountNumber() {
        return bankTransferAccountNumber;
    }

    public String getBankTransferBankName() {
        return bankTransferBankName;
    }

    public String getBankTransferAccountType() {
        return bankTransferAccountType;
    }
}
