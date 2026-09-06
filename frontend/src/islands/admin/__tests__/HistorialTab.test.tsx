import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';
import HistorialTab from '../HistorialTab';
import {
  getPublicationBatches,
  getPublicationBatchDetail,
  retryBatchFailed,
  cancelBatch,
} from '../../../lib/api';

vi.mock('../../../lib/api', () => ({
  getPublicationBatches: vi.fn(),
  getPublicationBatchDetail: vi.fn(),
  retryBatchFailed: vi.fn(),
  retryPublication: vi.fn(),
  cancelBatch: vi.fn(),
  rescheduleBatch: vi.fn(),
}));
vi.mock('../../../lib/authStore', () => ({
  useAuthStore: () => ({ token: 't' }),
  readAuthTokenCookie: () => 't',
}));

const summary = {
  batchId: 'b1',
  campaignLabel: 'Liquidacion primavera',
  createdAt: new Date().toISOString(),
  platforms: ['INSTAGRAM', 'FACEBOOK'] as Array<'INSTAGRAM' | 'FACEBOOK'>,
  total: 2,
  published: 1,
  failed: 1,
  scheduled: 0,
  pending: 0,
  retrying: 0,
  scheduledAt: null,
};
const detail = {
  batchId: 'b1',
  campaignLabel: 'Liquidacion primavera',
  captionTemplate: '{producto}',
  hashtags: ['#pilarestilo'],
  createdAt: summary.createdAt,
  productIds: ['p1'],
  scheduledAt: null,
  rows: [
    {
      publicationId: 'pub1',
      productId: 'p1',
      productName: 'Chaqueta',
      thumbnailUrl: 'https://img/x.jpg',
      platform: 'INSTAGRAM' as const,
      status: 'PUBLISHED',
      externalPermalink: 'https://www.instagram.com/p/ABC/',
      lastErrorCode: null,
      lastErrorMessage: null,
      imageUrls: ['https://img/a.jpg', 'https://img/b.jpg', 'https://img/c.jpg'],
      retryCount: 0,
      nextAttemptAt: null,
    },
    {
      publicationId: 'pub2',
      productId: 'p1',
      productName: 'Chaqueta',
      thumbnailUrl: 'https://img/x.jpg',
      platform: 'FACEBOOK' as const,
      status: 'FAILED',
      externalPermalink: null,
      lastErrorCode: 'FACEBOOK_PUBLISH_ERROR',
      lastErrorMessage: 'OAuthException 190',
      imageUrls: ['https://img/a.jpg', 'https://img/b.jpg', 'https://img/c.jpg'],
      retryCount: 0,
      nextAttemptAt: null,
    },
  ],
};

beforeEach(() => {
  vi.mocked(getPublicationBatches).mockResolvedValue([summary] as never);
  vi.mocked(getPublicationBatchDetail).mockResolvedValue(detail as never);
  vi.mocked(retryBatchFailed).mockResolvedValue(detail as never);
});

