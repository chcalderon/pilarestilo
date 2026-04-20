CREATE TABLE IF NOT EXISTS wishlists (
    customer_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS wishlist_items (
    customer_id UUID NOT NULL REFERENCES wishlists(customer_id) ON DELETE CASCADE,
    product_id  UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    added_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (customer_id, product_id)
);
