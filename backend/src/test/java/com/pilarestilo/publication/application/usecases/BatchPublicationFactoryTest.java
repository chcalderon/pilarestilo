package com.pilarestilo.publication.application.usecases;

import com.pilarestilo.product.domain.enums.ProductCondition;
import com.pilarestilo.product.domain.model.Product;
import com.pilarestilo.product.domain.model.ProductVariant;
import com.pilarestilo.publication.application.commands.CreatePublicationCommand;
import com.pilarestilo.publication.application.commands.PublishProductsBatchCommand;
import com.pilarestilo.publication.domain.enums.PublicationPlatform;
import com.pilarestilo.shared.application.Money;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchPublicationFactoryTest {

    private final BatchPublicationFactory factory =
            new BatchPublicationFactory(new ObjectMapper(), "https://pilarestilo.com");

    @Test
    void interpolates_all_five_variant_tokens() {
        Product product = Product.create("Zapatos", "d", new Money(BigDecimal.valueOf(29990), "CLP"),
                "https://img", ProductCondition.NEW, "Pilar", 5);
        product.setVariants(List.of(new ProductVariant("Negro", "40", 5, 1)));

        String caption = factory.interpolate("{producto} {color} {talla} quedan {cantidad} a {precio}",
                product.getId(), product, new PublishProductsBatchCommand.VariantSelection("Negro", "40"));

        assertEquals("Zapatos Negro 40 quedan 4 a $29.990", caption);
    }

    @Test
    void interpolates_the_product_url_from_the_configured_site_base() {
        Product product = Product.create("Zapatos", "d", new Money(BigDecimal.valueOf(1000), "CLP"),
                "https://img", ProductCondition.NEW, "Pilar", 5);

        String caption = factory.interpolate("Mira {producto} en {product_url}", product.getId(), product, null);

        assertEquals("Mira Zapatos en https://pilarestilo.com/es/products/" + product.getId(), caption);
    }

    @Test
    void leaves_the_product_url_empty_when_no_site_base_is_configured() {
        BatchPublicationFactory noBase = new BatchPublicationFactory(new ObjectMapper(), "");
        Product product = Product.create("Zapatos", "d", new Money(BigDecimal.valueOf(1000), "CLP"),
                "https://img", ProductCondition.NEW, "Pilar", 5);

        String caption = noBase.interpolate("Mira {product_url}", product.getId(), product, null);

        assertEquals("Mira ", caption);
    }

    @Test
    void build_create_command_threads_scheduled_at_and_batch_id() {
        Product product = Product.create("Zapatos", "d", new Money(BigDecimal.valueOf(1000), "CLP"),
                "https://img/z.jpg", ProductCondition.NEW, "Pilar", 5);
        UUID batchId = UUID.randomUUID();
        Instant when = Instant.now().plusSeconds(7200);
        PublishProductsBatchCommand cmd = new PublishProductsBatchCommand(
                List.of(product.getId()), Set.of(PublicationPlatform.INSTAGRAM),
                "{producto}", List.of("#x"), "Camp", Map.of(), Map.of(), when);

        CreatePublicationCommand out = factory.buildCreateCommand(
                cmd, product.getId(), product, PublicationPlatform.INSTAGRAM, "Zapatos", batchId);

        assertEquals(when, out.scheduledAt());
        assertEquals(batchId, out.batchId());
        assertEquals("Zapatos", out.caption());
        assertEquals("https://img/z.jpg", out.mediaBundles().get(0).primaryAssetUrl());
        assertTrue(out.idempotencyKey().startsWith("pub-batch-"));
    }

    @Test
    void build_create_command_stores_the_image_selection_list_in_the_manifest() {
        Product product = Product.create("Zapatos", "d", new Money(BigDecimal.valueOf(1000), "CLP"),
                "https://img/cover.jpg", ProductCondition.NEW, "Pilar", 5);
        UUID batchId = UUID.randomUUID();
        PublishProductsBatchCommand cmd = new PublishProductsBatchCommand(
                List.of(product.getId()), Set.of(PublicationPlatform.INSTAGRAM),
                "{producto}", List.of("#x"), "Camp",
                Map.of(product.getId(), List.of("https://img/cover.jpg", "https://img/2.jpg", "https://img/3.jpg")),
                Map.of(), null);

        CreatePublicationCommand out = factory.buildCreateCommand(
                cmd, product.getId(), product, PublicationPlatform.INSTAGRAM, "Zapatos", batchId);

        var bundle = out.mediaBundles().get(0);
        assertEquals("https://img/cover.jpg", bundle.primaryAssetUrl());
        assertEquals(
                List.of("https://img/cover.jpg", "https://img/2.jpg", "https://img/3.jpg"),
                bundle.assetManifest().get("imageUrls"));
    }

    @Test
    void build_create_command_falls_back_to_the_product_cover_when_no_selection() {
        Product product = Product.create("Zapatos", "d", new Money(BigDecimal.valueOf(1000), "CLP"),
                "https://img/cover.jpg", ProductCondition.NEW, "Pilar", 5);
        PublishProductsBatchCommand cmd = new PublishProductsBatchCommand(
                List.of(product.getId()), Set.of(PublicationPlatform.INSTAGRAM),
                "{producto}", List.of("#x"), "Camp", Map.of(), Map.of(), null);

        CreatePublicationCommand out = factory.buildCreateCommand(
                cmd, product.getId(), product, PublicationPlatform.INSTAGRAM, "Zapatos", UUID.randomUUID());

        assertEquals("https://img/cover.jpg", out.mediaBundles().get(0).primaryAssetUrl());
        assertEquals(List.of("https://img/cover.jpg"), out.mediaBundles().get(0).assetManifest().get("imageUrls"));
    }
}
