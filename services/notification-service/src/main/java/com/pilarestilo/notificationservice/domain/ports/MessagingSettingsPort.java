package com.pilarestilo.notificationservice.domain.ports;

import com.pilarestilo.notificationservice.domain.view.MessagingSettings;

public interface MessagingSettingsPort {

    /** The shop's current messaging configuration. Never null — a missing row yields defaults. */
    MessagingSettings current();
}
