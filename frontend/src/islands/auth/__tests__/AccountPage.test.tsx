import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';
import AccountPage from '../AccountPage';
import type {
  OrderDto, PaymentDto, UserProfileDto, CustomerAddressDto, ReviewDto, ReturnRequestDto, LocationRegionDto,
} from '@/lib/api';

/**
 * Characterization tests written before splitting this component's 8 S3776 violations apart
 * (outer component at 126, the orders.map callback at 73, six more nested functions from 16-26)
 * -- it had none, despite being the customer account page: profile, password, addresses, orders,
 * payment proof upload, gateway checkout/simulation and reviews. Covers every tab, every
 * validation guard, the query-param tab/gateway-return handling, and the order-timeline rendering
 * so extracting OrderListItem/OrderTimelineSteps and the validation helpers can't silently change
 * behavior in code real customers depend on.
 */

const getMyProfile = vi.fn();
const updateMyProfile = vi.fn();
const changeMyPassword = vi.fn();
const getMyReviews = vi.fn();
const deleteReview = vi.fn();
const getMyOrders = vi.fn();
const getMyReturns = vi.fn();
const getPaymentByOrder = vi.fn();
const getMyAddresses = vi.fn();
const createMyAddress = vi.fn();
const updateMyAddress = vi.fn();
const deleteMyAddress = vi.fn();
const setMyAddressAsDefault = vi.fn();
const getLocationTree = vi.fn();
const confirmOrderDelivery = vi.fn();
const submitPaymentProof = vi.fn();
const uploadPaymentProof = vi.fn();
const fetchPaymentProof = vi.fn();
const uploadMyAvatar = vi.fn();
const createGatewayCheckoutSession = vi.fn();
const simulateGatewayPaymentStatus = vi.fn();

vi.mock('@/lib/api', async () => {
  const actual = await vi.importActual<typeof import('@/lib/api')>('@/lib/api');
  return {
    ...actual,
    getMyProfile: (...a: unknown[]) => getMyProfile(...a),
    updateMyProfile: (...a: unknown[]) => updateMyProfile(...a),
    changeMyPassword: (...a: unknown[]) => changeMyPassword(...a),
    getMyReviews: (...a: unknown[]) => getMyReviews(...a),
    deleteReview: (...a: unknown[]) => deleteReview(...a),
    getMyOrders: (...a: unknown[]) => getMyOrders(...a),
    getMyReturns: (...a: unknown[]) => getMyReturns(...a),
    getPaymentByOrder: (...a: unknown[]) => getPaymentByOrder(...a),
    getMyAddresses: (...a: unknown[]) => getMyAddresses(...a),
    createMyAddress: (...a: unknown[]) => createMyAddress(...a),
    updateMyAddress: (...a: unknown[]) => updateMyAddress(...a),
    deleteMyAddress: (...a: unknown[]) => deleteMyAddress(...a),
    setMyAddressAsDefault: (...a: unknown[]) => setMyAddressAsDefault(...a),
    getLocationTree: (...a: unknown[]) => getLocationTree(...a),
    confirmOrderDelivery: (...a: unknown[]) => confirmOrderDelivery(...a),
    submitPaymentProof: (...a: unknown[]) => submitPaymentProof(...a),
    uploadPaymentProof: (...a: unknown[]) => uploadPaymentProof(...a),
    fetchPaymentProof: (...a: unknown[]) => fetchPaymentProof(...a),
    uploadMyAvatar: (...a: unknown[]) => uploadMyAvatar(...a),
    createGatewayCheckoutSession: (...a: unknown[]) => createGatewayCheckoutSession(...a),
    simulateGatewayPaymentStatus: (...a: unknown[]) => simulateGatewayPaymentStatus(...a),
  };
});

const clearAuth = vi.fn();
const authSetAuth = vi.fn();
let authUser: { id: string; email: string; role: string } | null = null;

