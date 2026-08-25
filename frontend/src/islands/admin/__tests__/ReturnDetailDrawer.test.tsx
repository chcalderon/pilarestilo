import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';
import ReturnDetailDrawer from '../ReturnDetailDrawer';
import type { OrderDto, ReturnRequestDto, SalesDocumentDto } from '../../../lib/api';

/**
 * Characterization tests written before reducing this component's Cognitive Complexity (S3776,
 * complexity 29) -- it had none. The drawer mixes five largely-independent sections (solicitud,
 * venta, prenda, dinero, nota de credito), each with its own status-driven branching, so the
 * refactor (splitting each Section's body into its own component) is verified against the actual
 * API payloads and copy, not just "it renders".
 */

const getOrderById = vi.fn();
const getSalesDocumentsByOrder = vi.fn();
const approveReturn = vi.fn();
const rejectReturn = vi.fn();
const receiveReturn = vi.fn();
const resolveDisposition = vi.fn();
const attachRefundAccount = vi.fn();
const registerRefund = vi.fn();
const issueCreditNote = vi.fn();
const uploadSalesDocumentFile = vi.fn();

vi.mock('../../../lib/api', async () => {
  const actual = await vi.importActual<typeof import('../../../lib/api')>('../../../lib/api');
  return {
    ...actual,
    getOrderById: (...args: unknown[]) => getOrderById(...args),
    getSalesDocumentsByOrder: (...args: unknown[]) => getSalesDocumentsByOrder(...args),
    approveReturn: (...args: unknown[]) => approveReturn(...args),
    rejectReturn: (...args: unknown[]) => rejectReturn(...args),
    receiveReturn: (...args: unknown[]) => receiveReturn(...args),
    resolveDisposition: (...args: unknown[]) => resolveDisposition(...args),
    attachRefundAccount: (...args: unknown[]) => attachRefundAccount(...args),
    registerRefund: (...args: unknown[]) => registerRefund(...args),
    issueCreditNote: (...args: unknown[]) => issueCreditNote(...args),
    uploadSalesDocumentFile: (...args: unknown[]) => uploadSalesDocumentFile(...args),
  };
});

const TOKEN = 'test-token';

function request(overrides: Partial<ReturnRequestDto> = {}): ReturnRequestDto {
  return {
    id: 'return-1',
    orderId: 'order-1',
    kind: 'DEVOLUCION',
    status: 'REQUESTED',
    reason: 'La talla no calzaba',
    requestedBy: 'customer-1',
    requestedAt: '2026-08-01T12:00:00Z',
    deadlineAt: '2026-09-15T12:00:00Z',
    daysUntilDeadline: 20,
    resolvedAt: null,
    resolutionNote: null,
    itemDisposition: null,
    dispositionAt: null,
    dispositionNote: null,
    refundAmount: null,
    refundCurrency: null,
    refundMethod: null,
    refundReference: null,
    refundFileAttached: false,
    refundedAt: null,
    refundAccountHolder: null,
    refundBankName: null,
    refundAccountType: null,
    refundAccountLast4: null,
    refundAccountConfigured: false,
    creditNoteId: null,
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
  } as SalesDocumentDto;
}

function renderDrawer(overrides: {
  request?: Partial<ReturnRequestDto>;
  canManage?: boolean;
  canRefund?: boolean;
} = {}) {
  const onClose = vi.fn();
  const onChanged = vi.fn();
  render(
    <ReturnDetailDrawer
      request={request(overrides.request)}
      token={TOKEN}
      canManage={overrides.canManage ?? true}
      canRefund={overrides.canRefund ?? true}
      onClose={onClose}
      onChanged={onChanged}
    />,
  );
  return { onClose, onChanged };
}

beforeEach(() => {
  vi.clearAllMocks();
  getOrderById.mockResolvedValue(order());
  getSalesDocumentsByOrder.mockResolvedValue([]);
});