describe('HistorialTab', () => {
  it('renders a batch card with its status summary', async () => {
    render(<HistorialTab onRepublish={vi.fn()} onGoToPublish={vi.fn()} onEditScheduled={vi.fn()} />);
    expect(await screen.findByText('Liquidacion primavera')).toBeInTheDocument();
    expect(screen.getByText(/1 publicados/i)).toBeInTheDocument();
    expect(screen.getByText(/1 fallidos/i)).toBeInTheDocument();
  });

  it('shows an empty state with a button to Publicar', async () => {
    vi.mocked(getPublicationBatches).mockResolvedValue([]);
    const onGoToPublish = vi.fn();
    render(<HistorialTab onRepublish={vi.fn()} onGoToPublish={onGoToPublish} onEditScheduled={vi.fn()} />);
    const btn = await screen.findByRole('button', { name: /ir a publicar/i });
    await userEvent.setup().click(btn);
    expect(onGoToPublish).toHaveBeenCalled();
  });

  it('expands to rows and reveals the error detail on a failed row', async () => {
    const user = userEvent.setup();
    render(<HistorialTab onRepublish={vi.fn()} onGoToPublish={vi.fn()} onEditScheduled={vi.fn()} />);
    await user.click(await screen.findByRole('button', { name: /liquidacion primavera/i }));

    expect((await screen.findAllByText('Chaqueta')).length).toBe(2);

    await user.click(screen.getByRole('button', { name: /ver detalle/i }));
    expect(screen.getByText(/OAuthException 190/)).toBeInTheDocument();
  });

  it('marks a row as a carousel when it has more than one image', async () => {
    const user = userEvent.setup();
    render(<HistorialTab onRepublish={vi.fn()} onGoToPublish={vi.fn()} onEditScheduled={vi.fn()} />);
    await user.click(await screen.findByRole('button', { name: /liquidacion primavera/i }));
    expect((await screen.findAllByText(/carrusel · 3/i)).length).toBeGreaterThan(0);
  });

  it('"Editar" on a scheduled batch carries imageSelections from the rows', async () => {
    const s = { ...summary, published: 0, failed: 0, scheduled: 1, scheduledAt: '2027-06-15T14:00:00.000Z' };
    const d = {
      ...detail,
      scheduledAt: '2027-06-15T14:00:00.000Z',
      rows: [{ ...detail.rows[0], status: 'SCHEDULED', externalPermalink: null }],
    };
    vi.mocked(getPublicationBatches).mockResolvedValue([s] as never);
    vi.mocked(getPublicationBatchDetail).mockResolvedValue(d as never);
    const onEditScheduled = vi.fn();
    const user = userEvent.setup();
    render(<HistorialTab onRepublish={vi.fn()} onGoToPublish={vi.fn()} onEditScheduled={onEditScheduled} />);
    await user.click(await screen.findByRole('button', { name: /liquidacion primavera/i }));
    await user.click(await screen.findByRole('button', { name: /^editar$/i }));
    expect(onEditScheduled).toHaveBeenCalledWith(
      expect.any(String),
      expect.objectContaining({ imageSelections: { p1: ['https://img/a.jpg', 'https://img/b.jpg', 'https://img/c.jpg'] } }),
    );
  });

  it('shows a Reintentando pill with the attempt number and a retrying count', async () => {
    const s = { ...summary, published: 0, failed: 0, retrying: 1 };
    const d = {
      ...detail,
      rows: [{
        ...detail.rows[1],
        status: 'RETRY_SCHEDULED',
        retryCount: 2,
        nextAttemptAt: '2027-06-15T14:35:00.000Z',
      }],
    };
    vi.mocked(getPublicationBatches).mockResolvedValue([s] as never);
    vi.mocked(getPublicationBatchDetail).mockResolvedValue(d as never);

    const user = userEvent.setup();
    render(<HistorialTab onRepublish={vi.fn()} onGoToPublish={vi.fn()} onEditScheduled={vi.fn()} />);
    expect(await screen.findByText(/1 reintentando/i)).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /liquidacion primavera/i }));
    expect(await screen.findByText(/Reintentando · intento 2/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /reintentar ahora/i })).toBeInTheDocument();
  });

  it('a published row links to the live post', async () => {
    const user = userEvent.setup();
    render(<HistorialTab onRepublish={vi.fn()} onGoToPublish={vi.fn()} onEditScheduled={vi.fn()} />);
    await user.click(await screen.findByRole('button', { name: /liquidacion primavera/i }));
    const link = await screen.findByRole('link', { name: /ver en instagram/i });
    expect(link).toHaveAttribute('href', 'https://www.instagram.com/p/ABC/');
    expect(link).toHaveAttribute('target', '_blank');
  });

  it('retry-failed calls the endpoint and re-renders from its response', async () => {
    const user = userEvent.setup();
    render(<HistorialTab onRepublish={vi.fn()} onGoToPublish={vi.fn()} onEditScheduled={vi.fn()} />);
    await user.click(await screen.findByRole('button', { name: /liquidacion primavera/i }));
    await user.click(await screen.findByRole('button', { name: /reintentar fallidos/i }));
    await waitFor(() => expect(retryBatchFailed).toHaveBeenCalledWith('b1', 't'));
  });

  it('editar y volver a publicar calls onRepublish with the batch data', async () => {
    const onRepublish = vi.fn();
    const user = userEvent.setup();
    render(<HistorialTab onRepublish={onRepublish} onGoToPublish={vi.fn()} onEditScheduled={vi.fn()} />);
    await user.click(await screen.findByRole('button', { name: /liquidacion primavera/i }));
    await user.click(await screen.findByRole('button', { name: /editar y volver a publicar/i }));
    expect(onRepublish).toHaveBeenCalledWith(
      expect.objectContaining({
        productIds: ['p1'],
        captionTemplate: '{producto}',
        campaignLabel: 'Liquidacion primavera',
      }),
    );
  });

  it('shows a scheduled batch as "Programada para" with cancel / reschedule / edit', async () => {
    const s = { ...summary, published: 0, failed: 0, scheduled: 1, scheduledAt: '2027-06-15T14:00:00.000Z' };
    const d = {
      ...detail,
      scheduledAt: '2027-06-15T14:00:00.000Z',
      rows: [{ ...detail.rows[0], status: 'SCHEDULED', externalPermalink: null }],
    };
    vi.mocked(getPublicationBatches).mockResolvedValue([s] as never);
    vi.mocked(getPublicationBatchDetail).mockResolvedValue(d as never);
    vi.mocked(cancelBatch).mockResolvedValue({ ...d, rows: [{ ...d.rows[0], status: 'CANCELLED' }] } as never);

    const user = userEvent.setup();
    render(<HistorialTab onRepublish={vi.fn()} onGoToPublish={vi.fn()} onEditScheduled={vi.fn()} />);
    expect(await screen.findByText(/programada para/i)).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /liquidacion primavera/i }));
    await user.click(await screen.findByRole('button', { name: /cancelar programaci/i }));
    await waitFor(() => expect(cancelBatch).toHaveBeenCalledWith('b1', 't'));
  });

  it('"Editar" on a scheduled batch calls onEditScheduled with the batch data', async () => {
    const s = { ...summary, published: 0, failed: 0, scheduled: 1, scheduledAt: '2027-06-15T14:00:00.000Z' };
    const d = {
      ...detail,
      scheduledAt: '2027-06-15T14:00:00.000Z',
      rows: [{ ...detail.rows[0], status: 'SCHEDULED', externalPermalink: null }],
    };
    vi.mocked(getPublicationBatches).mockResolvedValue([s] as never);
    vi.mocked(getPublicationBatchDetail).mockResolvedValue(d as never);
    const onEditScheduled = vi.fn();

    const user = userEvent.setup();
    render(<HistorialTab onRepublish={vi.fn()} onGoToPublish={vi.fn()} onEditScheduled={onEditScheduled} />);
    await user.click(await screen.findByRole('button', { name: /liquidacion primavera/i }));
    await user.click(await screen.findByRole('button', { name: /^editar$/i }));

    expect(onEditScheduled).toHaveBeenCalledWith('b1', expect.objectContaining({
      productIds: ['p1'],
      captionTemplate: '{producto}',
      hashtags: ['#pilarestilo'],
      campaignLabel: 'Liquidacion primavera',
      scheduledAt: '2027-06-15T14:00:00.000Z',
    }));
  });
});
