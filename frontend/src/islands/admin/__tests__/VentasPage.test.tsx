import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';
import VentasPage from '../VentasPage';
import type { SaleSummaryDto, Page } from '../../../lib/api';

/**
 * VentasPage's own DataTable already supports sortable columns -- these tests cover the wiring
 * that turns a header click into a real server-side sort (getAdminSales's sortKey/sortDir), not
 * just a client-side reorder of the one page already on screen.
 */

const getAdminSales = vi.fn();

vi.mock('../../../lib/api', async () => {
  const actual = await vi.importActual<typeof import('../../../lib/api')>('../../../lib/api');
  return {
    ...actual,
    getAdminSales: (...a: unknown[]) => getAdminSales(...a),
  };
});

vi.mock('../../../lib/authStore', () => ({
  useAuthStore: Object.assign(
    (selector?: (state: { token: string; user: { role: string } }) => unknown) => {
      const state = { token: 'tok', user: { role: 'ADMIN' } };
      return selector ? selector(state) : state;
    },
    { getState: () => ({ token: 'tok', user: { role: 'ADMIN' } }) },
  ),
  readAuthTokenCookie: () => 'tok',
}));

function sale(overrides: Partial<SaleSummaryDto> = {}): SaleSummaryDto {
  return {
    orderId: 'order-1',
    publicReference: 'PE-0001',
    createdAt: '2026-09-01T10:00:00Z',
    orderStatus: 'PAID',
    customerName: 'Ana Perez',
    customerEmail: 'ana@correo.cl',
    totalAmount: 20000,
    netAmount: 16807,
    taxAmount: 3193,
    currency: 'CLP',
    paymentMethod: 'TRANSFER',
    paymentStatus: 'APPROVED',
    paymentGatewayFlag: null,
    documentId: null,
    documentFolio: null,
    itemCount: 1,
    firstItemName: 'Vestido rosa',
    ...overrides,
  } as SaleSummaryDto;
}

function page(rows: SaleSummaryDto[]): Page<SaleSummaryDto> {
  return { content: rows, totalElements: rows.length, totalPages: 1, size: 20, number: 0 };
}

beforeEach(() => {
  vi.clearAllMocks();
  getAdminSales.mockResolvedValue(page([sale()]));
});

describe('VentasPage: sorting', () => {
  it('loads with no sort applied', async () => {
    render(<VentasPage />);
    await waitFor(() => expect(getAdminSales).toHaveBeenCalled());
    const [params] = getAdminSales.mock.calls[0];
    expect(params.sortKey).toBeUndefined();
  });

  it('sorts by total descending on the first click, ascending on the second', async () => {
    const user = userEvent.setup();
    render(<VentasPage />);
    await screen.findByText('PE-0001');

    await user.click(screen.getByRole('columnheader', { name: /total/i }));
    await waitFor(() => {
      const last = getAdminSales.mock.calls.at(-1)?.[0];
      expect(last).toMatchObject({ sortKey: 'totalAmount', sortDir: 'desc' });
    });

    await user.click(screen.getByRole('columnheader', { name: /total/i }));
    await waitFor(() => {
      const last = getAdminSales.mock.calls.at(-1)?.[0];
      expect(last).toMatchObject({ sortKey: 'totalAmount', sortDir: 'asc' });
    });
  });

  it('switching to a different sortable column resets to descending', async () => {
    const user = userEvent.setup();
    render(<VentasPage />);
    await screen.findByText('PE-0001');

    await user.click(screen.getByRole('columnheader', { name: /total/i }));
    await waitFor(() => {
      expect(getAdminSales.mock.calls.at(-1)?.[0]).toMatchObject({ sortKey: 'totalAmount' });
    });

    await user.click(screen.getByRole('columnheader', { name: /fecha/i }));
    await waitFor(() => {
      expect(getAdminSales.mock.calls.at(-1)?.[0]).toMatchObject({ sortKey: 'createdAt', sortDir: 'desc' });
    });
  });
});
