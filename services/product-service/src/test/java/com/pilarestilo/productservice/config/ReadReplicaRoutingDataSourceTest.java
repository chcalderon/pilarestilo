package com.pilarestilo.productservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReadReplicaRoutingDataSourceTest {

    @Test
    void returns_write_key_without_read_only_tx() {
        ReadReplicaRoutingDataSource ds = new ReadReplicaRoutingDataSource();

        Object key = ds.determineCurrentLookupKey();

        assertEquals(ReadReplicaRoutingDataSource.WRITE, key);
    }

    @Test
    void returns_read_key_with_read_only_tx() {
        ReadReplicaRoutingDataSource ds = new ReadReplicaRoutingDataSource();
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
        try {
            Object key = ds.determineCurrentLookupKey();
            assertEquals(ReadReplicaRoutingDataSource.READ, key);
        } finally {
            TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
        }
    }
}
