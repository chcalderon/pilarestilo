package com.pilarestilo.orderservice.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class PersistenceEntitiesTest {

    @Test
    void payment_entity_getters_and_setters() {
        PaymentEntity entity = new PaymentEntity();
        UUID id = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID reviewedBy = UUID.randomUUID();
        Instant now = Instant.now();

        entity.setId(id);
        entity.setOrderId(orderId);
        entity.setMethod("TRANSFER");
        entity.setStatus("PENDING");
        entity.setProofReference("proof-1");
        entity.setReviewedBy(reviewedBy);
        entity.setReviewedAt(now);
        entity.setCreatedAt(now);
        entity.setTransferAccountHolderName("Pilar Estilo");
        entity.setTransferAccountEmail("admin@pilarestilo.com");
        entity.setTransferAccountNumber("123");
        entity.setTransferBankName("Banco");
        entity.setTransferAccountType("Corriente");

        assertEquals(id, entity.getId());
        assertEquals(orderId, entity.getOrderId());
        assertEquals("TRANSFER", entity.getMethod());
        assertEquals("PENDING", entity.getStatus());
        assertEquals("proof-1", entity.getProofReference());
        assertEquals(reviewedBy, entity.getReviewedBy());
        assertEquals(now, entity.getReviewedAt());
        assertEquals(now, entity.getCreatedAt());
        assertEquals("Pilar Estilo", entity.getTransferAccountHolderName());
        assertEquals("admin@pilarestilo.com", entity.getTransferAccountEmail());
        assertEquals("123", entity.getTransferAccountNumber());
        assertEquals("Banco", entity.getTransferBankName());
        assertEquals("Corriente", entity.getTransferAccountType());
    }

    @Test
    void order_item_entity_getters_and_setters() {
        OrderItemEntity item = new OrderItemEntity();
        OrderEntity order = new OrderEntity();
        UUID id = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        item.setId(id);
        item.setOrder(order);
        item.setProductId(productId);
        item.setProductName("Vestido");
        item.setUnitPriceAmount(new BigDecimal("100.00"));
        item.setUnitPriceCurrency("CLP");
        item.setQuantity(3);

        assertEquals(id, item.getId());
        assertSame(order, item.getOrder());
        assertEquals(productId, item.getProductId());
        assertEquals("Vestido", item.getProductName());
        assertEquals(new BigDecimal("100.00"), item.getUnitPriceAmount());
        assertEquals("CLP", item.getUnitPriceCurrency());
        assertEquals(3, item.getQuantity());
    }

    @Test
    void customer_address_getters_read_reflection_backed_values() {
        CustomerAddressEntity entity = new CustomerAddressEntity();
        UUID id = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        ReflectionTestUtils.setField(entity, "id", id);
        ReflectionTestUtils.setField(entity, "customerId", customerId);
        ReflectionTestUtils.setField(entity, "label", "Casa");
        ReflectionTestUtils.setField(entity, "recipientName", "Pilar");
        ReflectionTestUtils.setField(entity, "phone", "+56912345678");
        ReflectionTestUtils.setField(entity, "line1", "Av Apoquindo 123");
        ReflectionTestUtils.setField(entity, "line2", "Depto 22");
        ReflectionTestUtils.setField(entity, "comuna", "Las Condes");
        ReflectionTestUtils.setField(entity, "city", "Santiago");
        ReflectionTestUtils.setField(entity, "region", "RM");
        ReflectionTestUtils.setField(entity, "reference", "Porteria");

        assertEquals(id, entity.getId());
        assertEquals(customerId, entity.getCustomerId());
        assertEquals("Casa", entity.getLabel());
        assertEquals("Pilar", entity.getRecipientName());
        assertEquals("+56912345678", entity.getPhone());
        assertEquals("Av Apoquindo 123", entity.getLine1());
        assertEquals("Depto 22", entity.getLine2());
        assertEquals("Las Condes", entity.getComuna());
        assertEquals("Santiago", entity.getCity());
        assertEquals("RM", entity.getRegion());
        assertEquals("Porteria", entity.getReference());
    }

    @Test
    void system_settings_getters_read_reflection_backed_values() {
        SystemSettingsEntity entity = new SystemSettingsEntity();

        ReflectionTestUtils.setField(entity, "id", (short) 1);
        ReflectionTestUtils.setField(entity, "bankTransferAccountHolder", "Pilar Estilo");
        ReflectionTestUtils.setField(entity, "bankTransferContactEmail", "admin@pilarestilo.com");
        ReflectionTestUtils.setField(entity, "bankTransferAccountNumber", "000000");
        ReflectionTestUtils.setField(entity, "bankTransferBankName", "Banco de Chile");
        ReflectionTestUtils.setField(entity, "bankTransferAccountType", "Corriente");
        ReflectionTestUtils.setField(entity, "shippingZonesJson", "[]");
        ReflectionTestUtils.setField(entity, "shippingCouriersJson", "[]");
        ReflectionTestUtils.setField(entity, "shippingPaymentMode", "POR_PAGAR");

        assertEquals((short) 1, entity.getId());
        assertEquals("Pilar Estilo", entity.getBankTransferAccountHolder());
        assertEquals("admin@pilarestilo.com", entity.getBankTransferContactEmail());
        assertEquals("000000", entity.getBankTransferAccountNumber());
        assertEquals("Banco de Chile", entity.getBankTransferBankName());
        assertEquals("Corriente", entity.getBankTransferAccountType());
        assertEquals("[]", entity.getShippingZonesJson());
        assertEquals("[]", entity.getShippingCouriersJson());
        assertEquals("POR_PAGAR", entity.getShippingPaymentMode());
    }

    @Test
    void product_getters_read_reflection_backed_values() {
        ProductEntity entity = new ProductEntity();
        UUID id = UUID.randomUUID();

        ReflectionTestUtils.setField(entity, "id", id);
        ReflectionTestUtils.setField(entity, "name", "Panuelo");
        ReflectionTestUtils.setField(entity, "priceAmount", new BigDecimal("79000.00"));
        ReflectionTestUtils.setField(entity, "priceCurrency", "CLP");
        ReflectionTestUtils.setField(entity, "stock", 5);

        assertEquals(id, entity.getId());
        assertEquals("Panuelo", entity.getName());
        assertEquals(new BigDecimal("79000.00"), entity.getPriceAmount());
        assertEquals("CLP", entity.getPriceCurrency());
        assertEquals(5, entity.getStock());
    }
}
