package com.pilarestilo.productservice.config;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class DataSourceRoutingConfiguration {

    private static final Logger log = LoggerFactory.getLogger(DataSourceRoutingConfiguration.class);

    @Bean("writeDataSourceProperties")
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties writeDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean("readReplicaDataSourceProperties")
    @ConfigurationProperties("app.datasource.read-replica")
    public DataSourceProperties readReplicaDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean("writeDataSource")
    public DataSource writeDataSource(@Qualifier("writeDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    @Bean
    @Primary
    public DataSource routingDataSource(
            @Qualifier("writeDataSource") DataSource writeDataSource,
            @Qualifier("readReplicaDataSourceProperties") DataSourceProperties readReplicaProperties,
            @Value("${app.datasource.read-replica.enabled:false}") boolean readReplicaEnabled
    ) {
        DataSource readDataSource = resolveReadDataSource(readReplicaEnabled, readReplicaProperties, writeDataSource);

        ReadReplicaRoutingDataSource routingDataSource = new ReadReplicaRoutingDataSource();
        Map<Object, Object> targets = new HashMap<>();
        targets.put(ReadReplicaRoutingDataSource.WRITE, writeDataSource);
        targets.put(ReadReplicaRoutingDataSource.READ, readDataSource);
        routingDataSource.setTargetDataSources(targets);
        routingDataSource.setDefaultTargetDataSource(writeDataSource);
        routingDataSource.afterPropertiesSet();

        return new LazyConnectionDataSourceProxy(routingDataSource);
    }

    private DataSource resolveReadDataSource(
            boolean readReplicaEnabled,
            DataSourceProperties readReplicaProperties,
            DataSource writeDataSource
    ) {
        if (!readReplicaEnabled) {
            log.info("Read replica disabled or not configured. Product queries use primary datasource.");
            return writeDataSource;
        }
        if (readReplicaProperties.getUrl() == null || readReplicaProperties.getUrl().isBlank()) {
            throw new IllegalStateException("Read replica is enabled but APP_DB_READ_REPLICA_URL is missing.");
        }

        HikariDataSource readDataSource = readReplicaProperties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
        readDataSource.setReadOnly(true);
        readDataSource.setPoolName("product-service-read-replica-pool");
        log.info("Read replica datasource enabled for product-service catalog queries.");
        return readDataSource;
    }
}
