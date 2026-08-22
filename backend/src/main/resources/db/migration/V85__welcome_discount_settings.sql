-- The coupon a new account gets, if the shop chooses to run one. Off by default: the owner turns
-- it on from /admin/descuentos when ready. Vigencia is not a column here on purpose -- it is fixed
-- at 30 days in IssueWelcomeDiscountUseCase, not a value the owner asked to configure.

ALTER TABLE system_settings
  ADD COLUMN welcome_discount_enabled            BOOLEAN       NOT NULL DEFAULT FALSE,
  ADD COLUMN welcome_discount_type               VARCHAR(20)   NOT NULL DEFAULT 'PERCENTAGE',
  ADD COLUMN welcome_discount_value              NUMERIC(12,2) NOT NULL DEFAULT 10,
  ADD COLUMN welcome_discount_min_order_amount   NUMERIC(12,2) NOT NULL DEFAULT 0,
  ADD COLUMN welcome_discount_requires_marketing BOOLEAN       NOT NULL DEFAULT TRUE;