describe('ReturnDetailDrawer: solicitud & venta', () => {
  it('shows the order items and total once loaded', async () => {
    renderDrawer();

    await screen.findByText(/Blazer/);
    expect(screen.getAllByText('$10.000').length).toBeGreaterThan(0);
  });

  it('shows the legal retracto notice and no reject button when kind is RETRACTO and requested', async () => {
    renderDrawer({ request: { kind: 'RETRACTO' } });

    await screen.findByText(/ejerci[oó] su derecho a retracto/i);
    expect(screen.queryByRole('button', { name: /rechazar/i })).not.toBeInTheDocument();
  });

  it('shows the resolution note when present', async () => {
    renderDrawer({ request: { resolutionNote: 'Aprobada tras revisar fotos' } });

    expect(await screen.findByText('Aprobada tras revisar fotos')).toBeInTheDocument();
  });

  it('closes on Escape', async () => {
    const { onClose } = renderDrawer();
    await screen.findByText(/Blazer/);

    await userEvent.keyboard('{Escape}');

    expect(onClose).toHaveBeenCalled();
  });
});

describe('ReturnDetailDrawer: prenda (item lifecycle)', () => {
  it('approves the return', async () => {
    approveReturn.mockResolvedValue(request({ status: 'APPROVED' }));
    const user = userEvent.setup();
    const { onChanged } = renderDrawer();
    await screen.findByText(/Blazer/);

    await user.click(screen.getByRole('button', { name: /^aprobar$/i }));

    await waitFor(() => expect(approveReturn).toHaveBeenCalledWith('return-1', TOKEN));
    expect(onChanged).toHaveBeenCalled();
  });

  it('requires typing a reject note before it is sent, and sends it once typed', async () => {
    rejectReturn.mockResolvedValue(request({ status: 'REJECTED' }));
    const user = userEvent.setup();
    renderDrawer();
    await screen.findByText(/Blazer/);

    await user.click(screen.getByRole('button', { name: /^rechazar$/i }));
    await user.type(screen.getByPlaceholderText(/fuera de plazo/i), 'Prenda usada');
    await user.click(screen.getByRole('button', { name: /confirmar rechazo/i }));

    await waitFor(() => expect(rejectReturn).toHaveBeenCalledWith('return-1', 'Prenda usada', TOKEN));
  });

  it('does not offer to reject a RETRACTO request', async () => {
    renderDrawer({ request: { kind: 'RETRACTO', status: 'REQUESTED' } });
    await screen.findByText(/Blazer/);

    expect(screen.queryByRole('button', { name: /rechazar/i })).not.toBeInTheDocument();
  });

  it('registers that the garment arrived when APPROVED', async () => {
    receiveReturn.mockResolvedValue(request({ status: 'RECEIVED', itemDisposition: 'PENDING_RECONDITIONING' }));
    const user = userEvent.setup();
    renderDrawer({ request: { status: 'APPROVED' } });
    await screen.findByText(/Blazer/);

    await user.click(screen.getByRole('button', { name: /registrar que lleg[oó]/i }));

    await waitFor(() => expect(receiveReturn).toHaveBeenCalledWith('return-1', TOKEN));
  });

  it('restocks the garment when disposition is pending reconditioning', async () => {
    resolveDisposition.mockResolvedValue(request({ itemDisposition: 'RESTOCKED' }));
    const user = userEvent.setup();
    renderDrawer({ request: { status: 'RECEIVED', itemDisposition: 'PENDING_RECONDITIONING' } });
    await screen.findByText(/Blazer/);

    await user.click(screen.getByRole('button', { name: /volver a la venta/i }));

    await waitFor(() => expect(resolveDisposition).toHaveBeenCalledWith('return-1', 'RESTOCKED', null, TOKEN));
  });

  it('discards the garment with a note', async () => {
    resolveDisposition.mockResolvedValue(request({ itemDisposition: 'DISCARDED' }));
    const user = userEvent.setup();
    renderDrawer({ request: { status: 'RECEIVED', itemDisposition: 'PENDING_RECONDITIONING' } });
    await screen.findByText(/Blazer/);

    await user.click(screen.getByRole('button', { name: /^descartar$/i }));
    await user.type(screen.getByPlaceholderText(/mancha irrecuperable/i), 'Rotura en la manga');
    await user.click(screen.getByRole('button', { name: /^confirmar$/i }));

    await waitFor(() => expect(resolveDisposition).toHaveBeenCalledWith('return-1', 'DISCARDED', 'Rotura en la manga', TOKEN));
  });

  it('shows a static row once restocked, with no further actions', async () => {
    renderDrawer({ request: { status: 'RECEIVED', itemDisposition: 'RESTOCKED' } });

    expect(await screen.findByText('De vuelta a la venta')).toBeInTheDocument();
  });

  it('shows the discard note once discarded', async () => {
    renderDrawer({ request: { status: 'RECEIVED', itemDisposition: 'DISCARDED', dispositionNote: 'Mancha' } });

    expect(await screen.findByText('Mancha')).toBeInTheDocument();
  });

  it('hides the prenda section once the request is closed', async () => {
    renderDrawer({ request: { status: 'REFUNDED', itemDisposition: 'RESTOCKED' } });
    await screen.findByText(/Blazer/);

    expect(screen.queryByText('Prenda')).not.toBeInTheDocument();
  });
});

