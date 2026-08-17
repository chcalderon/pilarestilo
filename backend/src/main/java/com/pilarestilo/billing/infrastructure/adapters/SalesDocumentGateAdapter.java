package com.pilarestilo.billing.infrastructure.adapters;

import com.pilarestilo.billing.domain.ports.SalesDocumentRepository;
import com.pilarestilo.dispatch.domain.ports.SalesDocumentGate;
import com.pilarestilo.systemsettings.domain.ports.SystemSettingsRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class SalesDocumentGateAdapter implements SalesDocumentGate {

    private final SalesDocumentRepository salesDocumentRepository;
    private final SystemSettingsRepository systemSettingsRepository;

    public SalesDocumentGateAdapter(SalesDocumentRepository salesDocumentRepository,
                                    SystemSettingsRepository systemSettingsRepository) {
        this.salesDocumentRepository = salesDocumentRepository;
        this.systemSettingsRepository = systemSettingsRepository;
    }

    @Override
    public boolean blocksDispatch(UUID orderId) {
        if (!systemSettingsRepository.get().getTax().documentRequiredBeforeDispatch()) {
            return false;
        }
        return salesDocumentRepository.findLiveByOrderId(orderId).isEmpty();
    }
}
