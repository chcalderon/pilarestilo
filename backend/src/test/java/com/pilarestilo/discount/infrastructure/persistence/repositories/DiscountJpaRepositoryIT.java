package com.pilarestilo.discount.infrastructure.persistence.repositories;

import com.pilarestilo.discount.domain.enums.DiscountType;
import com.pilarestilo.discount.infrastructure.persistence.entities.DiscountEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A single-use code (max_uses = 1, the shape of every welcome coupon) stopped counting as
 * "vigente" the moment it is redeemed, not only once its valid_until passes. Before this fix
 * {@code findActiveDiscounts} looked only at {@code active} and {@code valid_until}, so a spent
 * coupon kept showing up in the admin's "Vigentes" tab.
 */
@Testcontainers
@SpringBootTest
class DiscountJpaRepositoryIT {

    @Container
    @SuppressWarnings("resource")
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
    DiscountJpaRepository repository;

    private DiscountEntity save(int maxUses, int timesUsed, LocalDate validUntil) {
        DiscountEntity entity = new DiscountEntity();
        entity.setId(UUID.randomUUID());
        entity.setCode("IT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        entity.setType(DiscountType.PERCENTAGE);
        entity.setValue(BigDecimal.TEN);
        entity.setMinOrderAmount(BigDecimal.ZERO);
        entity.setMinOrderCurrency("CLP");
        entity.setValidFrom(LocalDate.now().minusDays(1));
        entity.setValidUntil(validUntil);
        entity.setMaxUses(maxUses);
        entity.setTimesUsed(timesUsed);
        entity.setActive(true);
        return repository.save(entity);
    }

    @Test
    void aFullyRedeemedSingleUseCodeIsNotActiveEvenBeforeItsDate() {
        DiscountEntity spent = save(1, 1, LocalDate.now().plusDays(30));

        assertThat(repository.findActiveDiscounts(LocalDate.now()))
                .extracting(DiscountEntity::getId)
                .doesNotContain(spent.getId());
    }

    @Test
    void aFullyRedeemedSingleUseCodeShowsUpAsExpired() {
        DiscountEntity spent = save(1, 1, LocalDate.now().plusDays(30));

        assertThat(repository.findExpiredDiscounts(LocalDate.now()))
                .extracting(DiscountEntity::getId)
                .contains(spent.getId());
    }

    @Test
    void anUnusedCodeStillWithinItsDatesIsActive() {
        DiscountEntity fresh = save(1, 0, LocalDate.now().plusDays(30));

        assertThat(repository.findActiveDiscounts(LocalDate.now()))
                .extracting(DiscountEntity::getId)
                .contains(fresh.getId());
        assertThat(repository.findExpiredDiscounts(LocalDate.now()))
                .extracting(DiscountEntity::getId)
                .doesNotContain(fresh.getId());
    }
}
