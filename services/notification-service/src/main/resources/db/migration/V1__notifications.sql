-- The notifications table, in a database of its own.
--
-- Same shape as the one V31 created in pilarestilo, minus two things.
--
-- The foreign key is gone. V31 declared user_id as REFERENCES users(id) ON DELETE CASCADE, and
-- users now lives in a database Postgres cannot reach from here -- a cross-database foreign key
-- does not exist. Referential integrity for this column is no longer enforced by the engine, which
-- is part of what separating the data costs.
--
-- What went with it is the ON DELETE CASCADE, and it turns out to have been reaching almost
-- nothing. orders references users with NO ACTION, so a customer who has ever bought cannot be
-- hard-deleted at all -- Postgres refuses and DeleteUserUseCase reports it as related records. And
-- every notification about a purchase needs an order. The erasure path in the Ley 21.719
-- anonymises rather than deletes, so it never used this cascade either.
--
-- What is left is narrow: a customer who was assigned a discount code, never ordered, and is then
-- deleted by an admin. discounts.assigned_user_id is SET NULL and discount_code_usages cascades,
-- so nothing blocks that delete, and one DISCOUNT_CODE_ASSIGNED row is left behind holding a code
-- and a user id that no longer resolves. No name, no email, no amount. Nothing sweeps it, and no
-- screen reads it: notifications are addressed by user_id.
--
-- And the id has no DEFAULT. V31 used uuid_generate_v4(), which needs the uuid-ossp extension;
-- nothing inserts here except JPA, and the entity generates its own UUID.

CREATE TABLE notifications (
    id         UUID PRIMARY KEY,
    user_id    UUID NOT NULL,
    type       VARCHAR(50)  NOT NULL,
    title      VARCHAR(200) NOT NULL,
    body       TEXT         NOT NULL,
    metadata   JSONB,
    read_at    TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notifications_user_id ON notifications(user_id);
CREATE INDEX idx_notifications_user_unread ON notifications(user_id) WHERE read_at IS NULL;
