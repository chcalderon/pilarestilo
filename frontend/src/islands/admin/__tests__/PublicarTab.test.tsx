import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';
import PublicarTab from '../PublicarTab';
import {
  searchProducts,
  publishProductsBatch,
  updateScheduledBatch,
  uploadMediaFile,
  getProduct,
  getProductPublicationImageHistory,
} from '../../../lib/api';

vi.mock('../../../lib/api', () => ({
  searchProducts: vi.fn(),
  publishProductsBatch: vi.fn(),
  updateScheduledBatch: vi.fn(),
  uploadMediaFile: vi.fn(),
  getProduct: vi.fn(),
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
      { productId: 'p1', platform: 'INSTAGRAM', success: false, publicationId: 'pub-1', errorMessage: null, scheduled: false },
      { productId: 'p1', platform: 'FACEBOOK', success: false, publicationId: 'pub-2', errorMessage: null, scheduled: false },
    ],
  } as never);
  vi.mocked(getProductPublicationImageHistory).mockResolvedValue([]);
  vi.mocked(uploadMediaFile).mockResolvedValue('https://img/edited.jpg');
  vi.mocked(updateScheduledBatch).mockResolvedValue({} as never);
  vi.mocked(getProduct).mockResolvedValue({
    id: 'p1', name: 'Chaqueta', price: { amount: 49990, currency: 'CLP' }, imageUrl: 'https://img/chaqueta.jpg',
  } as never);
});

async function selectTheProduct(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByPlaceholderText(/buscar producto/i), 'cha');
  const hit = await screen.findByRole('button', { name: /chaqueta/i });
  await user.click(hit);
}

const productWithVariants = {
  id: 'p2',
  name: 'Zapatos',
  price: { amount: 29990, currency: 'CLP' },
  imageUrl: 'https://img/zapatos.jpg',
  variants: [
    { color: 'Negro', size: '38', stock: 5, stockOnHand: 5, stockReserved: 1, stockAvailable: 4 },
    { color: 'Negro', size: '39', stock: 3, stockOnHand: 3, stockReserved: 0, stockAvailable: 3 },
    { color: 'Blanco', size: '38', stock: 2, stockOnHand: 2, stockReserved: 0, stockAvailable: 2 },
  ],
} as never;

async function selectZapatos(user: ReturnType<typeof userEvent.setup>) {
  vi.mocked(searchProducts).mockResolvedValue({
    content: [productWithVariants],
    totalElements: 1,
    totalPages: 1,
    size: 24,
    number: 0,
  } as never);
  await user.type(screen.getByPlaceholderText(/buscar producto/i), 'zap');
  const hit = await screen.findByRole('button', { name: /zapatos/i });
  await user.click(hit);
}

function setCaptionTemplate(text: string) {
  // userEvent.type treats { and } as special-key syntax; fireEvent.change sets the raw value
  // directly and sidesteps that entirely.
  fireEvent.change(screen.getByLabelText(/plantilla/i), { target: { value: text } });
}