vi.mock('@/lib/authStore', () => ({
  useAuthStore: Object.assign(
    () => ({ user: authUser, token: 'tok', clearAuth }),
    { getState: () => ({ user: authUser, token: 'tok', setAuth: authSetAuth }) },
  ),
  readAuthTokenCookie: () => 'tok',
}));

vi.mock('../RetractoButton', () => ({
  default: ({ orderId }: { orderId: string }) => <div data-testid={`retracto-${orderId}`}>Retracto</div>,
}));

vi.mock('../../NotificationHistory', () => ({
  default: () => <div data-testid="notification-history">History</div>,
}));

function user_() {
  return { id: 'u1', email: 'ana@correo.cl', role: 'CUSTOMER' };
}

function profile(overrides: Partial<UserProfileDto> = {}): UserProfileDto {
  return {
    id: 'u1', email: 'ana@correo.cl', fullName: 'Ana Perez', phone: '+56911111111',
    notificationChannelPreference: 'AUTO', role: 'CUSTOMER', active: true,
    ...overrides,
  };
}

function order(overrides: Partial<OrderDto> = {}): OrderDto {
  return {
    id: 'order-1',
    customerId: 'u1',
    items: [{ id: 'i1', productId: 'p1', productName: 'Vestido rosa', unitPrice: { amount: 20000, currency: 'CLP' }, quantity: 1 }],
    subtotal: { amount: 20000, currency: 'CLP' },
    discountAmount: { amount: 0, currency: 'CLP' },
    totalAmount: { amount: 20000, currency: 'CLP' },
    paymentMethod: 'TRANSFER',
    status: 'PENDING_PAYMENT',
    createdAt: '2026-08-01T10:00:00Z',
    updatedAt: '2026-08-01T10:00:00Z',
    ...overrides,
  } as OrderDto;
}

function payment(overrides: Partial<PaymentDto> = {}): PaymentDto {
  return {
    id: 'payment-1', orderId: 'order-1', method: 'TRANSFER', status: 'PENDING', createdAt: '2026-08-01T10:00:00Z',
    ...overrides,
  };
}

function address(overrides: Partial<CustomerAddressDto> = {}): CustomerAddressDto {
  return {
    id: 'addr-1', customerId: 'u1', label: 'Casa', recipientName: 'Ana Perez', phone: '+56911111111',
    line1: 'Av. Siempre Viva 123', comuna: 'Providencia', city: 'Santiago', region: 'Metropolitana',
    isDefault: false, createdAt: '2026-08-01T10:00:00Z',
    ...overrides,
  } as CustomerAddressDto;
}

function review(overrides: Partial<ReviewDto> = {}): ReviewDto {
  return {
    id: 'r1', productId: 'p1', userId: 'u1', rating: 4, title: 'Buena', comment: 'Me gusto',
    approved: true, createdAt: '2026-08-01T10:00:00Z',
    ...overrides,
  };
}

function region(): LocationRegionDto {
  return {
    id: 1, name: 'Metropolitana',
    cities: [{ id: 10, regionId: 1, name: 'Santiago', communes: [{ id: 100, regionId: 1, cityId: 10, name: 'Providencia' }] }],
  };
}

function emptyPage<T>() {
  return { content: [] as T[], totalElements: 0, totalPages: 0, size: 20, number: 0 };
}

/**
 * window.location's properties are getters on Location.prototype, not own enumerable
 * properties, so `{...window.location}` silently drops them (href reads back as undefined).
 * Listing what's needed explicitly, with a real get/set pair for href, keeps the rest of the
 * component's URL reads working while letting a test observe/intercept navigation.
 */
function stubLocation() {
  const hrefSpy = vi.fn();
  const assignSpy = vi.fn();
  let hrefValue = window.location.href;
  vi.stubGlobal('location', {
    pathname: window.location.pathname,
    search: window.location.search,
    hash: window.location.hash,
    origin: window.location.origin,
    assign: assignSpy,
    get href() { return hrefValue; },
    set href(v: string) { hrefSpy(v); hrefValue = v; },
  });
  return { hrefSpy, assignSpy };
}

