import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';
import PublicacionesPage from '../PublicacionesPage';
import {
  searchProducts,
  publishProductsBatch,
  uploadMediaFile,
  getProductPublicationImageHistory,
} from '../../../lib/api';

vi.mock('../../../lib/api', () => ({
  searchProducts: vi.fn(),
  publishProductsBatch: vi.fn(),
  uploadMediaFile: vi.fn(),
  getProductPublicationImageHistory: vi.fn(),
}));

vi.mock('../../../lib/authStore', () => ({
  useAuthStore: () => ({ token: 't' }),
  readAuthTokenCookie: () => 't',
}));

beforeEach(() => {
  vi.mocked(searchProducts).mockResolvedValue({
    content: [
      {
        id: 'p1',
        name: 'Chaqueta',
        price: { amount: 49990, currency: 'CLP' },
        imageUrl: 'https://img/chaqueta.jpg',
      } as never,
    ],
    totalElements: 1,
    totalPages: 1,
    size: 24,
    number: 0,
  } as never);
  vi.mocked(publishProductsBatch).mockResolvedValue({
    items: [
      { productId: 'p1', platform: 'INSTAGRAM', success: true, publicationId: 'pub-1', errorMessage: null },
      { productId: 'p1', platform: 'FACEBOOK', success: false, publicationId: null, errorMessage: 'Credenciales no configuradas' },
    ],
  } as never);
  vi.mocked(getProductPublicationImageHistory).mockResolvedValue([]);
  vi.mocked(uploadMediaFile).mockResolvedValue('https://img/edited.jpg');
});

async function selectTheProduct(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByPlaceholderText(/buscar producto/i), 'cha');
  const hit = await screen.findByRole('button', { name: /chaqueta/i });
  await user.click(hit);
}

describe('PublicacionesPage', () => {
  it('interpolates the caption template in the preview', async () => {
    const user = userEvent.setup();
    render(<PublicacionesPage />);
    await selectTheProduct(user);

    expect(await screen.findByText(/chaqueta a solo \$49\.990/i)).toBeInTheDocument();
  });

  it('publishes the batch and renders a mixed result', async () => {
    const user = userEvent.setup();
    render(<PublicacionesPage />);
    await selectTheProduct(user);

    await user.click(screen.getByRole('button', { name: /publicar ahora/i }));

    await waitFor(() => expect(publishProductsBatch).toHaveBeenCalled());
    expect(await screen.findByText(/credenciales no configuradas/i)).toBeInTheDocument();
  });

  it('disables the publish button until a product is selected', () => {
    render(<PublicacionesPage />);
    expect(screen.getByRole('button', { name: /publicar ahora/i })).toBeDisabled();
  });

  it('shows the catalog on focus, before typing anything', async () => {
    const user = userEvent.setup();
    render(<PublicacionesPage />);

    expect(screen.queryByRole('button', { name: /chaqueta/i })).not.toBeInTheDocument();
    await user.click(screen.getByPlaceholderText(/buscar producto/i));

    expect(await screen.findByRole('button', { name: /chaqueta/i })).toBeInTheDocument();
    expect(searchProducts).toHaveBeenCalledWith({ q: '', page: 0, size: 24 }, 0, 24);
  });

  it('shows a chosen product as a chip right under the search box', async () => {
    const user = userEvent.setup();
    render(<PublicacionesPage />);
    await selectTheProduct(user);

    expect(screen.getByText('1 producto(s) elegido(s)')).toBeInTheDocument();
    const chip = screen.getByRole('button', { name: /quitar chaqueta/i });
    expect(chip).toBeInTheDocument();

    await user.click(chip);
    expect(screen.queryByText(/producto\(s\) elegido/i)).not.toBeInTheDocument();
  });

  it('uploads an edited photo and sends it as the override for that product', async () => {
    const user = userEvent.setup();
    render(<PublicacionesPage />);
    await selectTheProduct(user);

    const file = new File(['fake'], 'edited.jpg', { type: 'image/jpeg' });
    const input = screen.getByLabelText(/subir foto editada/i);
    await user.upload(input, file);

    await waitFor(() => expect(uploadMediaFile).toHaveBeenCalledWith(file, 'publications', 't'));
    expect(await screen.findByText(/reemplazar foto editada/i)).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /publicar ahora/i }));
    await waitFor(() =>
      expect(publishProductsBatch).toHaveBeenCalledWith(
        expect.objectContaining({ imageOverrides: { p1: 'https://img/edited.jpg' } }),
        't',
      ),
    );
  });

  it('offers previously used photos for the product and reuses one on click', async () => {
    vi.mocked(getProductPublicationImageHistory).mockResolvedValue(['https://img/old-edit.jpg']);
    const user = userEvent.setup();
    render(<PublicacionesPage />);
    await selectTheProduct(user);

    const reuseButton = await screen.findByRole('button', { name: /reusar esta foto/i });
    await user.click(reuseButton);

    await user.click(screen.getByRole('button', { name: /publicar ahora/i }));
    await waitFor(() =>
      expect(publishProductsBatch).toHaveBeenCalledWith(
        expect.objectContaining({ imageOverrides: { p1: 'https://img/old-edit.jpg' } }),
        't',
      ),
    );
  });
});
