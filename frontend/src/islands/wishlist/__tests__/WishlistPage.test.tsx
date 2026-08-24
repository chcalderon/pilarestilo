import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';
import WishlistPage from '../WishlistPage';
import type { ProductDto } from '@/lib/api';

/**
 * Characterization tests written before pulling the share panel out of this component (S3776,
 * complexity 27) -- it had none. Covers the empty/loading/populated states, the discount-price
 * strike-through, moving an item to the cart, and the enable/copy/disable share-link flow.
 */

const getProduct = vi.fn();
const getWishlistShareLink = vi.fn();
const enableWishlistShareLink = vi.fn();
const disableWishlistShareLink = vi.fn();

vi.mock('@/lib/api', async () => {
  const actual = await vi.importActual<typeof import('@/lib/api')>('@/lib/api');
  return {
    ...actual,
    getProduct: (...args: unknown[]) => getProduct(...args),
    getWishlistShareLink: (...args: unknown[]) => getWishlistShareLink(...args),
    enableWishlistShareLink: (...args: unknown[]) => enableWishlistShareLink(...args),
    disableWishlistShareLink: (...args: unknown[]) => disableWishlistShareLink(...args),
  };
});

const remove = vi.fn().mockResolvedValue(undefined);
const syncFromServer = vi.fn().mockResolvedValue(undefined);
let productIds = new Set<string>();

vi.mock('@/lib/wishlistStore', () => ({
  useWishlistStore: () => ({ productIds, remove, syncFromServer }),
}));

const addItem = vi.fn();

vi.mock('@/lib/cartStore', () => ({
  useCartStore: (selector: (s: { addItem: typeof addItem }) => unknown) => selector({ addItem }),
}));

function product(overrides: Partial<ProductDto> = {}): ProductDto {
  return {
    id: 'p1',
    name: 'Vestido rosa',
    description: '',
    price: { amount: 20000, currency: 'CLP' },
    imageUrl: '/img/p1.jpg',
    condition: 'NEW',
    brand: 'PilarEstilo',
    stock: 5,
    active: true,
    createdAt: '',
    updatedAt: '',
    ...overrides,
  };
}

beforeEach(() => {
  vi.clearAllMocks();
  productIds = new Set<string>();
  getWishlistShareLink.mockResolvedValue({ token: null, enabled: false });
});

afterEach(() => {
  vi.unstubAllGlobals();
  delete (navigator as any).clipboard;
});

describe('WishlistPage', () => {
  it('shows the empty state with no items', async () => {
    render(<WishlistPage locale="es" />);
    expect(await screen.findByText(/lista de favoritos esta vacia/i)).toBeInTheDocument();
  });

  it('lists products, strikes through the list price on a discount, and moves one to the cart', async () => {
    productIds = new Set(['p1']);
    getProduct.mockResolvedValue(product({
      listPrice: { amount: 30000, currency: 'CLP' },
      price: { amount: 20000, currency: 'CLP' },
    }));
    const user = userEvent.setup();
    render(<WishlistPage locale="es" />);

    expect(await screen.findByText('Vestido rosa')).toBeInTheDocument();
    expect(screen.getByText('$30.000')).toBeInTheDocument();
    expect(screen.getByText('$20.000')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /mover al carrito/i }));
    expect(addItem).toHaveBeenCalledWith(expect.objectContaining({ id: 'p1', name: 'Vestido rosa' }));
  });

  it('removes an item via the X button', async () => {
    productIds = new Set(['p1']);
    getProduct.mockResolvedValue(product());
    const user = userEvent.setup();
    render(<WishlistPage locale="es" token="tok" />);

    await screen.findByText('Vestido rosa');
    await user.click(screen.getByRole('button', { name: /quitar de favoritos/i }));

    expect(remove).toHaveBeenCalledWith('p1', 'tok');
  });

  it('drops a product that fails to load, without crashing', async () => {
    productIds = new Set(['p1', 'p2']);
    getProduct.mockImplementation((id: string) =>
      id === 'p1' ? Promise.resolve(product({ id: 'p1', name: 'Vestido rosa' })) : Promise.reject(new Error('gone'))
    );
    render(<WishlistPage locale="es" />);

    expect(await screen.findByText('Vestido rosa')).toBeInTheDocument();
    expect(screen.queryByText(/lista de favoritos esta vacia/i)).not.toBeInTheDocument();
  });

  it('does not show the share panel without a token', async () => {
    render(<WishlistPage locale="es" />);
    await screen.findByText(/lista de favoritos esta vacia/i);
    expect(screen.queryByText(/wishlist compartible/i)).not.toBeInTheDocument();
  });

  it('enables the share link, shows the url, then disables it', async () => {
    enableWishlistShareLink.mockResolvedValue({ token: 'abc123', enabled: true });
    const user = userEvent.setup();
    render(<WishlistPage locale="es" token="tok" />);

    await screen.findByText(/wishlist compartible/i);
    await user.click(screen.getByRole('button', { name: /activar link/i }));

    expect(await screen.findByText(/wishlist\/shared\/abc123/)).toBeInTheDocument();
    expect(screen.getByText(/link de favoritos activado/i)).toBeInTheDocument();

    disableWishlistShareLink.mockResolvedValue(undefined);
    await user.click(screen.getByRole('button', { name: /desactivar/i }));

    expect(await screen.findByRole('button', { name: /activar link/i })).toBeInTheDocument();
    expect(screen.getByText(/link compartido desactivado/i)).toBeInTheDocument();
  });

  it('copies the share url to the clipboard', async () => {
    getWishlistShareLink.mockResolvedValue({ token: 'abc123', enabled: true });
    const writeText = vi.fn().mockResolvedValue(undefined);
    const user = userEvent.setup();
    render(<WishlistPage locale="es" token="tok" />);
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true });

    await user.click(await screen.findByRole('button', { name: /copiar link/i }));

    expect(writeText).toHaveBeenCalledWith(expect.stringContaining('/wishlist/shared/abc123'));
    expect(await screen.findByText(/link copiado al portapapeles/i)).toBeInTheDocument();
  });
});