describe('PublicarTab', () => {
  it('interpolates the caption template in the preview', async () => {
    const user = userEvent.setup();
    render(<PublicarTab />);
    await selectTheProduct(user);

    expect(await screen.findByText(/chaqueta a solo \$49\.990/i)).toBeInTheDocument();
  });

  it('publishes the batch, shows the queued confirmation and jumps to Historial', async () => {
    const user = userEvent.setup();
    const onPublished = vi.fn();
    render(<PublicarTab onPublished={onPublished} />);
    await selectTheProduct(user);

    await user.click(screen.getByRole('button', { name: /publicar ahora/i }));

    await waitFor(() => expect(publishProductsBatch).toHaveBeenCalled());
    expect(await screen.findByText(/encolado/i)).toBeInTheDocument();
    expect(onPublished).toHaveBeenCalled();
  });

  it('shows creation errors when an item comes back with a message', async () => {
    vi.mocked(publishProductsBatch).mockResolvedValueOnce({
      items: [
        { productId: 'p1', platform: 'INSTAGRAM', success: false, publicationId: null, errorMessage: 'clave duplicada', scheduled: false },
      ],
    } as never);
    const user = userEvent.setup();
    render(<PublicarTab />);
    await selectTheProduct(user);

    await user.click(screen.getByRole('button', { name: /publicar ahora/i }));

    expect(await screen.findByText(/clave duplicada/i)).toBeInTheDocument();
  });

  it('disables the publish button until a product is selected', () => {
    render(<PublicarTab />);
    expect(screen.getByRole('button', { name: /publicar ahora/i })).toBeDisabled();
  });

  it('shows the catalog on focus, before typing anything', async () => {
    const user = userEvent.setup();
    render(<PublicarTab />);

    expect(screen.queryByRole('button', { name: /chaqueta/i })).not.toBeInTheDocument();
    await user.click(screen.getByPlaceholderText(/buscar producto/i));

    expect(await screen.findByRole('button', { name: /chaqueta/i })).toBeInTheDocument();
    expect(searchProducts).toHaveBeenCalledWith({ q: '', page: 0, size: 24 }, 0, 24);
  });

  it('shows a chosen product as a chip right under the search box', async () => {
    const user = userEvent.setup();
    render(<PublicarTab />);
    await selectTheProduct(user);

    expect(screen.getByText('1 producto(s) elegido(s)')).toBeInTheDocument();
    const chip = screen.getByRole('button', { name: /quitar chaqueta/i });
    expect(chip).toBeInTheDocument();

    await user.click(chip);
    expect(screen.queryByText(/producto\(s\) elegido/i)).not.toBeInTheDocument();
  });

  it('hides the catalog list once focus leaves the search area, keeping the chip', async () => {
    const user = userEvent.setup();
    render(<PublicarTab />);
    await selectTheProduct(user);
    expect(screen.getByText('Elegido')).toBeInTheDocument();

    await user.click(screen.getByText('Instagram'));

    expect(screen.queryByText('Elegido')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /quitar chaqueta/i })).toBeInTheDocument();
  });

  it('uploads an edited photo and sends it as the override for that product', async () => {
    const user = userEvent.setup();
    render(<PublicarTab />);
    await selectTheProduct(user);

    const file = new File(['fake'], 'edited.jpg', { type: 'image/jpeg' });
    const input = screen.getByLabelText(/subir foto editada/i);
    await user.upload(input, file);

    await waitFor(() => expect(uploadMediaFile).toHaveBeenCalledWith(file, 'publications', 't'));
    expect(await screen.findByText(/reemplazar foto editada/i)).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /publicar ahora/i }));
    await waitFor(() =>
      expect(publishProductsBatch).toHaveBeenCalledWith(
        expect.objectContaining({ imageSelections: { p1: ['https://img/edited.jpg'] } }),
        't',
      ),
    );
  });

  it('fills {product_url} with the storefront product page in the preview', async () => {
    const user = userEvent.setup();
    render(<PublicarTab />);
    await selectTheProduct(user);
    setCaptionTemplate('Mira {product_url}');

    expect(await screen.findByText(/\/es\/products\/p1/)).toBeInTheDocument();
  });

  it('hides the carousel toggle for a product with no gallery', async () => {
    const user = userEvent.setup();
    render(<PublicarTab />);
    await selectTheProduct(user);
    expect(screen.queryByRole('checkbox', { name: /carrusel/i })).not.toBeInTheDocument();
    expect(screen.getByText(/agregá fotos a la galería/i)).toBeInTheDocument();
  });

  it('sends imageSelections with the gallery when carousel is on', async () => {
    const user = userEvent.setup();
    vi.mocked(searchProducts).mockResolvedValue({
      content: [{
        id: 'p1', name: 'Chaqueta', price: { amount: 49990, currency: 'CLP' },
        imageUrl: 'https://img/cover.jpg', galleryImageUrls: ['https://img/g1.jpg', 'https://img/g2.jpg'],
      } as never],
      totalElements: 1, totalPages: 1, size: 24, number: 0,
    } as never);
    render(<PublicarTab />);
    await user.type(screen.getByPlaceholderText(/buscar producto/i), 'cha');
    await user.click(await screen.findByRole('button', { name: /chaqueta/i }));

    await user.click(screen.getByRole('checkbox', { name: /carrusel/i }));
    await user.click(screen.getByRole('button', { name: /publicar ahora/i }));

    await waitFor(() =>
      expect(publishProductsBatch).toHaveBeenCalledWith(
        expect.objectContaining({
          imageSelections: { p1: ['https://img/cover.jpg', 'https://img/g1.jpg', 'https://img/g2.jpg'] },
        }),
        't',
      ),
    );
  });

  it('sends a single-element imageSelections when carousel is off', async () => {
    const user = userEvent.setup();
    render(<PublicarTab />);
    await selectTheProduct(user);
    await user.click(screen.getByRole('button', { name: /publicar ahora/i }));
    await waitFor(() =>
      expect(publishProductsBatch).toHaveBeenCalledWith(
        expect.objectContaining({ imageSelections: { p1: ['https://img/chaqueta.jpg'] } }),
        't',
      ),
    );
  });

  it('auto-picks a variant with stock and fills {color}/{talla}/{cantidad} in the preview', async () => {
    const user = userEvent.setup();
    render(<PublicarTab />);
    await selectZapatos(user);
    setCaptionTemplate('{color} {talla} quedan {cantidad}');

    expect(await screen.findByText(/Negro 38 quedan 4/)).toBeInTheDocument();
  });

  it('lets you change talla, and re-picks talla when color changes to one that lacks it', async () => {
    const user = userEvent.setup();
    render(<PublicarTab />);
    await selectZapatos(user);
    setCaptionTemplate('{color} {talla} quedan {cantidad}');
    await screen.findByText(/Negro 38 quedan 4/);

    await user.selectOptions(screen.getByLabelText(/^talla$/i), '39');
    expect(await screen.findByText(/Negro 39 quedan 3/)).toBeInTheDocument();

    await user.selectOptions(screen.getByLabelText(/^color$/i), 'Blanco');
    expect(await screen.findByText(/Blanco 38 quedan 2/)).toBeInTheDocument();
  });

  it('warns when the template uses a variant token but the product has no variants', async () => {
    const user = userEvent.setup();
    render(<PublicarTab />);
    await selectTheProduct(user);
    setCaptionTemplate('{producto} talla {talla}');

    expect(await screen.findByText(/sin variante/i)).toBeInTheDocument();
  });

  it('sends the chosen variant in the publish payload', async () => {
    const user = userEvent.setup();
    render(<PublicarTab />);
    await selectZapatos(user);

    await user.click(screen.getByRole('button', { name: /publicar ahora/i }));
    await waitFor(() =>
      expect(publishProductsBatch).toHaveBeenCalledWith(
        expect.objectContaining({ variantSelections: { p2: { color: 'Negro', size: '38' } } }),
        't',
      ),
    );
  });

  it('offers previously used photos for the product and reuses one on click', async () => {
    vi.mocked(getProductPublicationImageHistory).mockResolvedValue(['https://img/old-edit.jpg']);
    const user = userEvent.setup();
    render(<PublicarTab />);
    await selectTheProduct(user);

    const reuseButton = await screen.findByRole('button', { name: /reusar esta foto/i });
    await user.click(reuseButton);

    await user.click(screen.getByRole('button', { name: /publicar ahora/i }));
    await waitFor(() =>
      expect(publishProductsBatch).toHaveBeenCalledWith(
        expect.objectContaining({ imageSelections: { p1: ['https://img/old-edit.jpg'] } }),
        't',
      ),
    );
  });

  it('sends scheduledAt when the batch is scheduled', async () => {
    const user = userEvent.setup();
    render(<PublicarTab />);
    await selectTheProduct(user);

    await user.click(screen.getByRole('radio', { name: /programar/i }));
    fireEvent.change(screen.getByLabelText(/fecha y hora/i), { target: { value: '2027-06-15T10:00' } });

    await user.click(screen.getByRole('button', { name: /programar publicaci/i }));
    await waitFor(() =>
      expect(publishProductsBatch).toHaveBeenCalledWith(
        expect.objectContaining({ scheduledAt: '2027-06-15T14:00:00.000Z' }),
        't',
      ),
    );
    expect(await screen.findByText(/programada para/i)).toBeInTheDocument();
  });

  it('in edit mode, submit calls updateScheduledBatch and the CTA says Guardar cambios', async () => {
    const user = userEvent.setup();
    render(
      <PublicarTab
        editingBatchId="b9"
        preload={{ productIds: [], captionTemplate: 'x', hashtags: [], campaignLabel: null, scheduledAt: '2027-06-15T14:00:00.000Z' }}
      />,
    );
    await selectTheProduct(user);
    await user.click(screen.getByRole('button', { name: /guardar cambios/i }));
    await waitFor(() => expect(vi.mocked(updateScheduledBatch)).toHaveBeenCalled());
  });
});
