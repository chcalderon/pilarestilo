package com.pilarestilo.inventory.application;

import com.pilarestilo.inventory.domain.ports.InventoryMovementRepository;
import com.pilarestilo.product.domain.ports.ProductRepository;
import com.pilarestilo.shared.domain.DomainEventPublisher;
import com.pilarestilo.shared.domain.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * The branch production actually runs, and the one that had no coverage at all.
 *
 * <p>With {@code APP_INVENTORY_REMOTE_ENABLED=true} every stock command is an HTTP call and the
 * monolith's own tables are never touched. A no-op on the far side of that call is what let paid
 * orders keep their units on the shelf, and nothing here would have caught it — but these do catch
 * the half this codebase owns: that the call is made, to the right path, with the variant, and that
 * a refusal becomes a DomainException rather than a silent success.
 */
class InventoryServiceRemoteDelegationTest {

    private static final String BASE_URL = "http://inventory-service:8082";

    private ProductRepository productRepository;
    private InventoryMovementRepository movementRepository;
    private MockRestServiceServer server;
    private InventoryService service;

    private final UUID productId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        movementRepository = mock(InventoryMovementRepository.class);

        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();

        service = new InventoryService(
                productRepository,
                mock(DomainEventPublisher.class),
                movementRepository,
                builder,
                true,
                BASE_URL
        );
    }

    @Test
    @DisplayName("reserving a variant is delegated, colour and size included")
    void reserveGoesToTheService() {
        expect("/api/inventory/commands/reserve").andRespond(withSuccess());

        service.reserve(productId, 2, "Negro", "M");

        server.verify();
        // The monolith must not also write: two writers of one table is how the stock bugs started.
        verifyNoInteractions(productRepository);
        verifyNoInteractions(movementRepository);
    }

    @Test
    @DisplayName("releasing a variant is delegated")
    void releaseGoesToTheService() {
        expect("/api/inventory/commands/release").andRespond(withSuccess());

        service.release(productId, 1, "Negro", "M");

        server.verify();
        verifyNoInteractions(productRepository);
    }

    @Test
    @DisplayName("confirming a sale is delegated — the call that used to do nothing on the far side")
    void confirmGoesToTheService() {
        expect("/api/inventory/commands/confirm").andRespond(withSuccess());

        service.confirm(productId, 1, "Negro", "M");

        server.verify();
        verifyNoInteractions(productRepository);
    }

    @Test
    @DisplayName("a product the service does not know becomes a not-found, not a generic failure")
    void translatesNotFound() {
        expect("/api/inventory/commands/reserve").andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> service.reserve(productId, 1, "Negro", "M"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Product not found");
    }

    @Test
    @DisplayName("a refusal is an error here, never a quiet success that leaves stock unreserved")
    void refusalBecomesAnError() {
        expect("/api/inventory/commands/reserve").andRespond(withStatus(HttpStatus.CONFLICT));

        assertThatThrownBy(() -> service.reserve(productId, 1, "Negro", "M"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("rejected reserve");
    }

    @Test
    @DisplayName("an unreachable service is an error too — a network failure is not availability")
    void unreachableServiceBecomesAnError() {
        expect("/api/inventory/commands/reserve")
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> service.reserve(productId, 1, "Negro", "M"))
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("a product with no variants still delegates, with no colour or size")
    void nonVariantProductDelegatesWithoutASelector() {
        server.expect(requestTo(BASE_URL + "/api/inventory/commands/reserve"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.variantColor").doesNotExist())
                .andExpect(jsonPath("$.variantSize").doesNotExist())
                .andRespond(withSuccess());

        service.reserve(productId, 3);

        server.verify();
    }

    private org.springframework.test.web.client.ResponseActions expect(String path) {
        return server.expect(requestTo(BASE_URL + path))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.productId").value(productId.toString()))
                .andExpect(jsonPath("$.variantColor").value("Negro"))
                .andExpect(jsonPath("$.variantSize").value("M"));
    }
}