beforeEach(() => {
  vi.clearAllMocks();
  authUser = user_();
  getMyProfile.mockResolvedValue(profile());
  getMyOrders.mockResolvedValue(emptyPage<OrderDto>());
  getMyReturns.mockResolvedValue([] as ReturnRequestDto[]);
  getMyReviews.mockResolvedValue([] as ReviewDto[]);
  getMyAddresses.mockResolvedValue([] as CustomerAddressDto[]);
  getLocationTree.mockResolvedValue([region()]);
  getPaymentByOrder.mockResolvedValue(null);
  window.history.pushState({}, '', '/es/account');
});

afterEach(() => {
  vi.unstubAllGlobals();
});

async function renderReady(locale: 'es' | 'en' = 'es') {
  render(<AccountPage locale={locale} />);
  await screen.findByText('Ana Perez');
}

describe('AccountPage: loading and tabs', () => {
  it('redirects to login when not authenticated', async () => {
    authUser = null;
    const { hrefSpy } = stubLocation();
    render(<AccountPage locale="es" />);
    await waitFor(() => expect(hrefSpy).toHaveBeenCalledWith('/es/auth/login?redirect=/es/account'));
  });

  it('shows the profile tab by default and switches tabs on click', async () => {
    await renderReady();
    expect(screen.getByPlaceholderText(/tu nombre completo/i)).toBeInTheDocument();

    await userEvent.setup().click(screen.getByRole('button', { name: /mis pedidos/i }));
    await waitFor(() => expect(getMyOrders).toHaveBeenCalled());
  });

  it('jumps to the notifications tab when the URL hash is #notifications', async () => {
    window.history.pushState({}, '', '/es/account#notifications');
    render(<AccountPage locale="es" />);
    expect(await screen.findByTestId('notification-history')).toBeInTheDocument();
  });

  it('jumps to the requested tab via ?tab=addresses', async () => {
    window.history.pushState({}, '', '/es/account?tab=addresses');
    render(<AccountPage locale="es" />);
    await waitFor(() => expect(getMyAddresses).toHaveBeenCalled());
  });

  it('shows gateway-return feedback and switches to orders on a payment-gateway redirect back', async () => {
    window.history.pushState({}, '', '/es/account?mp=approved');
    render(<AccountPage locale="es" />);
    expect(await screen.findByText(/pago confirmado por pasarela/i)).toBeInTheDocument();
    await waitFor(() => expect(getMyOrders).toHaveBeenCalled());
    expect(window.location.search).toBe('');
  });

  it('shows an error message for a rejected gateway payment', async () => {
    window.history.pushState({}, '', '/es/account?collection_status=rejected');
    render(<AccountPage locale="es" />);
    expect(await screen.findByText(/rechazado o cancelado/i)).toBeInTheDocument();
  });
});

describe('AccountPage: profile', () => {
  it('loads and displays the profile', async () => {
    await renderReady();
    expect(screen.getByDisplayValue('Ana Perez')).toBeInTheDocument();
    expect(screen.getByDisplayValue('+56911111111')).toBeInTheDocument();
  });

  it('requires a non-empty name', async () => {
    await renderReady();
    const user = userEvent.setup();
    await user.clear(screen.getByPlaceholderText(/tu nombre completo/i));
    await user.click(screen.getByRole('button', { name: /guardar perfil/i }));
    expect(await screen.findByText(/el nombre no puede estar vacio/i)).toBeInTheDocument();
    expect(updateMyProfile).not.toHaveBeenCalled();
  });

  it('refuses a phone number that looks like an email', async () => {
    await renderReady();
    const user = userEvent.setup();
    const phoneInput = screen.getByPlaceholderText('+56912345678');
    await user.clear(phoneInput);
    await user.type(phoneInput, 'ana@correo.cl');
    await user.click(screen.getByRole('button', { name: /guardar perfil/i }));
    expect(await screen.findByText(/no un correo/i)).toBeInTheDocument();
  });

  it('refuses a phone number with too few digits', async () => {
    await renderReady();
    const user = userEvent.setup();
    const phoneInput = screen.getByPlaceholderText('+56912345678');
    await user.clear(phoneInput);
    await user.type(phoneInput, '123');
    await user.click(screen.getByRole('button', { name: /guardar perfil/i }));
    expect(await screen.findByText(/entre 8 y 15 digitos/i)).toBeInTheDocument();
  });

  it('saves the profile successfully', async () => {
    updateMyProfile.mockResolvedValue(profile({ fullName: 'Ana P.' }));
    await renderReady();
    await userEvent.setup().click(screen.getByRole('button', { name: /guardar perfil/i }));
    expect(await screen.findByText(/perfil actualizado/i)).toBeInTheDocument();
    expect(updateMyProfile).toHaveBeenCalledWith('Ana Perez', '+56911111111', 'AUTO', 'tok');
  });

  it('shows the server error message when saving the profile fails', async () => {
    updateMyProfile.mockRejectedValue(new Error('boom'));
    await renderReady();
    await userEvent.setup().click(screen.getByRole('button', { name: /guardar perfil/i }));
    expect(await screen.findByText('boom')).toBeInTheDocument();
  });
});

