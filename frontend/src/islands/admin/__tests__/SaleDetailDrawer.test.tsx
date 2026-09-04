import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';
import SaleDetailDrawer from '../SaleDetailDrawer';
import type { OrderDto, PaymentDto, SaleSummaryDto, SalesDocumentDto } from '../../../lib/api';

/**
 * Characterization tests written before reducing this component's Cognitive Complexity (S3776)
 * -- it had none. This drawer is the only place a boleta/factura gets issued, voided or reissued,
 * so the refactor (splitting the JSX into sibling components) is verified against the actual
 * issue/void/reissue payloads and validation messages, not just "it renders".
 */

const getOrderById = vi.fn();
const getPaymentByOrder = vi.fn();
const getSalesDocumentsByOrder = vi.fn();
const getNextFolio = vi.fn();
const issueSalesDocument = vi.fn();
const voidSalesDocument = vi.fn();
const reissueSalesDocument = vi.fn();
const cancelSale = vi.fn();
const uploadSalesDocumentFile = vi.fn();
const attachSalesDocumentFile = vi.fn();
const fetchSalesDocumentFile = vi.fn();
const fetchPaymentProof = vi.fn();

vi.mock('../../../lib/api', async () => {
  const actual = await vi.importActual<typeof import('../../../lib/api')>('../../../lib/api');
  return {
    ...actual,
    getOrderById: (...args: unknown[]) => getOrderById(...args),
    getPaymentByOrder: (...args: unknown[]) => getPaymentByOrder(...args),
    getSalesDocumentsByOrder: (...args: unknown[]) => getSalesDocumentsByOrder(...args),
    getNextFolio: (...args: unknown[]) => getNextFolio(...args),
    issueSalesDocument: (...args: unknown[]) => issueSalesDocument(...args),
    voidSalesDocument: (...args: unknown[]) => voidSalesDocument(...args),
    reissueSalesDocument: (...args: unknown[]) => reissueSalesDocument(...args),
    cancelSale: (...args: unknown[]) => cancelSale(...args),
    uploadSalesDocumentFile: (...args: unknown[]) => uploadSalesDocumentFile(...args),
    attachSalesDocumentFile: (...args: unknown[]) => attachSalesDocumentFile(...args),
    fetchSalesDocumentFile: (...args: unknown[]) => fetchSalesDocumentFile(...args),
    fetchPaymentProof: (...args: unknown[]) => fetchPaymentProof(...args),
  };
});

const TOKEN = 'test-token';

function sale(overrides: Partial<SaleSummaryDto> = {}): SaleSummaryDto {
  return {
    orderId: 'order-1',
    publicReference: 'PE-0000000001',
    createdAt: '2026-08-01T12:00:00Z',
    orderStatus: 'PAID',
    customerName: 'Ana Perez',
    customerEmail: 'ana@correo.cl',
    totalAmount: 10000,
    netAmount: 8403,
    taxAmount: 1597,
    currency: 'CLP',
    paymentMethod: 'TRANSFER',
    paymentStatus: 'APPROVED',
    paymentGatewayFlag: null,
    documentId: null,
    documentFolio: null,
    itemCount: 1,
    firstItemName: 'Blazer',
    ...overrides,
  };
}

function order(overrides: Partial<OrderDto> = {}): OrderDto {
  return {
    id: 'order-1',
    publicReference: 'PE-0000000001',
    customerId: 'cust-1',
    items: [
      { id: 'item-1', productId: 'p1', productName: 'Blazer', unitPrice: { amount: 10000, currency: 'CLP' }, quantity: 1 },
    ],
    subtotal: { amount: 10000, currency: 'CLP' },
    discountAmount: { amount: 0, currency: 'CLP' },
    totalAmount: { amount: 10000, currency: 'CLP' },
    netAmount: { amount: 8403, currency: 'CLP' },
    taxAmount: { amount: 1597, currency: 'CLP' },
    taxRate: 19,
    paymentMethod: 'TRANSFER',
    status: 'PAID',
    createdAt: '2026-08-01T12:00:00Z',
    updatedAt: '2026-08-01T12:00:00Z',
    ...overrides,
  } as OrderDto;
}

