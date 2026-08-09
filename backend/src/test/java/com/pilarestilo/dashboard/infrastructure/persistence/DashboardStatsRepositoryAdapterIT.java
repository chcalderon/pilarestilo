package com.pilarestilo.dashboard.infrastructure.persistence;

import com.pilarestilo.dashboard.application.port.out.DashboardStatsRepository;
import com.pilarestilo.dashboard.domain.model.DashboardStats;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
class DashboardStatsRepositoryAdapterIT {

    @Container
    @SuppressWarnings("resource") // lifecycle managed by @Testcontainers, not try-with-resources
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16")
            .withDatabaseName("pilarestilo_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    DashboardStatsRepository repo;

    @Test
    void adminStats_returnsNonNullSalesTotals() {
        DashboardStats.AdminStats stats = repo.getAdminStats();
        assertThat(stats.dailySales()).isNotNull();
        assertThat(stats.dailySales().amount()).isNotNegative();
        assertThat(stats.dailyRevenueSeries()).hasSizeLessThanOrEqualTo(7);
        assertThat(stats.topProducts()).hasSizeLessThanOrEqualTo(5);
    }

    @Test
    void sellerStats_returnsSnapshot() {
        UUID sellerId = UUID.randomUUID();
        DashboardStats.SellerStats stats = repo.getSellerStats(sellerId);
        assertThat(stats).isNotNull();
    }

    @Test
    void despachadorStats_returnsQueues() {
        UUID despachadorId = UUID.randomUUID();
        DashboardStats.DespachadorStats stats = repo.getDespachadorStats(despachadorId);
        assertThat(stats.pendingDispatches()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void administracionStats_returnsWorkerCounts() {
        DashboardStats.AdministracionStats stats = repo.getAdministracionStats();
        assertThat(stats.activeWorkers()).isGreaterThanOrEqualTo(0);
        assertThat(stats.expiringWorkers()).isNotNull();
    }
}
