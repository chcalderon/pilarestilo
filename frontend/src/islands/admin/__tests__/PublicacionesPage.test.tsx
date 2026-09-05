import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';
import PublicacionesPage from '../PublicacionesPage';
import { getPublicationBatches, getPublicationBatchDetail } from '../../../lib/api';

vi.mock('../../../lib/api', () => ({
  getPublicationBatches: vi.fn().mockResolvedValue([]),
  getPublicationBatchDetail: vi.fn(),
  retryBatchFailed: vi.fn(),
  retryPublication: vi.fn(),
  cancelBatch: vi.fn(),
  rescheduleBatch: vi.fn(),
  updateScheduledBatch: vi.fn(),
  searchProducts: vi.fn().mockResolvedValue({ content: [], totalElements: 0, totalPages: 0, size: 24, number: 0 }),
  publishProductsBatch: vi.fn(),
  uploadMediaFile: vi.fn(),
  getProduct: vi.fn(),
  getProductPublicationImageHistory: vi.fn().mockResolvedValue([]),
}));
vi.mock('../../../lib/authStore', () => ({
  useAuthStore: () => ({ token: 't' }),
  readAuthTokenCookie: () => 't',
}));

beforeEach(() => {
  window.history.replaceState({}, '', '/admin/publicaciones');
  vi.mocked(getPublicationBatches).mockResolvedValue([]);
});

describe('PublicacionesPage shell', () => {
  it('shows the Publicar tab by default', () => {
    render(<PublicacionesPage />);
    expect(screen.getByRole('tab', { name: /publicar/i })).toHaveAttribute('aria-selected', 'true');
    expect(screen.getByPlaceholderText(/buscar producto/i)).toBeInTheDocument();
  });

  it('switches to Historial and writes ?tab=historial', async () => {
    const user = userEvent.setup();
    render(<PublicacionesPage />);
    await user.click(screen.getByRole('tab', { name: /historial/i }));
    expect(screen.getByRole('tab', { name: /historial/i })).toHaveAttribute('aria-selected', 'true');
    expect(new URLSearchParams(window.location.search).get('tab')).toBe('historial');
  });

  it('opens on Historial when the URL says so', () => {
    window.history.replaceState({}, '', '/admin/publicaciones?tab=historial');
    render(<PublicacionesPage />);
    expect(screen.getByRole('tab', { name: /historial/i })).toHaveAttribute('aria-selected', 'true');
  });

  it('editing a scheduled batch from Historial opens Publicar in edit mode', async () => {
    const scheduled = {
      batchId: 'bs', campaignLabel: 'Prog', createdAt: new Date().toISOString(),
      platforms: ['INSTAGRAM'], total: 1, published: 0, failed: 0, scheduled: 1, pending: 0,
      scheduledAt: '2027-06-15T14:00:00.000Z',
    };
    vi.mocked(getPublicationBatches).mockResolvedValue([scheduled] as never);
    vi.mocked(getPublicationBatchDetail).mockResolvedValue({
      batchId: 'bs', campaignLabel: 'Prog', captionTemplate: '{producto}', hashtags: [],
      createdAt: scheduled.createdAt, productIds: [], scheduledAt: '2027-06-15T14:00:00.000Z',
      rows: [{ publicationId: 'p', productId: null, productName: 'X', thumbnailUrl: null,
        platform: 'INSTAGRAM', status: 'SCHEDULED', externalPermalink: null,
        lastErrorCode: null, lastErrorMessage: null }],
    } as never);

    const user = userEvent.setup();
    window.history.replaceState({}, '', '/admin/publicaciones?tab=historial');
    render(<PublicacionesPage />);
    await user.click(await screen.findByRole('button', { name: /prog/i }));
    await user.click(await screen.findByRole('button', { name: /^editar$/i }));

    expect(new URLSearchParams(window.location.search).get('tab')).toBe('publicar');
    expect(await screen.findByRole('button', { name: /guardar cambios/i })).toBeInTheDocument();
  });
});
