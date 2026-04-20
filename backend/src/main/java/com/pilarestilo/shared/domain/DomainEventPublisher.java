package com.pilarestilo.shared.domain;

public interface DomainEventPublisher {
    void publish(DomainEvent event);
}
