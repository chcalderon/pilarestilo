package com.pilarestilo.productservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class DataSourceRoutingConfigurationTest {

    @Test
    void creates_properties_beans() {
        DataSourceRoutingConfiguration config = new DataSourceRoutingConfiguration();

        assertNotNull(config.writeDataSourceProperties());
        assertNotNull(config.readReplicaDataSourceProperties());
    }

    @Test
    void routing_datasource_uses_write_when_replica_disabled() {
        DataSourceRoutingConfiguration config = new DataSourceRoutingConfiguration();
        DataSource write = mock(DataSource.class);
        DataSourceProperties readReplicaProperties = new DataSourceProperties();

        DataSource routing = config.routingDataSource(write, readReplicaProperties, false);

        assertNotNull(routing);
        assertEquals(LazyConnectionDataSourceProxy.class, routing.getClass());
    }

    @Test
    void routing_datasource_throws_when_replica_enabled_without_url() {
        DataSourceRoutingConfiguration config = new DataSourceRoutingConfiguration();
        DataSource write = mock(DataSource.class);
        DataSourceProperties readReplicaProperties = new DataSourceProperties();

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> config.routingDataSource(write, readReplicaProperties, true));
        assertEquals("Read replica is enabled but APP_DB_READ_REPLICA_URL is missing.", ex.getMessage());
    }
}
