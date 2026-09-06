import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';
import CampanasTab from '../CampanasTab';
import { getCampaigns, getCampaignDetail, refreshCampaignMetrics } from '../../../lib/api';

vi.mock('../../../lib/api', () => ({
  getCampaigns: vi.fn(),
  getCampaignDetail: vi.fn(),
  refreshCampaignMetrics: vi.fn(),
}));
vi.mock('../../../lib/authStore', () => ({
  useAuthStore: () => ({ token: 't' }),
  readAuthTokenCookie: () => 't',
}));

const summary = {
  label: 'Verano', firstPostAt: '2026-09-01T00:00:00Z', lastPostAt: '2026-09-03T00:00:00Z',
  batchCount: 2, totalPosts: 4, published: 3, failed: 1, scheduled: 0,
  platforms: ['INSTAGRAM', 'FACEBOOK'] as const,
  totals: { impressions: 1234, reach: 900, likes: 87, comments: 5, shares: 2, saved: 10 },
  postsWithError: 1,
};

const detail = {
  label: 'Verano', firstPostAt: '2026-09-01T00:00:00Z', lastPostAt: '2026-09-03T00:00:00Z',
  posts: [
    {
      publicationId: 'p1', productId: 'x', productName: 'Vestido', thumbnailUrl: null,
      platform: 'INSTAGRAM' as const, status: 'PUBLISHED', externalPermalink: 'https://instagram.com/p/A/',
      metrics: { impressions: 500, reach: 400, likes: 40, comments: 2, shares: 1, saved: 6 },
      fetchError: null, fetchedAt: '2026-09-04T06:00:00Z',
    },
    {
      publicationId: 'p2', productId: 'x', productName: 'Vestido', thumbnailUrl: null,
      platform: 'FACEBOOK' as const, status: 'PUBLISHED', externalPermalink: null,
      metrics: null, fetchError: '403 forbidden', fetchedAt: '2026-09-04T06:00:00Z',
    },
  ],
};

beforeEach(() => {
  vi.mocked(getCampaigns).mockResolvedValue([summary] as never);
  vi.mocked(getCampaignDetail).mockResolvedValue(detail as never);
  vi.mocked(refreshCampaignMetrics).mockResolvedValue({ refreshed: 1, failed: 1 });
});

describe('CampanasTab', () => {
  it('lists campaigns with headline metrics', async () => {
    render(<CampanasTab />);
    expect(await screen.findByText('Verano')).toBeInTheDocument();
    expect(screen.getByText(/impresiones/i)).toBeInTheDocument();
  });

  it('expands to per-post rows on click', async () => {
    const user = userEvent.setup();
    render(<CampanasTab />);
    await user.click(await screen.findByRole('button', { name: /verano/i }));
    expect(await screen.findByText('No disponible')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /ver en instagram/i })).toBeInTheDocument();
  });

  it('refreshes metrics and re-fetches', async () => {
    const user = userEvent.setup();
    render(<CampanasTab />);
    await screen.findByText('Verano');
    await user.click(screen.getByRole('button', { name: /actualizar métricas/i }));
    expect(refreshCampaignMetrics).toHaveBeenCalledWith('Verano', 't');
    expect(vi.mocked(getCampaigns).mock.calls.length).toBeGreaterThanOrEqual(2);
  });

  it('shows an empty state when there are no campaigns', async () => {
    vi.mocked(getCampaigns).mockResolvedValue([]);
    render(<CampanasTab />);
    expect(await screen.findByText(/aún no hay campañas/i)).toBeInTheDocument();
  });
});