describe('AccountPage: change password', () => {
  it('requires the current password', async () => {
    await renderReady();
    await userEvent.setup().click(screen.getByRole('button', { name: /actualizar contraseña/i }));
    expect(await screen.findByText(/ingresa tu contraseña actual/i)).toBeInTheDocument();
  });

  it('requires the new password to be at least 8 characters', async () => {
    await renderReady();
    const user = userEvent.setup();
    await user.type(screen.getByPlaceholderText(/contraseña actual/i), 'oldpass1');
    await user.type(screen.getByPlaceholderText(/^nueva contraseña$/i), 'short');
    await user.click(screen.getByRole('button', { name: /actualizar contraseña/i }));
    expect(await screen.findByText(/al menos 8 caracteres/i)).toBeInTheDocument();
  });

  it('requires the confirmation to match', async () => {
    await renderReady();
    const user = userEvent.setup();
    await user.type(screen.getByPlaceholderText(/contraseña actual/i), 'oldpass1');
    await user.type(screen.getByPlaceholderText(/^nueva contraseña$/i), 'newpass1');
    await user.type(screen.getByPlaceholderText(/confirmar nueva contraseña/i), 'different1');
    await user.click(screen.getByRole('button', { name: /actualizar contraseña/i }));
    expect(await screen.findByText(/no coinciden/i)).toBeInTheDocument();
  });

  it('changes the password successfully and clears the fields', async () => {
    changeMyPassword.mockResolvedValue(undefined);
    await renderReady();
    const user = userEvent.setup();
    await user.type(screen.getByPlaceholderText(/contraseña actual/i), 'oldpass1');
    await user.type(screen.getByPlaceholderText(/^nueva contraseña$/i), 'newpass1');
    await user.type(screen.getByPlaceholderText(/confirmar nueva contraseña/i), 'newpass1');
    await user.click(screen.getByRole('button', { name: /actualizar contraseña/i }));
    expect(await screen.findByText(/actualizada correctamente/i)).toBeInTheDocument();
    expect(screen.getByPlaceholderText(/contraseña actual/i)).toHaveValue('');
  });

  it('maps the "current password is invalid" server error to a friendly message', async () => {
    changeMyPassword.mockRejectedValue(new Error('Current password is invalid'));
    await renderReady();
    const user = userEvent.setup();
    await user.type(screen.getByPlaceholderText(/contraseña actual/i), 'wrongpass');
    await user.type(screen.getByPlaceholderText(/^nueva contraseña$/i), 'newpass1');
    await user.type(screen.getByPlaceholderText(/confirmar nueva contraseña/i), 'newpass1');
    await user.click(screen.getByRole('button', { name: /actualizar contraseña/i }));
    expect(await screen.findByText(/no es correcta/i)).toBeInTheDocument();
  });
});