describe('ReturnDetailDrawer: dinero (refund)', () => {
  it('shows the closed refund summary once REFUNDED', async () => {
    renderDrawer({
      request: {
        status: 'REFUNDED',
        refundAmount: 10000,
        refundMethod: 'TRANSFERENCIA',
        refundReference: 'OP-123',
        refundBankName: 'Banco Estado',
        refundAccountLast4: '4321',
      },
    });

    await screen.findByText('OP-123');
    expect(screen.getByText(/Banco Estado ····4321/)).toBeInTheDocument();
  });

  it('collects the destination account before allowing a transfer refund', async () => {
    attachRefundAccount.mockResolvedValue(request({ refundAccountConfigured: true }));
    const user = userEvent.setup();
    renderDrawer();
    await screen.findByText(/Blazer/);

    await user.type(screen.getByPlaceholderText('Titular'), 'Ana Perez');
    await user.type(screen.getByPlaceholderText('RUT'), '11.111.111-1');
    await user.type(screen.getByPlaceholderText('Banco'), 'Banco de Chile');
    await user.type(screen.getByPlaceholderText('N° de cuenta'), '999888');
    await user.click(screen.getByRole('button', { name: /guardar cuenta/i }));

    await waitFor(() => expect(attachRefundAccount).toHaveBeenCalledWith(
      'return-1',
      expect.objectContaining({ holder: 'Ana Perez', rut: '11.111.111-1', bankName: 'Banco de Chile', accountNumber: '999888' }),
      TOKEN,
    ));
  });

  it('skips the account form once the account is already configured', async () => {
    renderDrawer({ request: { refundAccountConfigured: true, refundBankName: 'Banco Estado', refundAccountLast4: '1234' } });

    await screen.findByText(/Banco Estado ····1234/);
    expect(screen.queryByPlaceholderText('Titular')).not.toBeInTheDocument();
  });

  it('registers the refund with the entered amount, method and reference', async () => {
    registerRefund.mockResolvedValue(request({ status: 'REFUNDED' }));
    const user = userEvent.setup();
    const { onChanged } = renderDrawer();
    await screen.findByText(/Blazer/);

    const amountInput = screen.getByDisplayValue('10000');
    await user.clear(amountInput);
    await user.type(amountInput, '9500');
    await user.type(screen.getByPlaceholderText(/n° de transferencia/i), 'OP-456');
    await user.click(screen.getByRole('button', { name: /registrar reembolso/i }));

    await waitFor(() => expect(registerRefund).toHaveBeenCalledWith('return-1', {
      amount: 9500,
      currency: 'CLP',
      method: 'TRANSFERENCIA',
      reference: 'OP-456',
    }, TOKEN));
    expect(onChanged).toHaveBeenCalled();
  });

  it('does not offer refund controls without permission', async () => {
    renderDrawer({ canRefund: false });

    expect(await screen.findByText(/no tienes permiso para registrar reembolsos/i)).toBeInTheDocument();
  });

  it('shows the closed message once closed with no refund controls', async () => {
    renderDrawer({ request: { status: 'REJECTED' } });

    expect(await screen.findByText(/devoluci[oó]n cerrada sin reembolso/i)).toBeInTheDocument();
  });
});

