-- The shop's own tax identity, and the two rules that depend on it.
--
-- A boleta names its issuer: RUT, razon social, giro, Acteco and address. None of that existed
-- anywhere -- the closest thing was bank_transfer_account_holder, defaulting to the literal string
-- 'Pilar Estilo'. Hardcoding it in Java would put a legal identity in a deploy instead of in the
-- CMS, which is exactly what the owner asked to avoid.
--
-- Same shape every other feature has used on this table since V13: one singleton row, ADD COLUMN
-- IF NOT EXISTS, then seed row 1.
ALTER TABLE system_settings ADD COLUMN IF NOT EXISTS tax_payer_rut                         VARCHAR(20);
ALTER TABLE system_settings ADD COLUMN IF NOT EXISTS tax_business_name                     VARCHAR(160);
ALTER TABLE system_settings ADD COLUMN IF NOT EXISTS tax_business_activity                 VARCHAR(160);
ALTER TABLE system_settings ADD COLUMN IF NOT EXISTS tax_acteco_code                       VARCHAR(20);
ALTER TABLE system_settings ADD COLUMN IF NOT EXISTS tax_address                           VARCHAR(200);
ALTER TABLE system_settings ADD COLUMN IF NOT EXISTS tax_commune                           VARCHAR(120);
ALTER TABLE system_settings ADD COLUMN IF NOT EXISTS tax_city                              VARCHAR(120);
ALTER TABLE system_settings ADD COLUMN IF NOT EXISTS tax_vat_rate                          NUMERIC(5,2);
ALTER TABLE system_settings ADD COLUMN IF NOT EXISTS tax_document_required_before_dispatch BOOLEAN;
ALTER TABLE system_settings ADD COLUMN IF NOT EXISTS tax_document_provider                 VARCHAR(30);

UPDATE system_settings
   SET tax_vat_rate                          = COALESCE(tax_vat_rate, 19.00),
       tax_document_required_before_dispatch = COALESCE(tax_document_required_before_dispatch, TRUE),
       tax_document_provider                 = COALESCE(tax_document_provider, 'MANUAL')
 WHERE id = 1;

ALTER TABLE system_settings ALTER COLUMN tax_vat_rate                          SET DEFAULT 19.00;
ALTER TABLE system_settings ALTER COLUMN tax_document_required_before_dispatch SET DEFAULT TRUE;
ALTER TABLE system_settings ALTER COLUMN tax_document_provider                 SET DEFAULT 'MANUAL';

COMMENT ON COLUMN system_settings.tax_vat_rate IS
  'VAT percentage applied to new orders. Snapshotted onto each order, so changing it never restates a past sale.';
COMMENT ON COLUMN system_settings.tax_document_required_before_dispatch IS
  'When true, a paid order cannot be claimed for dispatch until it has a live sales document. The boleta accompanies the goods, so the gate sits at the goods leaving, not at the money arriving.';
COMMENT ON COLUMN system_settings.tax_document_provider IS
  'MANUAL means the folio is typed in from the SII eBoleta app. TUU and OPENFACTURA are placeholders for API emission; no adapter reads this yet.';