function payment(overrides: Partial<PaymentDto> = {}): PaymentDto {
  return {
    id: 'payment-1',
    orderId: 'order-1',
    method: 'TRANSFER',
    status: 'APPROVED',
    createdAt: '2026-08-01T12:00:00Z',
    ...overrides,
  };
}

function boleta(overrides: Partial<SalesDocumentDto> = {}): SalesDocumentDto {
  return {
    id: 'doc-1',
    orderId: 'order-1',
    documentType: 'BOLETA',
    folio: '1042',
    issuedAt: '2026-08-01T12:05:00Z',
    netAmount: 8403,
    taxAmount: 1597,
    taxRate: 19,
    totalAmount: 10000,
    currency: 'CLP',
    receiverRut: null,
    receiverBusinessName: null,
    receiverBusinessActivity: null,
    receiverName: null,
    receiverEmail: null,
    fileAttached: true,
    status: 'ISSUED',
    voidedAt: null,
    voidReason: null,
    replacesDocumentId: null,
    referenceCode: null,
    issuedBy: 'admin@pilarestilo.com',
    ...overrides,
  };
}

function renderDrawer(overrides: {
  sale?: Partial<SaleSummaryDto>;
  canIssue?: boolean;
  canVoid?: boolean;
  canCancelSale?: boolean;
} = {}) {
  const onClose = vi.fn();
  const onChanged = vi.fn();
  render(
    <SaleDetailDrawer
      sale={sale(overrides.sale)}
      token={TOKEN}
      canIssue={overrides.canIssue ?? true}
      canVoid={overrides.canVoid ?? true}
      canCancelSale={overrides.canCancelSale ?? true}
      onClose={onClose}
      onChanged={onChanged}
    />,
  );
  return { onClose, onChanged };
}

beforeEach(() => {
  vi.clearAllMocks();
  getOrderById.mockResolvedValue(order());
  getPaymentByOrder.mockResolvedValue(payment());
  getSalesDocumentsByOrder.mockResolvedValue([]);
  getNextFolio.mockResolvedValue(null);
});