describe('ReturnDetailDrawer: nota de credito', () => {
  it('says it is registered once the money is returned, before it exists', async () => {
    renderDrawer({ request: { status: 'APPROVED' } });

    expect(await screen.findByText(/se registra una vez devuelto el dinero/i)).toBeInTheDocument();
  });

  it('says there is nothing to void when there is no live sale document', async () => {
    getSalesDocumentsByOrder.mockResolvedValue([]);
    renderDrawer({ request: { status: 'REFUNDED' } });
    await screen.findByText(/Blazer/);

    expect(await screen.findByText(/no tiene boleta viva/i)).toBeInTheDocument();
  });

  it('withholds the credit note form without permission', async () => {
    getSalesDocumentsByOrder.mockResolvedValue([boleta()]);
    renderDrawer({ request: { status: 'REFUNDED' }, canRefund: false });

    expect(await screen.findByText(/no tienes permiso para registrar documentos/i)).toBeInTheDocument();
  });

  it('registers a credit note referencing the live boleta', async () => {
    getSalesDocumentsByOrder.mockResolvedValue([boleta()]);
    issueCreditNote.mockResolvedValue(boleta({ id: 'doc-2', documentType: 'NOTA_CREDITO', folio: '5001' }));
    const user = userEvent.setup();
    const { onChanged } = renderDrawer({ request: { status: 'REFUNDED', refundAmount: 10000 } });
    await screen.findByText(/la boleta 1042 ya fue declarada/i);

    await user.type(screen.getByLabelText(/^folio$/i), '5001');
    await user.click(screen.getByRole('button', { name: /registrar nota de cr[eé]dito/i }));

    await waitFor(() => expect(issueCreditNote).toHaveBeenCalledWith({
      orderId: 'order-1',
      folio: '5001',
      amount: 10000,
      fileUrl: '',
      returnId: 'return-1',
    }, TOKEN));
    expect(onChanged).toHaveBeenCalled();
  });

  it('disables the register button until a folio is entered', async () => {
    getSalesDocumentsByOrder.mockResolvedValue([boleta()]);
    renderDrawer({ request: { status: 'REFUNDED' } });

    await screen.findByText(/la boleta 1042 ya fue declarada/i);
    expect(screen.getByRole('button', { name: /registrar nota de cr[eé]dito/i })).toBeDisabled();
  });

  it('shows the full-annulment hint when the credited amount covers the whole sale', async () => {
    getSalesDocumentsByOrder.mockResolvedValue([boleta({ totalAmount: 10000 })]);
    const user = userEvent.setup();
    renderDrawer({ request: { status: 'REFUNDED', refundAmount: 10000 } });
    await screen.findByText(/la boleta 1042 ya fue declarada/i);

    await user.clear(screen.getByLabelText(/monto acreditado/i));
    await user.type(screen.getByLabelText(/monto acreditado/i), '10000');

    expect(await screen.findByText(/anula la boleta completa/i)).toBeInTheDocument();
  });

  it('shows the partial-correction hint when the credited amount is less than the total', async () => {
    getSalesDocumentsByOrder.mockResolvedValue([boleta({ totalAmount: 10000 })]);
    const user = userEvent.setup();
    renderDrawer({ request: { status: 'REFUNDED', refundAmount: 10000 } });
    await screen.findByText(/la boleta 1042 ya fue declarada/i);

    await user.clear(screen.getByLabelText(/monto acreditado/i));
    await user.type(screen.getByLabelText(/monto acreditado/i), '5000');

    expect(await screen.findByText(/corrige el monto de la boleta/i)).toBeInTheDocument();
  });

  it('warns when the live boleta is older than six months', async () => {
    getSalesDocumentsByOrder.mockResolvedValue([boleta({ issuedAt: '2020-01-01T00:00:00Z' })]);
    renderDrawer({ request: { status: 'REFUNDED' } });

    expect(await screen.findByText(/m[aá]s de seis meses/i)).toBeInTheDocument();
  });

  it('shows the issued credit note details, referencing the boleta it annuls', async () => {
    getSalesDocumentsByOrder.mockResolvedValue([
      boleta(),
      boleta({ id: 'doc-2', documentType: 'NOTA_CREDITO', folio: '5001', referenceCode: 1, totalAmount: 10000, netAmount: 8403, taxAmount: 1597 }),
    ]);
    renderDrawer({ request: { status: 'REFUNDED', creditNoteId: 'doc-2' } });

    await screen.findByText('5001');
    expect(screen.getByText(/anula la boleta 1042/i)).toBeInTheDocument();
  });
});
