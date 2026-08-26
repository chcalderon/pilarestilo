import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';
import VariantTemplateTable from '../VariantTemplateTable';
import type { VariantTemplateDto } from '../../../lib/api';

const getVariantTemplates = vi.fn();
const createVariantTemplate = vi.fn();
const updateVariantTemplate = vi.fn();
const deleteVariantTemplate = vi.fn();

vi.mock('../../../lib/api', async () => {
  const actual = await vi.importActual<typeof import('../../../lib/api')>('../../../lib/api');
  return {
    ...actual,
    getVariantTemplates: (...args: unknown[]) => getVariantTemplates(...args),
    createVariantTemplate: (...args: unknown[]) => createVariantTemplate(...args),
    updateVariantTemplate: (...args: unknown[]) => updateVariantTemplate(...args),
    deleteVariantTemplate: (...args: unknown[]) => deleteVariantTemplate(...args),
  };
});

function template(overrides: Partial<VariantTemplateDto> = {}): VariantTemplateDto {
  return {
    id: 'tpl-1', name: 'Zapatos',
    config: {
      primary: { label: 'Color', inputType: 'FREE_TEXT', options: [], min: null, max: null, allowMultiple: false, allowCustom: true },
      secondary: { label: 'Numero', inputType: 'RANGE', options: [], min: 34, max: 43, allowMultiple: true, allowCustom: true },
    },
    ...overrides,
  };
}

beforeEach(() => {
  vi.clearAllMocks();
  document.cookie = 'pe_token=test-token';
  getVariantTemplates.mockResolvedValue([template()]);
});

describe('VariantTemplateTable', () => {
  it('shows the resolved field labels for a template', async () => {
    render(<VariantTemplateTable />);
    await screen.findByText('Zapatos');
    expect(screen.getByTitle('Color / Numero')).toBeInTheDocument();
  });

  it('opens the edit form with the template values', async () => {
    render(<VariantTemplateTable />);
    await screen.findByText('Zapatos');

    await userEvent.click(screen.getByRole('button', { name: /editar/i }));

    expect(screen.getByDisplayValue('Zapatos')).toBeInTheDocument();
    expect(screen.getByDisplayValue('Color')).toBeInTheDocument();
    expect(screen.getByDisplayValue('Numero')).toBeInTheDocument();
  });

  it('shows a min/max range editor when the secondary field is RANGE', async () => {
    render(<VariantTemplateTable />);
    await screen.findByText('Zapatos');

    await userEvent.click(screen.getByRole('button', { name: /editar/i }));

    expect(screen.getByDisplayValue('34')).toBeInTheDocument();
    expect(screen.getByDisplayValue('43')).toBeInTheDocument();
  });

  it('submits the edited config on save', async () => {
    updateVariantTemplate.mockResolvedValue(template());
    render(<VariantTemplateTable />);
    await screen.findByText('Zapatos');
    await userEvent.click(screen.getByRole('button', { name: /editar/i }));

    const labelInputs = screen.getAllByDisplayValue('Color');
    await userEvent.clear(labelInputs[0]);
    await userEvent.type(labelInputs[0], 'Tono');
    await userEvent.click(screen.getByRole('button', { name: /guardar/i }));

    expect(updateVariantTemplate).toHaveBeenCalledWith('tpl-1', expect.objectContaining({
      name: 'Zapatos',
      primary: expect.objectContaining({ label: 'Tono' }),
    }), expect.any(String));
  });

  it('creates a new template', async () => {
    createVariantTemplate.mockResolvedValue(template({ id: 'tpl-2', name: 'Carteras' }));
    render(<VariantTemplateTable />);
    await screen.findByText('Zapatos');

    await userEvent.click(screen.getByRole('button', { name: /nuevo tipo de variante/i }));
    await userEvent.type(screen.getByPlaceholderText('ej: Zapatos'), 'Carteras');
    await userEvent.click(screen.getByRole('button', { name: /guardar/i }));

    expect(createVariantTemplate).toHaveBeenCalledWith(expect.objectContaining({ name: 'Carteras' }), expect.any(String));
  });

  it('deletes a template after confirmation', async () => {
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true);
    render(<VariantTemplateTable />);
    await screen.findByText('Zapatos');

    await userEvent.click(screen.getByRole('button', { name: /eliminar/i }));

    expect(confirmSpy).toHaveBeenCalled();
    expect(deleteVariantTemplate).toHaveBeenCalledWith('tpl-1', expect.any(String));
  });
});
