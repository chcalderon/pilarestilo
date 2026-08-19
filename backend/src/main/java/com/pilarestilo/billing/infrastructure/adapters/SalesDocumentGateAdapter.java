package com.pilarestilo.billing.infrastructure.adapters;

import com.pilarestilo.billing.domain.ports.SalesDocumentRepository;
import com.pilarestilo.dispatch.domain.ports.SalesDocumentGate;
import com.pilarestilo.systemsettings.domain.ports.SystemSettingsRepository;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
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
        if (!required()) {
            return false;
        }
        return salesDocumentRepository.findLiveByOrderId(orderId).isEmpty();
    }

    @Override
    public Set<UUID> blockedAmong(Collection<UUID> orderIds) {
        if (orderIds.isEmpty() || !required()) {
            return Set.of();
        }
        Set<UUID> blocked = new HashSet<>(orderIds);
        blocked.removeAll(salesDocumentRepository.findOrderIdsWithLiveDocument(orderIds));
        return blocked;
    }

    /** The shop can switch the rule off, and then nothing is blocked however few boletas exist. */
    private boolean required() {
        return systemSettingsRepository.get().getTax().documentRequiredBeforeDispatch();
    }
}
