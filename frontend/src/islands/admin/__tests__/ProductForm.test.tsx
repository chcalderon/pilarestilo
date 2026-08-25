import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';
import ProductForm from '../ProductForm';
import type { CategoryDto } from '../../../lib/api';

/**
 * Characterization suite for the config-driven rewrite (no per-product override picker any
 * more -- the variant schema is entirely derived from the product's selected categories). Also
 * covers the regression the deleted variant-type-picker test guarded: changing the resolved
 * schema (now via category selection, not a dropdown) must not wipe already-typed fields, since
 * the effect that seeds the form from `product` used to list the schema among its dependencies.
 */

const CATEGORIES: CategoryDto[] = [
  {
    id: 'cat-zapatos', slug: 'zapatos', nameEs: 'Zapatos', nameEn: 'Shoes',
    parentId: null, sortOrder: 0, active: true, featured: false, menuVisible: true,
    categoryType: 'SHOES',
    definesVariantFields: true,
    variantFieldConfig: {
      primary: { label: 'Color', inputType: 'FREE_TEXT', options: [], min: null, max: null, allowMultiple: false, allowCustom: true },
      secondary: { label: 'Numero', inputType: 'RANGE', options: [], min: 34, max: 43, allowMultiple: true, allowCustom: true },
    },
  } as unknown as CategoryDto,
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

beforeEach(() => {
  vi.clearAllMocks();
});

describe('ProductForm: config-driven variant schema', () => {
  it('renders the generic Variante/Detalle fallback when no shape category is selected', async () => {
    render(<ProductForm product={null} token="t" onSave={() => {}} onCancel={() => {}} />);
    await screen.findByText('Variante');
    expect(screen.getByText('Detalle(s)')).toBeInTheDocument();
  });

  it('renders the shape category field labels and range options once selected', async () => {
    const user = userEvent.setup();
    render(<ProductForm product={null} token="t" onSave={() => {}} onCancel={() => {}} />);
    await screen.findByText('Zapatos');

    await user.click(screen.getByLabelText(/zapatos/i));

    await screen.findByText('Numero(s)');
    expect(screen.getByRole('button', { name: '34' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '43' })).toBeInTheDocument();
  });

  it('keeps already-typed fields when selecting a category changes the schema', async () => {
    const user = userEvent.setup();
    render(<ProductForm product={null} token="t" onSave={() => {}} onCancel={() => {}} />);
    const name = await screen.findByLabelText(/nombre/i);
    await user.type(name, 'Zapato Elegance');
    await screen.findByText('Zapatos');

    await user.click(screen.getByLabelText(/zapatos/i));

    await screen.findByText('Numero(s)');
    expect((name as HTMLInputElement).value).toBe('Zapato Elegance');
  });

  it('adds and removes a variant row', async () => {
    const user = userEvent.setup();
    render(<ProductForm product={null} token="t" onSave={() => {}} onCancel={() => {}} />);
    await screen.findByText('Variante');
    expect(screen.getAllByRole('button', { name: /quitar/i })).toHaveLength(1);

    await user.click(screen.getByRole('button', { name: /agregar/i }));
    expect(screen.getAllByRole('button', { name: /quitar/i })).toHaveLength(2);

    await user.click(screen.getAllByRole('button', { name: /quitar/i })[0]);
    expect(screen.getAllByRole('button', { name: /quitar/i })).toHaveLength(1);
  });
});
