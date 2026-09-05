import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';
import PublicacionesPage from '../PublicacionesPage';
import { getPublicationBatches } from '../../../lib/api';

vi.mock('../../../lib/api', () => ({
  getPublicationBatches: vi.fn().mockResolvedValue([]),
  getPublicationBatchDetail: vi.fn(),
  retryBatchFailed: vi.fn(),
  retryPublication: vi.fn(),
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
});
