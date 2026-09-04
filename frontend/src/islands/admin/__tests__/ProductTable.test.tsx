import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';
import ProductTable from '../ProductTable';
import type { ProductDto, ProductSearchParams } from '../../../lib/api';

/**
 * "Eliminar" used to hard-delete the row and 500 the moment a product had a real order behind
 * it (a foreign-key violation with no friendly message). It is now a logical delete -- these
 * tests guard the two halves of that: the list defaults to active-only with a discoverable way
 * to reach what got deactivated, and the confirmation copy stops claiming it can't be undone.
 */

function product(overrides: Partial<ProductDto> = {}): ProductDto {
  return {
    id: 'p1',
    name: 'Vestido de prueba',
    description: 'Descripcion',
    price: { amount: 19990, currency: 'CLP' },
    imageUrl: 'https://example.com/img.jpg',
    condition: 'NEW',
    brand: 'Marca',
    stock: 5,
    active: true,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...overrides,
  };
}

const searchProducts = vi.fn(async (_params: string | ProductSearchParams) => ({
  content: [] as ProductDto[],
  totalElements: 0,
  totalPages: 0,
  size: 20,
  number: 0,
}));

vi.mock('../../../lib/api', async () => {
  const actual = await vi.importActual<typeof import('../../../lib/api')>('../../../lib/api');
  return {
    ...actual,
    searchProducts: (params: string | ProductSearchParams) => searchProducts(params),
    getCategories: vi.fn(async () => []),
    deleteProduct: vi.fn(),
  };
});

beforeEach(() => {
  vi.clearAllMocks();
  searchProducts.mockResolvedValue({ content: [], totalElements: 0, totalPages: 0, size: 20, number: 0 });
});

describe('ProductTable: active/inactive filter', () => {
  it('loads only active products by default', async () => {
    render(<ProductTable />);

    await waitFor(() => expect(searchProducts).toHaveBeenCalled());
    const call = searchProducts.mock.calls[0][0] as ProductSearchParams;
    expect(call.active).toBe(true);
  });

  it('switching to the Inactivos tab loads deactivated products', async () => {
    const user = userEvent.setup();
    render(<ProductTable />);
    await waitFor(() => expect(searchProducts).toHaveBeenCalled());

    await user.click(screen.getByRole('tab', { name: 'Inactivos' }));

    await waitFor(() => {
      const lastCall = searchProducts.mock.calls.at(-1)?.[0] as ProductSearchParams;
      expect(lastCall.active).toBe(false);
    });
  });
});

describe('ProductTable: deactivate confirmation', () => {
  it('offers a reversible "Desactivar", not a destructive "Eliminar"', async () => {
    const user = userEvent.setup();
    searchProducts.mockResolvedValue({
      content: [product()],
      totalElements: 1,
      totalPages: 1,
      size: 20,
      number: 0,
    });

    render(<ProductTable />);
    const desactivarButtons = await screen.findAllByRole('button', { name: /desactivar/i });
    await user.click(desactivarButtons[0]);

    const dialog = await screen.findByText('Desactivar producto?');
    const dialogBody = dialog.closest('div')?.parentElement as HTMLElement;
    expect(within(dialogBody).getByText(/reactivarlo editandolo/i)).toBeInTheDocument();
    expect(within(dialogBody).queryByText(/no se puede deshacer/i)).not.toBeInTheDocument();
  });
});
