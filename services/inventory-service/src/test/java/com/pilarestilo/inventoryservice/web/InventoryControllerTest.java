package com.pilarestilo.inventoryservice.web;

import com.pilarestilo.inventoryservice.application.InventoryCommandService;
import com.pilarestilo.inventoryservice.application.InventoryQueryService;
import com.pilarestilo.inventoryservice.persistence.ProductEntity;
import com.pilarestilo.inventoryservice.web.dto.InventoryCommandRequest;
import com.pilarestilo.inventoryservice.web.dto.InventoryProductDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryControllerTest {

    @Mock
    private InventoryQueryService queryService;
    @Mock
    private InventoryCommandService commandService;

    @Test
    void list_and_get_by_id_map_results() {
        InventoryController controller = new InventoryController(queryService, commandService);
        ProductEntity entity = product(UUID.randomUUID());
        when(queryService.list(any(), any(), any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of(entity)));
        when(queryService.getById(entity.getId())).thenReturn(entity);

        Page<InventoryProductDto> listed = controller.list(null, null, null, null, "", 2, PageRequest.of(0, 10));
        InventoryProductDto byId = controller.getById(entity.getId(), 2);

        assertEquals(1, listed.getTotalElements());
        assertEquals(entity.getId(), listed.getContent().get(0).id());
        assertEquals(entity.getId(), byId.id());
    }

    @Test
    void get_by_id_maps_not_found_to_404() {
        InventoryController controller = new InventoryController(queryService, commandService);
        UUID id = UUID.randomUUID();
        when(queryService.getById(id)).thenThrow(new NoSuchElementException("Product not found: " + id));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> controller.getById(id, 2));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void reserve_release_confirm_map_domain_errors_to_bad_request() {
        InventoryController controller = new InventoryController(queryService, commandService);
        UUID productId = UUID.randomUUID();
        InventoryCommandRequest req = new InventoryCommandRequest(productId, 1, null, null, null, null, null);

        doThrow(new IllegalStateException("boom")).when(commandService).reserve(eq(productId), eq(1), eq(null), eq(null), any());
        ResponseStatusException reserveEx = assertThrows(ResponseStatusException.class, () -> controller.reserve(req));
        assertEquals(HttpStatus.BAD_REQUEST, reserveEx.getStatusCode());

        doThrow(new IllegalArgumentException("bad")).when(commandService).release(eq(productId), eq(1), eq(null), eq(null), any());
        ResponseStatusException releaseEx = assertThrows(ResponseStatusException.class, () -> controller.release(req));
        assertEquals(HttpStatus.BAD_REQUEST, releaseEx.getStatusCode());

        doThrow(new NoSuchElementException("not found")).when(commandService).confirm(eq(productId), eq(1), eq(null), eq(null), any());
        ResponseStatusException confirmEx = assertThrows(ResponseStatusException.class, () -> controller.confirm(req));
        assertEquals(HttpStatus.NOT_FOUND, confirmEx.getStatusCode());

        verify(commandService).reserve(eq(productId), eq(1), isNull(), isNull(), any());
        verify(commandService).release(eq(productId), eq(1), isNull(), isNull(), any());
        verify(commandService).confirm(eq(productId), eq(1), isNull(), isNull(), any());
    }

    private ProductEntity product(UUID id) {
        ProductEntity entity = new ProductEntity();
        ReflectionTestUtils.setField(entity, "id", id);
        ReflectionTestUtils.setField(entity, "name", "Vestido");
        ReflectionTestUtils.setField(entity, "brand", "Prada");
        ReflectionTestUtils.setField(entity, "condition", "NUEVO");
        ReflectionTestUtils.setField(entity, "stock", 2);
        ReflectionTestUtils.setField(entity, "active", true);
        ReflectionTestUtils.setField(entity, "updatedAt", Instant.now());
        return entity;
    }
}
