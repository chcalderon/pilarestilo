-- Where the boleta finally leaves a trace.
--
-- Until now the shop issued every boleta by hand in the SII's eBoleta app and the system knew
-- nothing about it: no folio, no amounts, no file, no way to tell a sale that was declared from one
-- that was not. Searching the whole repo for iva, tax, boleta, factura, SII or DTE returned nothing
-- at all.
--
-- The table is an append-only history, the same shape reviews got in V71 and discount redemptions
-- in V67. A tax document is not editable: correcting one means voiding it and issuing another, and
-- both facts have to survive. Nothing here is ever UPDATEd except the void columns.
CREATE TABLE sales_documents (
  id                   UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  order_id             UUID NOT NULL REFERENCES orders(id),
  document_type        VARCHAR(20) NOT NULL,
  folio                VARCHAR(40) NOT NULL,
  issued_at            TIMESTAMP WITH TIME ZONE NOT NULL,

  net_amount           NUMERIC(15,2) NOT NULL,
  tax_amount           NUMERIC(15,2) NOT NULL,
  tax_rate             NUMERIC(5,2)  NOT NULL,
  total_amount         NUMERIC(15,2) NOT NULL,
  currency             VARCHAR(10)   NOT NULL,

  receiver_rut         VARCHAR(20),
  receiver_name        VARCHAR(160),
  receiver_email       VARCHAR(255),

  file_url             VARCHAR(500),

  status               VARCHAR(20) NOT NULL,
  voided_at            TIMESTAMP WITH TIME ZONE,
  void_reason          VARCHAR(500),
  voided_by            UUID REFERENCES users(id),
  replaces_document_id UUID REFERENCES sales_documents(id),

  issued_by            UUID NOT NULL REFERENCES users(id),
  created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

  CONSTRAINT chk_sales_documents_type   CHECK (document_type IN ('BOLETA', 'FACTURA')),
  CONSTRAINT chk_sales_documents_status CHECK (status IN ('ISSUED', 'VOIDED')),
  -- The IVA is derived by subtraction precisely so this always holds. If a row ever violates it,
  -- the amounts were computed somewhere other than TaxBreakdown.
  CONSTRAINT chk_sales_documents_amounts CHECK (net_amount + tax_amount = total_amount),
  -- A void without a reason is a void nobody can account for a year later.
  CONSTRAINT chk_sales_documents_void_reason
    CHECK (status <> 'VOIDED' OR (voided_at IS NOT NULL AND void_reason IS NOT NULL))
);

-- At most one live document per order. Voided rows pile up behind it, which is what makes
-- "anular y reemitir" possible without deleting the first attempt.
CREATE UNIQUE INDEX uq_sales_documents_live_per_order
  ON sales_documents (order_id)
  WHERE status <> 'VOIDED';

-- A folio identifies a document to the SII. Reusing one, even after voiding, would make two
-- different documents indistinguishable in the shop's own records.
CREATE UNIQUE INDEX uq_sales_documents_type_folio
  ON sales_documents (document_type, folio);

-- The pending queue reads "paid orders with no live document", and the sales screen reads by order.
CREATE INDEX idx_sales_documents_issued_at ON sales_documents (issued_at DESC);

COMMENT ON COLUMN sales_documents.receiver_rut IS
  'Optional: a boleta does not require the buyer RUT. Mandatory only once factura (DTE 33) is issued.';
COMMENT ON COLUMN sales_documents.receiver_name IS
  'Snapshot of the buyer at issue time. A tax document is kept for six years, so it has to stay readable after the user row is anonymised under Ley 21.719.';
COMMENT ON COLUMN sales_documents.receiver_email IS
  'Snapshot of the buyer email, so the document can be re-sent by hand without reading the user row.';
COMMENT ON COLUMN sales_documents.replaces_document_id IS
  'Set when this document was issued to replace a voided one. Chains a correction back to what it corrected.';
COMMENT ON COLUMN sales_documents.file_url IS
  'Internal path of the uploaded PDF or image. Served only through the authenticated endpoint, never under /api/media/**, which is permitAll.';

-- Nothing into orders cascades: every child is ON DELETE NO ACTION, which V74 learned the hard way
-- when Postgres refused its deletes and the backend restart-looped. This table is another such
-- child, so any future cleanup of orders has to delete from here first. That is deliberate: a tax
-- record should refuse to disappear quietly along with its order.
