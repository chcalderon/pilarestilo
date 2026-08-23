-- A factura (DTE 33) names its receiver's razon social and giro, not just their RUT. Nothing
-- captured either: the columns did not exist and no screen asked for them, so FACTURA could not
-- actually be issued even though the domain already required the RUT for it.
--
-- Nullable in the database, exactly like receiver_rut: a boleta never fills them, and the
-- requirement that a factura must is enforced in SalesDocument.issue, not by a CHECK here.
ALTER TABLE sales_documents ADD COLUMN IF NOT EXISTS receiver_business_name     VARCHAR(160);
ALTER TABLE sales_documents ADD COLUMN IF NOT EXISTS receiver_business_activity VARCHAR(160);

COMMENT ON COLUMN sales_documents.receiver_business_name IS
  'Razon social of the receiver. Mandatory only once factura (DTE 33) is issued; a boleta leaves it null.';
COMMENT ON COLUMN sales_documents.receiver_business_activity IS
  'Giro of the receiver. Mandatory only once factura (DTE 33) is issued; a boleta leaves it null.';
