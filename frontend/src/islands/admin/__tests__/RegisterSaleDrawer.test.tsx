import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';
import RegisterSaleDrawer from '../RegisterSaleDrawer';
import { registerExternalSale, searchProducts } from '../../../lib/api';

vi.mock('../../../lib/api', () => ({
  searchProducts: vi.fn(),
  registerExternalSale: vi.fn(),
}));

// jsdom/happy-dom has no <dialog>.showModal by default
beforeEach(() => {
  if (!HTMLDialogElement.prototype.showModal) {
    HTMLDialogElement.prototype.showModal = vi.fn();
    HTMLDialogElement.prototype.close = vi.fn();
  }
  vi.mocked(searchProducts).mockResolvedValue({
    content: [
      { id: 'p1', name: 'Vestido', price: { amount: 19990, currency: 'CLP' }, variants: [
        { color: 'Rojo', size: 'M', stock: 3, stockOnHand: 3, stockReserved: 0, stockAvailable: 3 },
      ] } as never,
    ],
    totalElements: 1, totalPages: 1, size: 8, number: 0,
  } as never);
  vi.mocked(registerExternalSale).mockResolvedValue({ id: 'o1' } as never);
});

function renderDrawer() {
  const onClose = vi.fn();
  const onCreated = vi.fn();
  render(<RegisterSaleDrawer token="t" onClose={onClose} onCreated={onCreated} />);
  return { onClose, onCreated };
}

async function addTheProduct(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByPlaceholderText(/buscar producto/i), 'ves');
  const hit = await screen.findByRole('button', { name: /vestido/i });
  await user.click(hit);
}

describe('RegisterSaleDrawer', () => {
  it('recalculates the live total when a line price is edited', async () => {
    const user = userEvent.setup();
    renderDrawer();
    await addTheProduct(user);

    expect(screen.getByText('$19.990')).toBeInTheDocument();

    const priceInput = screen.getByLabelText(/precio unitario/i);
    await user.clear(priceInput);
    await user.type(priceInput, '15000');

    await waitFor(() => expect(screen.getByText('$15.000')).toBeInTheDocument());
  });

  it('requires an address only when Envío is selected', async () => {
    const user = userEvent.setup();
    const { onCreated } = renderDrawer();
    await addTheProduct(user);
    await user.type(screen.getByLabelText(/^comprador$/i), 'Ana');
    await user.type(screen.getByLabelText(/contacto/i), '@ana');

    // Envío is the default — submit without an address fails
    await user.click(screen.getByRole('button', { name: /^registrar venta$/i }));
    expect(await screen.findByText(/dirección es obligatoria/i)).toBeInTheDocument();
    expect(onCreated).not.toHaveBeenCalled();

    // switching to Retiro removes the requirement
    await user.click(screen.getByRole('button', { name: /retiro en persona/i }));
    expect(screen.queryByPlaceholderText(/dirección de envío/i)).not.toBeInTheDocument();
  });

  it('shows the stock message on a 409 and does not close', async () => {
    vi.mocked(registerExternalSale).mockRejectedValueOnce(new Error('Stock insuficiente para Rojo / M'));
    const user = userEvent.setup();
    const { onClose } = renderDrawer();
    await addTheProduct(user);
    await user.type(screen.getByLabelText(/^comprador$/i), 'Ana');
    await user.type(screen.getByLabelText(/contacto/i), '@ana');
    await user.click(screen.getByRole('button', { name: /retiro en persona/i }));
    await user.click(screen.getByRole('button', { name: /^registrar venta$/i }));

    expect(await screen.findByText(/stock insuficiente/i)).toBeInTheDocument();
    expect(onClose).not.toHaveBeenCalled();
  });

  it('closes and calls onCreated on success', async () => {
    const user = userEvent.setup();
    const { onClose, onCreated } = renderDrawer();
    await addTheProduct(user);
    await user.type(screen.getByLabelText(/^comprador$/i), 'Ana');
    await user.type(screen.getByLabelText(/contacto/i), '@ana');
    await user.click(screen.getByRole('button', { name: /retiro en persona/i }));
    await user.click(screen.getByRole('button', { name: /^registrar venta$/i }));

    await waitFor(() => expect(onCreated).toHaveBeenCalled());
    expect(onClose).toHaveBeenCalled();
    expect(registerExternalSale).toHaveBeenCalledWith(
      expect.objectContaining({ deliveryMethod: 'PICKUP', buyerName: 'Ana', salesChannel: 'INSTAGRAM' }),
      't',
    );
  });
});
