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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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

        verify(commandService).reserve(eq(PRODUCT_ID), eq(1), isNull(), isNull(), any());
    }

    @Test
    void releaseReturnsNoContent() throws Exception {
        mockMvc.perform(post("/api/inventory/commands/release")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload()))
                .andExpect(status().isNoContent());

        verify(commandService).release(eq(PRODUCT_ID), eq(1), isNull(), isNull(), any());
    }

    @Test
    void confirmReturnsNoContent() throws Exception {
        mockMvc.perform(post("/api/inventory/commands/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload()))
                .andExpect(status().isNoContent());

        verify(commandService).confirm(eq(PRODUCT_ID), eq(1), isNull(), isNull(), any());
    }

    @Test
    void reserveMapsDomainValidationToBadRequest() throws Exception {
        doThrow(new IllegalArgumentException("Quantity must be greater than zero"))
                .when(commandService).reserve(any(UUID.class), anyInt(), any(), any(), any());

        mockMvc.perform(post("/api/inventory/commands/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reserveMapsMissingProductToNotFound() throws Exception {
        doThrow(new NoSuchElementException("Product not found"))
                .when(commandService).reserve(any(UUID.class), anyInt(), any(), any(), any());

        mockMvc.perform(post("/api/inventory/commands/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload()))
                .andExpect(status().isNotFound());
    }

    /** The cause travels with the command: this service is what writes the ledger line. */
    @Test
    void reserveForwardsTheCauseItWasGiven() throws Exception {
        mockMvc.perform(post("/api/inventory/commands/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": "00000000-0000-0000-0000-000000000001",
                                  "qty": 1,
                                  "referenceType": "ORDER",
                                  "referenceId": "00000000-0000-0000-0000-0000000000aa",
                                  "recordedBy": "00000000-0000-0000-0000-0000000000bb"
                                }
                                """))
                .andExpect(status().isNoContent());

        verify(commandService).reserve(eq(PRODUCT_ID), eq(1), isNull(), isNull(),
                argThat(origin -> "ORDER".equals(origin.referenceType())
                        && "00000000-0000-0000-0000-0000000000aa".equals(String.valueOf(origin.referenceId()))
                        && "00000000-0000-0000-0000-0000000000bb".equals(String.valueOf(origin.recordedBy()))));
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
