import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';
import CategoryTree from '../CategoryTree';
import type { CategoryTreeNode } from '../../../lib/api';

const getCategoryTree = vi.fn();
const createCategory = vi.fn();
const updateCategory = vi.fn();
const deleteCategory = vi.fn();
const reorderCategories = vi.fn();

vi.mock('../../../lib/api', async () => {
  const actual = await vi.importActual<typeof import('../../../lib/api')>('../../../lib/api');
  return {
    ...actual,
    getCategoryTree: (...args: unknown[]) => getCategoryTree(...args),
    createCategory: (...args: unknown[]) => createCategory(...args),
    updateCategory: (...args: unknown[]) => updateCategory(...args),
    deleteCategory: (...args: unknown[]) => deleteCategory(...args),
    reorderCategories: (...args: unknown[]) => reorderCategories(...args),
  };
});

function node(overrides: Partial<CategoryTreeNode> = {}): CategoryTreeNode {
  return {
    id: 'cat-1', parentId: null, slug: 'zapatos', nameEs: 'Zapatos', nameEn: 'Shoes',
    sortOrder: 0, active: true, featured: false, imageUrl: undefined, menuVisible: true,
    categoryType: 'SHOES', heroImageUrl: undefined,
    definesVariantFields: true,
    variantFieldConfig: {
      primary: { label: 'Color', inputType: 'FREE_TEXT', options: [], min: null, max: null, allowMultiple: false, allowCustom: true },
      secondary: { label: 'Numero', inputType: 'RANGE', options: [], min: 34, max: 43, allowMultiple: true, allowCustom: true },
    },
    children: [],
    ...overrides,
  };
}

beforeEach(() => {
  vi.clearAllMocks();
  document.cookie = 'pe_token=test-token';
  getCategoryTree.mockResolvedValue([node()]);
});

describe('CategoryTree: variant field config editor', () => {
  it('shows the resolved field labels for a shape category', async () => {
    render(<CategoryTree />);
    await screen.findByText('Zapatos');

    await userEvent.click(screen.getByRole('button', { name: /editar/i }));

    expect(screen.getByDisplayValue('Color')).toBeInTheDocument();
    expect(screen.getByDisplayValue('Numero')).toBeInTheDocument();
  });

  it('hides the field editors when "defines variant fields" is off', async () => {
    getCategoryTree.mockResolvedValue([node({ definesVariantFields: false, variantFieldConfig: null })]);
    render(<CategoryTree />);
    await screen.findByText('Zapatos');

    await userEvent.click(screen.getByRole('button', { name: /editar/i }));

    expect(screen.queryByLabelText(/etiqueta.*campo 1/i)).not.toBeInTheDocument();
  });

  it('shows a min/max range editor when the secondary field is RANGE', async () => {
    render(<CategoryTree />);
    await screen.findByText('Zapatos');

    await userEvent.click(screen.getByRole('button', { name: /editar/i }));

    expect(screen.getByDisplayValue('34')).toBeInTheDocument();
    expect(screen.getByDisplayValue('43')).toBeInTheDocument();
  });

  it('submits the edited config on save', async () => {
    updateCategory.mockResolvedValue(node());
    render(<CategoryTree />);
    await screen.findByText('Zapatos');
    await userEvent.click(screen.getByRole('button', { name: /editar/i }));

    const labelInputs = screen.getAllByDisplayValue('Color');
    await userEvent.clear(labelInputs[0]);
    await userEvent.type(labelInputs[0], 'Tono');
    await userEvent.click(screen.getByRole('button', { name: /guardar/i }));

    expect(updateCategory).toHaveBeenCalledWith('cat-1', expect.objectContaining({
      definesVariantFields: true,
      primary: expect.objectContaining({ label: 'Tono' }),
    }), expect.any(String));
  });
});