describe('AccountPage: reviews', () => {
  it('lists reviews and deletes one', async () => {
    getMyReviews.mockResolvedValue([review()]);
    deleteReview.mockResolvedValue(undefined);
    await renderReady();
    await userEvent.setup().click(screen.getByRole('button', { name: /mis resenas/i }));

    expect(await screen.findByText('Buena')).toBeInTheDocument();
    await userEvent.setup().click(screen.getByRole('button', { name: /eliminar resena/i }));
    await waitFor(() => expect(screen.queryByText('Buena')).not.toBeInTheDocument());
    expect(deleteReview).toHaveBeenCalledWith('r1', 'tok');
  });

  it('shows the empty state with no reviews', async () => {
    await renderReady();
    await userEvent.setup().click(screen.getByRole('button', { name: /mis resenas/i }));
    expect(await screen.findByText(/aun no escribiste resenas/i)).toBeInTheDocument();
  });
});

describe('AccountPage: addresses', () => {
  async function goToAddresses() {
    await renderReady();
    await userEvent.setup().click(screen.getByRole('button', { name: /^direcciones$/i }));
    await waitFor(() => expect(getMyAddresses).toHaveBeenCalled());
  }

  it('lists addresses and shows the default badge', async () => {
    getMyAddresses.mockResolvedValue([address({ isDefault: true })]);
    await goToAddresses();
    expect(await screen.findByText('Casa')).toBeInTheDocument();
    expect(screen.getByText(/principal/i)).toBeInTheDocument();
  });

  it('validates the new-address form before saving', async () => {
    await goToAddresses();
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: /agregar dirección/i }));
    await user.click(screen.getByRole('button', { name: /guardar dirección/i }));
    expect(await screen.findByText(/alias de dirección/i)).toBeInTheDocument();
    expect(createMyAddress).not.toHaveBeenCalled();
  });

  it('creates an address with the resolved region/city/comuna names', async () => {
    createMyAddress.mockResolvedValue(address({ id: 'new-1' }));
    getMyAddresses.mockResolvedValue([]);
    await goToAddresses();
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: /agregar dirección/i }));

    await user.type(screen.getByPlaceholderText(/alias/i), 'Casa');
    await user.type(screen.getByPlaceholderText(/^destinatario$/i), 'Ana Perez');
    await user.type(screen.getByPlaceholderText(/^teléfono$/i), '+56911111111');
    await user.type(screen.getByPlaceholderText(/línea 1/i), 'Av. Siempre Viva 123');
    await user.selectOptions(screen.getByDisplayValue(/selecciona region/i), '1');
    await user.selectOptions(screen.getByDisplayValue(/selecciona ciudad/i), '10');
    await user.selectOptions(screen.getByDisplayValue(/selecciona comuna/i), '100');

    await user.click(screen.getByRole('button', { name: /guardar dirección/i }));

    await waitFor(() => expect(createMyAddress).toHaveBeenCalledWith(
      expect.objectContaining({ region: 'Metropolitana', city: 'Santiago', comuna: 'Providencia' }),
      'tok',
    ));
    expect(await screen.findByText(/dirección guardada/i)).toBeInTheDocument();
  });

  it('prefills the modal when editing an existing address', async () => {
    getMyAddresses.mockResolvedValue([address()]);
    await goToAddresses();
    await screen.findByText('Casa');
    await userEvent.setup().click(screen.getByRole('button', { name: /^editar$/i }));
    expect(screen.getByDisplayValue('Casa')).toBeInTheDocument();
    expect(screen.getByText(/editar dirección/i)).toBeInTheDocument();
  });

  it('deletes an address', async () => {
    getMyAddresses.mockResolvedValueOnce([address()]).mockResolvedValueOnce([]);
    deleteMyAddress.mockResolvedValue(undefined);
    await goToAddresses();
    await screen.findByText('Casa');
    await userEvent.setup().click(screen.getByRole('button', { name: /^eliminar$/i }));
    expect(deleteMyAddress).toHaveBeenCalledWith('addr-1', 'tok');
    await waitFor(() => expect(screen.queryByText('Casa')).not.toBeInTheDocument());
  });

  it('sets an address as default', async () => {
    getMyAddresses.mockResolvedValue([address()]);
    setMyAddressAsDefault.mockResolvedValue(undefined);
    await goToAddresses();
    await screen.findByText('Casa');
    await userEvent.setup().click(screen.getByRole('button', { name: /marcar principal/i }));
    expect(setMyAddressAsDefault).toHaveBeenCalledWith('addr-1', 'tok');
  });
});

