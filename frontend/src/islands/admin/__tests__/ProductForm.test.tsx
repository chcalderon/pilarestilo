import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';
import ProductForm from '../ProductForm';
import type { VariantTemplateDto } from '../../../lib/api';

/**
 * Characterization suite for the template-driven rewrite (variant fields no longer derive from
 * categories -- a product picks a variant template directly from its own dropdown). Also covers
 * the regression the deleted variant-type-picker test guarded: changing the resolved schema (now
 * via the template dropdown, not category selection) must not wipe already-typed fields, since the
 * effect that seeds the form from `product` used to list the schema among its dependencies.
 */

const TEMPLATES: VariantTemplateDto[] = [
  {
    id: 'tpl-zapatos', name: 'Zapatos',
    config: {
      primary: { label: 'Color', inputType: 'FREE_TEXT', options: [], min: null, max: null, allowMultiple: false, allowCustom: true },
      secondary: { label: 'Numero', inputType: 'RANGE', options: [], min: 34, max: 43, allowMultiple: true, allowCustom: true },
    },
  },
];

vi.mock('../../../lib/api', async () => {
  const actual = await vi.importActual<typeof import('../../../lib/api')>('../../../lib/api');
  return {
    ...actual,
    getCategories: vi.fn(async () => []),
    getVariantTemplates: vi.fn(async () => TEMPLATES),
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

describe('ProductForm: template-driven variant schema', () => {
  it('renders the generic Variante/Detalle fallback when no template is selected', async () => {
    render(<ProductForm product={null} token="t" onSave={() => {}} onCancel={() => {}} />);
    await screen.findByText('Variante');
    expect(screen.getByText('Detalle(s)')).toBeInTheDocument();
  });

  it('renders the selected template field labels and range options', async () => {
    const user = userEvent.setup();
    render(<ProductForm product={null} token="t" onSave={() => {}} onCancel={() => {}} />);
    const select = await screen.findByLabelText(/tipo de variante/i);
    await user.selectOptions(select, 'tpl-zapatos');

    await screen.findByText('Numero(s)');
    expect(screen.getByRole('button', { name: '34' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '43' })).toBeInTheDocument();
  });

  it('keeps already-typed fields when selecting a template changes the schema', async () => {
    const user = userEvent.setup();
    render(<ProductForm product={null} token="t" onSave={() => {}} onCancel={() => {}} />);
    const name = await screen.findByLabelText(/nombre/i);
    await user.type(name, 'Zapato Elegance');
    const select = await screen.findByLabelText(/tipo de variante/i);

    await user.selectOptions(select, 'tpl-zapatos');

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
