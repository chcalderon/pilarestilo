-- Undoing a sale after it was delivered.
--
-- Cancelling only ever worked before delivery: Order.cancel() refuses a DELIVERED order, and it is
-- right to. Once the goods are with the customer, undoing the sale is a return, and a return is a
-- different thing in four planes at once -- tax, money, inventory, and who starts it.
--
-- Two doors, one machine: the customer who changes her mind within the Ley 19.496 art. 3 bis window,
-- and the shop taking a garment back.

CREATE TABLE return_requests (
  id                       UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  order_id                 UUID NOT NULL REFERENCES orders(id),
  kind                     VARCHAR(20) NOT NULL,
  status                   VARCHAR(20) NOT NULL,
  reason                   VARCHAR(500),

  -- Null when the shop opens it; a customer-initiated retracto always carries who asked.
  requested_by             UUID REFERENCES users(id),
  requested_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
  -- requested_at + 45 days. The law gives the shop that long to return the money, "a la mayor
  -- brevedad posible"; storing it makes the deadline something the screen can show rather than
  -- something somebody has to remember.
  deadline_at              TIMESTAMP WITH TIME ZONE NOT NULL,
  resolved_at              TIMESTAMP WITH TIME ZONE,
  resolution_note          VARCHAR(500),

  -- Every returned garment is cleaned, pressed, sanitised and repaired before it can be sold again,
  -- so receiving it is not the same as having it back on the shelf. A boolean cannot say "not known
  -- yet", and that is where the garment spends most of its time.
  item_disposition         VARCHAR(30),
  disposition_at           TIMESTAMP WITH TIME ZONE,
  disposition_note         VARCHAR(500),

  refund_amount            NUMERIC(15,2),
  refund_currency          VARCHAR(10),
  refund_method            VARCHAR(30),
  refund_reference         VARCHAR(200),
  refund_file_url          VARCHAR(500),
  refunded_at              TIMESTAMP WITH TIME ZONE,

  -- Bank details for a transfer refund. Asked for when the return is opened, never at checkout: the
  -- great majority of purchases are never returned, and the Ley 21.719 asks for no more data than
  -- the purpose needs. The account number is encrypted and erased once the refund settles; the last
  -- four digits and the operation reference are what survive, because those are what identify the
  -- payment afterwards.
  refund_account_holder    VARCHAR(160),
  refund_account_rut       VARCHAR(20),
  refund_bank_name         VARCHAR(120),
  refund_account_type      VARCHAR(80),
  refund_account_encrypted TEXT,
  refund_account_last4     VARCHAR(4),

  credit_note_id           UUID REFERENCES sales_documents(id),
  created_at               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

  CONSTRAINT chk_return_kind   CHECK (kind IN ('RETRACTO', 'DEVOLUCION')),
  CONSTRAINT chk_return_status CHECK (status IN ('REQUESTED', 'APPROVED', 'RECEIVED', 'REFUNDED', 'REJECTED')),
  CONSTRAINT chk_return_disposition
    CHECK (item_disposition IS NULL
           OR item_disposition IN ('PENDING_RECONDITIONING', 'RESTOCKED', 'DISCARDED')),
  -- A rejection nobody can account for a year later is the reason the note is required, and the same
  -- for discarding a garment instead of returning it to stock.
  CONSTRAINT chk_return_rejection_note
    CHECK (status <> 'REJECTED' OR resolution_note IS NOT NULL),
  CONSTRAINT chk_return_discard_note
    CHECK (item_disposition IS DISTINCT FROM 'DISCARDED' OR disposition_note IS NOT NULL)
);

-- One open request per order. Closed ones pile up behind it, so a second return on the same order
-- is possible once the first is settled.
CREATE UNIQUE INDEX uq_return_requests_open_per_order
  ON return_requests (order_id)
  WHERE status NOT IN ('REFUNDED', 'REJECTED');

-- The queue is read by deadline: what has to be paid soonest comes first.
CREATE INDEX idx_return_requests_open_by_deadline
  ON return_requests (deadline_at)
  WHERE status NOT IN ('REFUNDED', 'REJECTED');

CREATE INDEX idx_return_requests_order ON return_requests (order_id);

COMMENT ON COLUMN return_requests.deadline_at IS
  'requested_at + 45 days: the legal limit for returning the money. Independent of the garment: a delay in reconditioning must never push a legal deadline.';
COMMENT ON COLUMN return_requests.refund_account_encrypted IS
  'AES/GCM, same scheme as the shop secrets in system_settings. Erased when the refund settles.';

-- A credit note is a document type, not a status.
--
-- Voiding a boleta is a status flip here, and that is enough only while the document has not reached
-- the SII. A boleta for a delivered sale has been declared: leaving it without effect requires a
-- nota de credito electronica (DTE 61) referencing it, with its own folio. The credit note does not
-- erase the boleta, it counterweighs it, and both stay valid.
ALTER TABLE sales_documents DROP CONSTRAINT chk_sales_documents_type;
ALTER TABLE sales_documents
  ADD CONSTRAINT chk_sales_documents_type
  CHECK (document_type IN ('BOLETA', 'FACTURA', 'NOTA_CREDITO'));

-- The SII asks a credit note to say what it does to the document it references: 1 annuls, 2 corrects
-- text, 3 corrects an amount.
ALTER TABLE sales_documents ADD COLUMN IF NOT EXISTS reference_code SMALLINT;
ALTER TABLE sales_documents
  ADD CONSTRAINT chk_sales_documents_reference_code
  CHECK (reference_code IS NULL OR reference_code IN (1, 2, 3));
ALTER TABLE sales_documents
  ADD CONSTRAINT chk_sales_documents_credit_note_references
  CHECK (document_type <> 'NOTA_CREDITO'
         OR (replaces_document_id IS NOT NULL AND reference_code IS NOT NULL));

COMMENT ON COLUMN sales_documents.reference_code IS
  'SII reference code on a NOTA_CREDITO: 1 annuls, 2 corrects text, 3 corrects an amount. Null on a BOLETA or FACTURA.';
COMMENT ON COLUMN sales_documents.replaces_document_id IS
  'The document this one acts upon: replaces it when a reissue, counterweighs it when a credit note.';

-- The live-document index has to let a boleta and its credit note coexist.
--
-- uq_sales_documents_live_per_order allowed one non-voided row per order, which was right while
-- every document was a sale document. A credit note is a second live row for the same order by
-- design, so the rule narrows to what it always meant: one live *sale* document per order.
DROP INDEX uq_sales_documents_live_per_order;
CREATE UNIQUE INDEX uq_sales_documents_live_sale_per_order
  ON sales_documents (order_id)
  WHERE status <> 'VOIDED' AND document_type <> 'NOTA_CREDITO';
