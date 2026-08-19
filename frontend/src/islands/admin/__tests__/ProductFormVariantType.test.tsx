import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ProductForm from '../ProductForm';
import type { CategoryDto, ProductDto } from '../../../lib/api';

/**
 * Choosing a variant type used to undo itself.
 *
 * <p>The effect that seeds the form listed the variant schema among its dependencies, and the
 * schema is derived from the very field being changed. Picking a type therefore re-ran the
 * seeding: on a new product it restored the empty form, wiping everything already typed and
 * putting the selector back to "inherit"; on an existing one it restored the saved type. The
 * choice was impossible to make, which is what the shop hit trying to add a garment.
 */

const CATEGORIES: CategoryDto[] = [
  {
    id: 'cat-vestidos', slug: 'vestidos', nameEs: 'Vestidos', nameEn: 'Dresses',
    parentId: null, categoryType: 'CLOTHING', sortOrder: 0,
  } as CategoryDto,
];

vi.mock('../../../lib/api', async () => {
  const actual = await vi.importActual<typeof import('../../../lib/api')>('../../../lib/api');
  return {
    ...actual,
    getCategories: vi.fn(async () => CATEGORIES),
    getSystemSettings: vi.fn(async () => ({})),
    createProduct: vi.fn(),
    updateProduct: vi.fn(),
    inferSingleProductAi: vi.fn(),
    transformSingleProductAiImage: vi.fn(),
    assignHeroModelFromProduct: vi.fn(),
  };
});

function existingProduct(): ProductDto {
  return {
    id: 'prod-1',
    name: 'Calza Flare',
    description: 'Un basico',
    price: { amount: 5000, currency: 'CLP' },
    listPrice: { amount: 6900, currency: 'CLP' },
    imageUrl: '',
    condition: 'USED',
    brand: 'Pilar Estilo',
    stock: 1,
    active: true,
    categorySlugs: ['vestidos'],
    categoryTypes: ['CLOTHING'],
    variantType: 'CLOTHING',
    variants: [{ color: 'Negro', size: 'M', stock: 1 }],
  } as unknown as ProductDto;
}

function typeSelect(): HTMLSelectElement {
  return screen.getByLabelText(/tipo de variante/i) as HTMLSelectElement;
}

describe('the variant type selector', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('keeps what has already been typed when the type changes', async () => {
    const user = userEvent.setup();
    render(<ProductForm product={null} token="t" onSave={() => {}} onCancel={() => {}} />);

    const name = await screen.findByLabelText(/nombre/i);
    await user.type(name, 'Calza Flare Elegance');

    await user.selectOptions(typeSelect(), 'SHOES');

    await waitFor(() => expect(typeSelect().value).toBe('SHOES'));
    expect((name as HTMLInputElement).value).toBe('Calza Flare Elegance');
  });

  it('lets an existing product change the type it was saved with', async () => {
    const user = userEvent.setup();
    render(<ProductForm product={existingProduct()} token="t" onSave={() => {}} onCancel={() => {}} />);

    await waitFor(() => expect(typeSelect().value).toBe('CLOTHING'));

    await user.selectOptions(typeSelect(), 'JEWELRY');

    await waitFor(() => expect(typeSelect().value).toBe('JEWELRY'));
    // And the name it was loaded with is still there: nothing was reseeded.
    expect((screen.getByLabelText(/nombre/i) as HTMLInputElement).value).toBe('Calza Flare');
  });
});
