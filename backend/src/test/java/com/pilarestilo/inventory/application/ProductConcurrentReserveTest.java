package com.pilarestilo.inventory.application;

import com.pilarestilo.inventory.domain.model.StockMovementOrigin;
import com.pilarestilo.inventory.domain.ports.InventoryMovementRepository;
import com.pilarestilo.product.domain.ports.ProductRepository;
import com.pilarestilo.shared.domain.DomainEventPublisher;
import com.pilarestilo.shared.domain.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit-level concurrency test for {@link InventoryService#reserve(UUID, int, String, String)}.
 *
 * <p>Simulates 10 concurrent threads racing to reserve a variant that has only 1 unit in stock.
 * The {@code atomicReserveVariantStock} method is mocked to return 1 exactly once (first call)
 * and 0 for every subsequent call, mirroring the database-level optimistic-lock behaviour.
 * The expectation is that exactly 1 thread succeeds and the remaining 9 receive a
 * {@link DomainException} with an "insufficient stock" message.</p>
 */
@ExtendWith(MockitoExtension.class)
class ProductConcurrentReserveTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private DomainEventPublisher eventPublisher;

    @Mock
    private InventoryMovementRepository inventoryMovementRepository;

    @Mock
    private RestClient.Builder restClientBuilder;

    @Mock
    private RestClient restClient;

    private InventoryService inventoryService;

    @BeforeEach
    void setUp() {
        when(restClientBuilder.baseUrl(anyString())).thenReturn(restClientBuilder);
        when(restClientBuilder.build()).thenReturn(restClient);
        lenient().when(inventoryMovementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        inventoryService = new InventoryService(
                productRepository,
                eventPublisher,
                inventoryMovementRepository,
                restClientBuilder,
                false,
                "http://localhost:8082"
        );
    }

    @Test
    void concurrentReserve_onlyFirstThreadSucceeds_othersGetDomainException() throws InterruptedException {
        UUID productId = UUID.randomUUID();
        int threads = 10;

        // Thread-safe counter: first caller gets updated=1, all others get updated=0
        AtomicInteger callCount = new AtomicInteger(0);
        when(productRepository.atomicReserveVariantStock(any(UUID.class), anyString(), anyString(), anyInt()))
                .thenAnswer(inv -> callCount.getAndIncrement() == 0 ? 1 : 0);

        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);
        List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger successes = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    startGate.await(); // all threads start at the same instant
                    inventoryService.reserve(productId, 1, "Rojo", "M", StockMovementOrigin.unknown());
                    successes.incrementAndGet();
                } catch (DomainException ex) {
                    exceptions.add(ex);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startGate.countDown(); // release all threads simultaneously
        boolean finished = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdownNow();

        assertThat(finished).as("All threads should finish within the timeout").isTrue();
        assertThat(successes.get()).as("Exactly one thread should succeed").isEqualTo(1);
        assertThat(exceptions).as("Remaining threads should throw DomainException").hasSize(threads - 1);
        assertThat(exceptions)
                .allSatisfy(ex -> assertThat(ex).isInstanceOf(DomainException.class));
    }

    @Test
    void reserve_withVariant_throwsImmediately_whenAtomicUpdateReturnsZero() {
        UUID productId = UUID.randomUUID();
        when(productRepository.atomicReserveVariantStock(any(UUID.class), anyString(), anyString(), anyInt()))
                .thenReturn(0);

        org.junit.jupiter.api.Assertions.assertThrows(
                DomainException.class,
                () -> inventoryService.reserve(productId, 5, "Azul", "XL", StockMovementOrigin.unknown())
        );
    }
}
