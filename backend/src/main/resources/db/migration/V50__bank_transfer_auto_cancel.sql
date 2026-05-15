ALTER TABLE payments ADD COLUMN rejection_reason VARCHAR(500);

ALTER TABLE system_settings
    ADD COLUMN bank_transfer_auto_cancel_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN bank_transfer_auto_cancel_timeout_minutes INTEGER NOT NULL DEFAULT 30,
    ADD COLUMN bank_transfer_auto_cancel_cron VARCHAR(64) NOT NULL DEFAULT '0 */15 * * * *';

CREATE INDEX idx_payments_pending_bank_transfer
    ON payments (method, status, created_at)
    WHERE status = 'PENDING' AND method = 'BANK_TRANSFER';
