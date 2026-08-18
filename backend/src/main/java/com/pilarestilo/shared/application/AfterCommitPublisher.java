package com.pilarestilo.shared.application;

import com.pilarestilo.shared.domain.DomainEvent;
import com.pilarestilo.shared.domain.DomainEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Publishes a domain event once the transaction that produced it has committed.
 *
 * <p>Announcing from inside the transaction is a race this codebase has lost twice. The consumer
 * reads the row the event describes: with Kafka it is a different process entirely, and it gets
 * there first — the boleta email logged "could not be read back", and the review summary listener
 * wrote a rating of zero over a correct one, both for this reason.
 *
 * <p>Outside a transaction the event goes out immediately, which is what a caller with no
 * transaction of its own means.
 */
@Component
public class AfterCommitPublisher {

    private final DomainEventPublisher eventPublisher;

    public AfterCommitPublisher(DomainEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void publish(DomainEvent event) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            eventPublisher.publish(event);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                eventPublisher.publish(event);
            }
        });
    }
}
