package com.pilarestilo.inventoryservice.web;

import com.pilarestilo.inventoryservice.application.InventoryCommandService;
import com.pilarestilo.inventoryservice.application.InventoryQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.NoSuchElementException;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InventoryController.class)
class InventoryControllerCommandEndpointsTest {

    private static final UUID PRODUCT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InventoryQueryService queryService;

    @MockitoBean
    private InventoryCommandService commandService;

    @Test
    void reserveReturnsNoContent() throws Exception {
        mockMvc.perform(post("/api/inventory/commands/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload()))
                .andExpect(status().isNoContent());

        verify(commandService).reserve(PRODUCT_ID, 1, null, null);
    }

    @Test
    void releaseReturnsNoContent() throws Exception {
        mockMvc.perform(post("/api/inventory/commands/release")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload()))
                .andExpect(status().isNoContent());

        verify(commandService).release(PRODUCT_ID, 1, null, null);
    }

    @Test
    void confirmReturnsNoContent() throws Exception {
        mockMvc.perform(post("/api/inventory/commands/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload()))
                .andExpect(status().isNoContent());

        verify(commandService).confirm(PRODUCT_ID, 1, null, null);
    }

    @Test
    void reserveMapsDomainValidationToBadRequest() throws Exception {
        doThrow(new IllegalArgumentException("Quantity must be greater than zero"))
                .when(commandService).reserve(any(UUID.class), anyInt(), any(), any());

        mockMvc.perform(post("/api/inventory/commands/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reserveMapsMissingProductToNotFound() throws Exception {
        doThrow(new NoSuchElementException("Product not found"))
                .when(commandService).reserve(any(UUID.class), anyInt(), any(), any());

        mockMvc.perform(post("/api/inventory/commands/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload()))
                .andExpect(status().isNotFound());
    }

    private String payload() {
        return """
                {
                  "productId": "00000000-0000-0000-0000-000000000001",
                  "qty": 1
                }
                """;
    }
}
