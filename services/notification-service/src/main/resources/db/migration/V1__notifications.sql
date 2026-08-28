-- The notifications table, in the database this service owns.
--
-- Same shape as V31 in pilarestilo, minus the users foreign key (users lives in another database
-- Postgres cannot reach from here) and its ON DELETE CASCADE, and minus a DEFAULT on id (nothing
-- inserts here except JPA, and the entity generates its own UUID).

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