describe('SaleDetailDrawer', () => {
  it('shows the order summary once loaded', async () => {
    renderDrawer();

    await screen.findByText('Ana Perez');
    expect(screen.getByText(/Blazer/)).toBeInTheDocument();
  });

  it('warns when the gateway flagged the payment as refunded, and stays quiet otherwise', async () => {
    getPaymentByOrder.mockResolvedValue(payment({ gatewayFlag: 'REFUNDED', gatewayFlaggedAt: '2026-09-04T12:00:00Z' }));
    renderDrawer();

    await screen.findByText('Ana Perez');
    expect(screen.getByText(/reportó un reembolso/i)).toBeInTheDocument();
  });

  it('does not show the gateway-flag warning when nothing was flagged', async () => {
    renderDrawer();

    await screen.findByText('Ana Perez');
    expect(screen.queryByText(/reportó un reembolso/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/reportó un contracargo/i)).not.toBeInTheDocument();
  });

  it('shows "no boleta" and lets ADMIN cancel the sale when there is no live document', async () => {
    renderDrawer({ canCancelSale: true, sale: { orderStatus: 'PAID' } });

    await screen.findByText(/no tiene boleta registrada/i);
    expect(screen.getByRole('button', { name: /anular la venta/i })).toBeInTheDocument();
  });

  it('does not offer to cancel the sale from an order status that cannot be cancelled', async () => {
    renderDrawer({ canCancelSale: true, sale: { orderStatus: 'DELIVERED' } });

    await screen.findByText(/no tiene boleta registrada/i);
    expect(screen.queryByRole('button', { name: /anular la venta/i })).not.toBeInTheDocument();
  });

  it('shows the live boleta and the void/reissue actions when one exists', async () => {
    getSalesDocumentsByOrder.mockResolvedValue([boleta()]);
    renderDrawer({ canVoid: true, canIssue: true });

    await screen.findByText('1042');
    expect(screen.getByRole('button', { name: /^anular$/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /anular y reemitir/i })).toBeInTheDocument();
  });

  it('requires a folio before issuing a document', async () => {
    const user = userEvent.setup();
    renderDrawer();
    await screen.findByLabelText(/^folio/i);

    await user.click(screen.getByRole('button', { name: /registrar boleta/i }));

    expect(await screen.findByRole('status')).toHaveTextContent(/folio es obligatorio/i);
    expect(issueSalesDocument).not.toHaveBeenCalled();
  });

  it('requires receiver identity fields for a factura but not for a boleta', async () => {
    const user = userEvent.setup();
    renderDrawer();
    await screen.findByLabelText(/^folio/i);
    await user.type(screen.getByLabelText(/^folio/i), '1042');
    await user.selectOptions(screen.getByLabelText(/tipo de documento/i), 'FACTURA');

    await user.click(screen.getByRole('button', { name: /registrar factura/i }));

    expect(await screen.findByRole('status')).toHaveTextContent(/requiere rut, raz[oó]n social y giro/i);
    expect(issueSalesDocument).not.toHaveBeenCalled();
  });

  it('issues a boleta with the trimmed folio and reloads the document list', async () => {
    const user = userEvent.setup();
    issueSalesDocument.mockResolvedValue(boleta());
    getSalesDocumentsByOrder.mockResolvedValueOnce([]).mockResolvedValueOnce([boleta()]);
    const { onChanged } = renderDrawer();
    await screen.findByLabelText(/^folio/i);

    await user.type(screen.getByLabelText(/^folio/i), '  1042  ');
    await user.click(screen.getByRole('button', { name: /registrar boleta/i }));

    await waitFor(() => expect(issueSalesDocument).toHaveBeenCalledTimes(1));
    const [payload] = issueSalesDocument.mock.calls[0];
    expect(payload).toMatchObject({ orderId: 'order-1', documentType: 'BOLETA', folio: '1042' });
    expect(onChanged).toHaveBeenCalled();
    expect(await screen.findByRole('status')).toHaveTextContent(/registrada/i);
  });

  it('issues a factura with the receiver identity fields', async () => {
    const user = userEvent.setup();
    issueSalesDocument.mockResolvedValue(boleta({ documentType: 'FACTURA' }));
    const { onChanged } = renderDrawer();
    await screen.findByLabelText(/^folio/i);

    await user.selectOptions(screen.getByLabelText(/tipo de documento/i), 'FACTURA');
    await user.type(screen.getByLabelText(/^folio/i), '1043');
    await user.type(screen.getByLabelText(/rut receptor/i), '11.111.111-1');
    await user.type(screen.getByLabelText(/raz[oó]n social/i), 'Ana Perez EIRL');
    await user.type(screen.getByLabelText(/^giro/i), 'Venta de ropa');
    await user.click(screen.getByRole('button', { name: /registrar factura/i }));

    await waitFor(() => expect(issueSalesDocument).toHaveBeenCalledTimes(1));
    const [payload] = issueSalesDocument.mock.calls[0];
    expect(payload).toMatchObject({
      documentType: 'FACTURA',
      receiverRut: '11.111.111-1',
      receiverBusinessName: 'Ana Perez EIRL',
      receiverBusinessActivity: 'Venta de ropa',
    });
    expect(onChanged).toHaveBeenCalled();
  });

  it('prefills an empty folio with the suggestion for the selected document type', async () => {
    getNextFolio.mockResolvedValue(1043);
    renderDrawer();

    await waitFor(() => expect(screen.getByLabelText(/^folio/i)).toHaveValue('1043'));
    expect(getNextFolio).toHaveBeenCalledWith('BOLETA', TOKEN);
    expect(screen.getByText(/sugerido a partir del último folio/i)).toBeInTheDocument();
  });

  it('never overwrites a folio the operator already started typing', async () => {
    let resolveNextFolio: (value: number) => void = () => {};
    getNextFolio.mockReturnValue(new Promise((resolve) => { resolveNextFolio = resolve; }));
    const user = userEvent.setup();
    renderDrawer();
    await screen.findByLabelText(/^folio/i);

    await user.type(screen.getByLabelText(/^folio/i), '2001');
    resolveNextFolio(9999);
    await waitFor(() => expect(getNextFolio).toHaveBeenCalled());

    expect(screen.getByLabelText(/^folio/i)).toHaveValue('2001');
    expect(screen.queryByText(/sugerido a partir del último folio/i)).not.toBeInTheDocument();
  });

  it('requires a reason before confirming a void', async () => {
    getSalesDocumentsByOrder.mockResolvedValue([boleta()]);
    const user = userEvent.setup();
    renderDrawer();
    await screen.findByText('1042');

    await user.click(screen.getByRole('button', { name: /^anular$/i }));
    await user.click(screen.getByRole('button', { name: /confirmar anulaci[oó]n/i }));

    expect(await screen.findByRole('status')).toHaveTextContent(/motivo de la anulaci[oó]n/i);
    expect(voidSalesDocument).not.toHaveBeenCalled();
  });

  it('voids only the document when "also cancel the sale" is left unchecked', async () => {
    getSalesDocumentsByOrder.mockResolvedValue([boleta()]);
    voidSalesDocument.mockResolvedValue(boleta({ status: 'VOIDED' }));
    const user = userEvent.setup();
    renderDrawer();
    await screen.findByText('1042');

    await user.click(screen.getByRole('button', { name: /^anular$/i }));
    await user.type(screen.getByLabelText(/motivo de la anulaci[oó]n/i), 'Folio equivocado');
    await user.click(screen.getByRole('button', { name: /confirmar anulaci[oó]n/i }));

    await waitFor(() => expect(voidSalesDocument).toHaveBeenCalledWith('doc-1', 'Folio equivocado', TOKEN));
    expect(cancelSale).not.toHaveBeenCalled();
  });

  it('cancels the whole sale when "also cancel the sale" is checked', async () => {
    getSalesDocumentsByOrder.mockResolvedValue([boleta()]);
    cancelSale.mockResolvedValue(order({ status: 'CANCELLED' }));
    const user = userEvent.setup();
    renderDrawer();
    await screen.findByText('1042');

    await user.click(screen.getByRole('button', { name: /^anular$/i }));
    await user.type(screen.getByLabelText(/motivo de la anulaci[oó]n/i), 'Cliente se arrepintió');
    await user.click(screen.getByRole('checkbox', { name: /cerrar tambi[eé]n la venta/i }));
    await user.click(screen.getByRole('button', { name: /anular la venta completa/i }));

    await waitFor(() => expect(cancelSale).toHaveBeenCalledWith('order-1', 'Cliente se arrepintió', TOKEN));
    expect(voidSalesDocument).not.toHaveBeenCalled();
  });

  it('reissues the document, voiding the old one with a reason', async () => {
    getSalesDocumentsByOrder.mockResolvedValue([boleta()]);
    reissueSalesDocument.mockResolvedValue(boleta({ id: 'doc-2', folio: '1044' }));
    const user = userEvent.setup();
    renderDrawer();
    await screen.findByText('1042');

    await user.click(screen.getByRole('button', { name: /anular y reemitir/i }));
    await user.type(screen.getByLabelText(/motivo de la anulaci[oó]n/i), 'Folio mal escrito');
    await user.type(screen.getByLabelText(/^folio/i), '1044');
    await user.click(screen.getByRole('button', { name: /anular y registrar la nueva/i }));

    await waitFor(() => expect(reissueSalesDocument).toHaveBeenCalledTimes(1));
    const [documentId, payload] = reissueSalesDocument.mock.calls[0];
    expect(documentId).toBe('doc-1');
    expect(payload).toMatchObject({ voidReason: 'Folio mal escrito', folio: '1044' });
  });

  /**
   * Closing used to complete the void in the background with nobody watching: the outcome banner
   * lives on this component, so it unmounted along with the request still in flight and the only
   * way to learn what happened was reopening the row.
   */
  it('blocks closing while a void is in flight, and allows it again once it settles', async () => {
    getSalesDocumentsByOrder.mockResolvedValue([boleta()]);
    let resolveVoid: (value: SalesDocumentDto) => void = () => {};
    voidSalesDocument.mockReturnValue(new Promise((resolve) => { resolveVoid = resolve; }));
    const user = userEvent.setup();
    const { onClose } = renderDrawer();
    await screen.findByText('1042');

    await user.click(screen.getByRole('button', { name: /^anular$/i }));
    await user.type(screen.getByLabelText(/motivo de la anulaci[oó]n/i), 'Folio equivocado');
    await user.click(screen.getByRole('button', { name: /confirmar anulaci[oó]n/i }));

    const closeButton = screen.getByRole('button', { name: /^cerrar$/i });
    expect(closeButton).toBeDisabled();
    await user.click(closeButton);
    expect(onClose).not.toHaveBeenCalled();

    resolveVoid(boleta({ status: 'VOIDED' }));
    await waitFor(() => expect(closeButton).not.toBeDisabled());
    expect(await screen.findByRole('status')).toHaveTextContent(/anulada/i);

    await user.click(closeButton);
    expect(onClose).toHaveBeenCalled();
  });

  it('lists voided documents in the history section', async () => {
    getSalesDocumentsByOrder.mockResolvedValue([
      boleta({ id: 'doc-old', folio: '999', status: 'VOIDED', voidReason: 'Folio duplicado' }),
    ]);
    renderDrawer();

    await screen.findByText(/no tiene boleta registrada/i);
    expect(screen.getByText(/Folio 999/)).toBeInTheDocument();
    expect(screen.getByText('Folio duplicado')).toBeInTheDocument();
  });

  it('opens an externally-hosted payment proof directly, without calling the API', async () => {
    getPaymentByOrder.mockResolvedValue(payment({ proofReference: 'https://example.com/proof.jpg' }));
    const user = userEvent.setup();
    renderDrawer();

    await user.click(await screen.findByRole('button', { name: /ver comprobante/i }));

    expect(fetchPaymentProof).not.toHaveBeenCalled();
  });

  it('fetches a stored payment proof through the authenticated endpoint', async () => {
    getPaymentByOrder.mockResolvedValue(payment({ proofReference: 'proof-key-123' }));
    fetchPaymentProof.mockResolvedValue(new Blob(['x']));
    const user = userEvent.setup();
    renderDrawer();

    await user.click(await screen.findByRole('button', { name: /ver comprobante/i }));

    await waitFor(() => expect(fetchPaymentProof).toHaveBeenCalledWith('payment-1', TOKEN));
  });

  it('closes on Escape', async () => {
    const { onClose } = renderDrawer();
    await screen.findByText('Ana Perez');

    /*
     * A real <dialog> shown via showModal() turns Escape into a native `cancel` event on its
     * own -- there is no keydown listener left in the component to catch. happy-dom's dialog
     * polyfill sets the `open` attribute but does not wire that browser behavior up, so the
     * event is dispatched directly here to stand in for it.
     */
    screen.getByRole('dialog').dispatchEvent(new Event('cancel', { cancelable: true }));

    expect(onClose).toHaveBeenCalled();
  });
});
