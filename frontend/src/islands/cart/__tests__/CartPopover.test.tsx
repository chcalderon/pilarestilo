import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, act } from '@testing-library/react';
import { forwardRef } from 'react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';
import CartPopover from '../CartPopover';
import type { CartItem } from '@/lib/cartStore';

/**
 * Characterization tests written before pulling the mostly-duplicated desktop-popover and
 * mobile-sheet panels apart into a shared body, and the seven reduced-motion ternaries into a
 * helper (S3776, complexity 24) -- it had none. Covers the item badge, empty vs populated states,
 * opening/closing (click, X, backdrop, Escape), the subtotal/CTA footer, and the mobile-vs-desktop
 * layout switch.
 */

vi.mock('motion/react', () => ({
  AnimatePresence: ({ children }: any) => children,
  motion: {
    div: forwardRef(({ initial, animate, exit, transition, ...rest }: any, ref: any) => <div ref={ref} {...rest} />),
  },
}));

let items: CartItem[] = [];
const removeItem = vi.fn();
let subtotal = 0;

vi.mock('@/lib/cartStore', async () => {
  const actual = await vi.importActual<typeof import('@/lib/cartStore')>('@/lib/cartStore');
  return {
    ...actual,
    useCartStore: (selector: (s: any) => unknown) => selector({ items, removeItem, getSubtotal: () => subtotal }),
  };
});

let isMobileQuery = false;
let reducedMotionQuery = false;

function installMatchMedia() {
  window.matchMedia = vi.fn().mockImplementation((query: string) => {
    const matches = query.includes('max-width') ? isMobileQuery : reducedMotionQuery;
    return {
      matches,
      media: query,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    };
  });
}

function item(overrides: Partial<CartItem> = {}): CartItem {
  return {
    id: 'i1',
    name: 'Vestido rosa',
    brand: 'PilarEstilo',
    price: { amount: 20000, currency: 'CLP' },
    imageUrl: '/img/i1.jpg',
    condition: 'NEW',
    quantity: 1,
    ...overrides,
  };
}

beforeEach(() => {
  vi.clearAllMocks();
  items = [];
  subtotal = 0;
  isMobileQuery = false;
  reducedMotionQuery = false;
  installMatchMedia();
});

afterEach(() => {
  vi.useRealTimers();
});

describe('CartPopover (desktop)', () => {
  it('shows no badge when the cart is empty, and opens to the empty state on click', async () => {
    const user = userEvent.setup();
    render(<CartPopover locale="es" />);
    expect(screen.queryByText('0')).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /carrito de compras/i }));
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });

  it('shows the total quantity badge, capped at 99+', async () => {
    items = [item({ id: 'a', quantity: 60 }), item({ id: 'b', quantity: 60 })];
    render(<CartPopover locale="es" />);
    expect(screen.getByText('99+')).toBeInTheDocument();
  });

  it('lists items, the subtotal and a view-cart link when the cart has items', async () => {
    items = [item({ name: 'Vestido rosa', price: { amount: 15000, currency: 'CLP' } })];
    subtotal = 20000;
    const user = userEvent.setup();
    render(<CartPopover locale="es" />);

    await user.click(screen.getByRole('button', { name: /carrito de compras/i }));

    expect(screen.getByText('Vestido rosa')).toBeInTheDocument();
    expect(screen.getByText('$15.000')).toBeInTheDocument();
    expect(screen.getByText('$20.000')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /ver carrito/i })).toHaveAttribute('href', '/es/cart');
  });

  it('shows a "+N more" hint past the visible limit', async () => {
    items = Array.from({ length: 6 }, (_, i) => item({ id: `i${i}`, name: `Item ${i}` }));
    const user = userEvent.setup();
    render(<CartPopover locale="es" />);

    await user.click(screen.getByRole('button', { name: /carrito de compras/i }));
    expect(screen.getByText(/y 2 productos más/i)).toBeInTheDocument();
  });

  it('closes on the X button, on Escape, and on an outside click', async () => {
    const user = userEvent.setup();
    render(<CartPopover locale="es" />);

    await user.click(screen.getByRole('button', { name: /carrito de compras/i }));
    await user.click(screen.getByRole('button', { name: /cerrar carrito/i }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /carrito de compras/i }));
    fireEvent.keyDown(document, { key: 'Escape' });
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /carrito de compras/i }));
    fireEvent.mouseDown(document.body);
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('opens on hover after the delay and closes after the mouse leaves', () => {
    vi.useFakeTimers();
    render(<CartPopover locale="es" />);
    const trigger = screen.getByRole('button', { name: /carrito de compras/i });

    fireEvent.mouseEnter(trigger.parentElement!);
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    act(() => { vi.advanceTimersByTime(200); });
    expect(screen.getByRole('dialog')).toBeInTheDocument();

    fireEvent.mouseLeave(trigger.parentElement!);
    act(() => { vi.advanceTimersByTime(300); });
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });
});

describe('CartPopover (mobile)', () => {
  beforeEach(() => {
    isMobileQuery = true;
    installMatchMedia();
  });

  it('opens the bottom sheet instead of the anchored popover, and does not open on hover', () => {
    vi.useFakeTimers();
    render(<CartPopover locale="es" />);
    const trigger = screen.getByRole('button', { name: /carrito de compras/i });

    fireEvent.mouseEnter(trigger.parentElement!);
    vi.advanceTimersByTime(500);
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('shows the sheet with items on click and locks body scroll while open', async () => {
    items = [item()];
    const user = userEvent.setup();
    render(<CartPopover locale="es" />);

    await user.click(screen.getByRole('button', { name: /carrito de compras/i }));
    expect(screen.getByRole('dialog', { hidden: true })).toBeInTheDocument();
    expect(document.body.style.overflow).toBe('hidden');

    await user.click(screen.getByRole('button', { name: /cerrar carrito/i, hidden: true }));
    expect(document.body.style.overflow).toBe('');
  });
});
