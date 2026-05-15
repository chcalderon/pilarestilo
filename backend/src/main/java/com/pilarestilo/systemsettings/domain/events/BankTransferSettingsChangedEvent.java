package com.pilarestilo.systemsettings.domain.events;

public record BankTransferSettingsChangedEvent(
        boolean enabled,
        String cron,
        int timeoutMinutes
) {}