describe('AccountPage: orders', () => {
  async function goToOrders() {
    await renderReady();
    await userEvent.setup().click(screen.getByRole('button', { name: /mis pedidos/i }));
    await waitFor(() => expect(getMyOrders).toHaveBeenCalled());
  }

  it('shows the empty state with no orders', async () => {
    await goToOrders();
    expect(await screen.findByText(/aun no tienes pedidos/i)).toBeInTheDocument();
  });

  it('renders an order with its id, status and total', async () => {
    getMyOrders.mockResolvedValue({
      content: [order({ totalAmount: { amount: 23000, currency: 'CLP' } })], totalElements: 1, totalPages: 1, size: 20, number: 0,
    });
    await goToOrders();
    expect(await screen.findByText('order-1')).toBeInTheDocument();
    expect(screen.getByText(/\$23\.000/)).toBeInTheDocument();
  });

  it('shows the retracto button for a delivered order and the confirm-delivery button for a shipped one', async () => {
    getMyOrders.mockResolvedValue({
      content: [order({ id: 'delivered-1', status: 'DELIVERED' }), order({ id: 'shipped-1', status: 'SHIPPED' })],
      totalElements: 2, totalPages: 1, size: 20, number: 0,
    });
    await goToOrders();
    expect(await screen.findByTestId('retracto-delivered-1')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /marcar como recibido/i })).toBeInTheDocument();
  });

  it('confirms delivery', async () => {
    // The success message reuses gatewayFeedbackByOrder, which only renders for a WEBPAY/
    // MERCADOPAGO order -- a TRANSFER order would call confirmOrderDelivery just the same but
    // never show the confirmation text anywhere in the DOM.
    getMyOrders.mockResolvedValueOnce({ content: [order({ id: 'shipped-1', status: 'SHIPPED', paymentMethod: 'WEBPAY' })], totalElements: 1, totalPages: 1, size: 20, number: 0 })
      .mockResolvedValue({ content: [order({ id: 'shipped-1', status: 'DELIVERED', paymentMethod: 'WEBPAY' })], totalElements: 1, totalPages: 1, size: 20, number: 0 });
    confirmOrderDelivery.mockResolvedValue(undefined);
    await goToOrders();
    await screen.findByText('shipped-1');
    await userEvent.setup().click(screen.getByRole('button', { name: /marcar como recibido/i }));
    expect(confirmOrderDelivery).toHaveBeenCalledWith('shipped-1', 'tok');
    expect(await screen.findByText(/marcado como recibido/i)).toBeInTheDocument();
  });

  it('uploads and submits a transfer payment proof', async () => {
    getMyOrders.mockResolvedValue({ content: [order({ paymentMethod: 'TRANSFER' })], totalElements: 1, totalPages: 1, size: 20, number: 0 });
    getPaymentByOrder.mockResolvedValue(payment({ status: 'PENDING' }));
    uploadPaymentProof.mockResolvedValue('proof-ref-1');
    submitPaymentProof.mockResolvedValue(payment({ status: 'SUBMITTED', proofReference: 'proof-ref-1' }));
    await goToOrders();
    await screen.findByText('order-1');
    await waitFor(() => expect(getPaymentByOrder).toHaveBeenCalled());

    const user = userEvent.setup();
    const file = new File(['x'], 'comprobante.png', { type: 'image/png' });
    const fileInput = document.querySelector('input[type="file"][accept="image/*"]') as HTMLInputElement;
    await user.upload(fileInput, file);
    await user.click(screen.getByRole('button', { name: /enviar comprobante/i }));

    expect(uploadPaymentProof).toHaveBeenCalledWith(file, 'tok');
    await waitFor(() => expect(submitPaymentProof).toHaveBeenCalledWith('payment-1', 'proof-ref-1', 'tok'));
    expect(await screen.findByText(/lo revisaremos pronto/i)).toBeInTheDocument();
  });

  it('starts a gateway checkout and redirects to the checkout URL', async () => {
    getMyOrders.mockResolvedValue({ content: [order({ paymentMethod: 'WEBPAY' })], totalElements: 1, totalPages: 1, size: 20, number: 0 });
    getPaymentByOrder.mockResolvedValue(payment({ status: 'PENDING' }));
    createGatewayCheckoutSession.mockResolvedValue({
      paymentId: 'payment-1', orderId: 'order-1', gatewayReference: 'ref-1', checkoutUrl: 'https://gateway.test/pay/abc',
    });
    await goToOrders();
    await screen.findByText('order-1');
    await waitFor(() => expect(getPaymentByOrder).toHaveBeenCalled());
    const { assignSpy } = stubLocation();

    await userEvent.setup().click(screen.getByRole('button', { name: /ir a pagar/i }));
    await waitFor(() => expect(assignSpy).toHaveBeenCalledWith('https://gateway.test/pay/abc'));
  });

  it('simulates an approved gateway payment', async () => {
    getMyOrders.mockResolvedValue({ content: [order({ paymentMethod: 'MERCADOPAGO' })], totalElements: 1, totalPages: 1, size: 20, number: 0 });
    getPaymentByOrder.mockResolvedValue(payment({ status: 'PENDING' }));
    simulateGatewayPaymentStatus.mockResolvedValue(undefined);
    await goToOrders();
    await screen.findByText('order-1');
    await waitFor(() => expect(getPaymentByOrder).toHaveBeenCalled());

    await userEvent.setup().click(screen.getByRole('button', { name: /simular aprobado/i }));
    expect(simulateGatewayPaymentStatus).toHaveBeenCalledWith('payment-1', 'APPROVED');
    expect(await screen.findByText(/pago aprobado/i)).toBeInTheDocument();
  });

  it('shows the timeline with the current step highlighted and prior steps done', async () => {
    getMyOrders.mockResolvedValue({ content: [order({ status: 'PAID' })], totalElements: 1, totalPages: 1, size: 20, number: 0 });
    await goToOrders();
    const orderCard = (await screen.findByText('order-1')).closest('li') as HTMLElement;
    // "Pagado" appears twice by coincidence: the status badge (orderStatusLabel) and the
    // timeline's current-step node (orderTimelineLabel) render the same word for PAID.
    expect(within(orderCard).getAllByText(/^pagado$/i)).toHaveLength(2);
    expect(within(orderCard).getByText(/^creado$/i)).toBeInTheDocument();
  });

  it('shows a cancelled order as ended, not as still in progress', async () => {
    getMyOrders.mockResolvedValue({ content: [order({ status: 'CANCELLED' })], totalElements: 1, totalPages: 1, size: 20, number: 0 });
    await goToOrders();
    const orderCard = (await screen.findByText('order-1')).closest('li') as HTMLElement;
    // Same coincidence as PAID above: the status badge and the timeline's terminal node both
    // render "Cancelado".
    expect(within(orderCard).getAllByText(/^cancelado$/i)).toHaveLength(2);
    expect(within(orderCard).getByText(/si fue por falta de pago/i)).toBeInTheDocument();
  });
});

describe('AccountPage: logout', () => {
  it('clears auth and redirects home', async () => {
    await renderReady();
    const { hrefSpy } = stubLocation();
    await userEvent.setup().click(screen.getByRole('button', { name: /cerrar sesion/i }));
    expect(clearAuth).toHaveBeenCalled();
    expect(hrefSpy).toHaveBeenCalledWith('/es/');
  });
});
