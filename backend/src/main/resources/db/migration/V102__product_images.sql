-- Additional product images beyond products.image_url (the cover), in display order.
-- Populated by the admin product form; consumed by the Product JSON-LD, the Merchant feed,
-- and (later, H-4b) the social carousel. sort_order is the 0-based list index Hibernate
-- maintains from @OrderColumn.
CREATE TABLE product_images (
    product_id UUID NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    image_url  TEXT NOT NULL,
    sort_order INT  NOT NULL,
    PRIMARY KEY (product_id, sort_order)
);

CREATE INDEX idx_product_images_product ON product_images (product_id);
