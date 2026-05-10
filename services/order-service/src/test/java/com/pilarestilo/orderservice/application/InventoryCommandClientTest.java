package com.pilarestilo.orderservice.application;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class InventoryCommandClientTest {

    @Test
    void reserve_sends_request_successfully() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        InventoryCommandClient client = new InventoryCommandClient(builder, "http://inventory-service:8082");

        server.expect(requestTo("http://inventory-service:8082/api/inventory/commands/reserve"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        client.reserve(UUID.randomUUID(), 2, "Negro", "M");
        server.verify();
    }

    @Test
    void reserve_maps_not_found_error() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        InventoryCommandClient client = new InventoryCommandClient(builder, "http://inventory-service:8082");
        UUID productId = UUID.randomUUID();

        server.expect(requestTo("http://inventory-service:8082/api/inventory/commands/reserve"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(org.springframework.http.HttpStatus.NOT_FOUND));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                client.reserve(productId, 1, null, null));

        assertEquals("Product not found: " + productId, ex.getMessage());
    }
}
