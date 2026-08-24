import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';
import DataTable, { type Column } from '../DataTable';

/**
 * Characterization tests written before splitting the desktop table and mobile card views out of
 * this component (S3776, complexity 21) -- it had none, despite being the shared table primitive
 * behind most of /admin. Covers loading/empty states, sorting, row selection and bulk actions,
 * row click/keyboard activation, pagination, and the mobile card layout, in both viewports.
 */

interface Row { id: string; name: string; qty: number; }

const columns: Column<Row>[] = [
  { key: 'name', header: 'Nombre', sortable: true },
  { key: 'qty', header: 'Cantidad' },
];

function rows(n: number): Row[] {
  return Array.from({ length: n }, (_, i) => ({ id: `r${i}`, name: `Item ${i}`, qty: i }));
}

function setViewportWidth(width: number) {
  Object.defineProperty(window, 'innerWidth', { value: width, configurable: true, writable: true });
  fireEvent(window, new Event('resize'));
}

beforeEach(() => {
  setViewportWidth(1280);
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe('DataTable (desktop)', () => {
  it('renders a header and row per column/data entry', () => {
    render(<DataTable columns={columns} data={rows(2)} keyField="id" />);
    expect(screen.getByRole('columnheader', { name: /nombre/i })).toBeInTheDocument();
    expect(screen.getByText('Item 0')).toBeInTheDocument();
    expect(screen.getByText('Item 1')).toBeInTheDocument();
  });

  it('shows the loading spinner instead of rows', () => {
    render(<DataTable columns={columns} data={rows(2)} keyField="id" loading />);
    expect(screen.queryByText('Item 0')).not.toBeInTheDocument();
    expect(document.querySelector('.animate-spin')).toBeInTheDocument();
  });

  it('shows the empty message with no data', () => {
    render(<DataTable columns={columns} data={[]} keyField="id" emptyMessage="Nada por aquí" />);
    expect(screen.getByText('Nada por aquí')).toBeInTheDocument();
  });

  it('calls onSort with the column key, and shows the sort direction icon', () => {
    const onSort = vi.fn();
    const { container } = render(
      <DataTable columns={columns} data={rows(1)} keyField="id" onSort={onSort} sortKey="name" sortDir="asc" />
    );
    fireEvent.click(screen.getByRole('columnheader', { name: /nombre/i }));
    expect(onSort).toHaveBeenCalledWith('name');
    expect(container.querySelector('.lucide-chevron-up')).toBeInTheDocument();
  });

  it('selects a row, shows the bulk action bar, runs the action, and clears selection', async () => {
    const action = vi.fn();
    const user = userEvent.setup();
    render(
      <DataTable
        columns={columns}
        data={rows(2)}
        keyField="id"
        selectable
        bulkActions={[{ label: 'Borrar', action }]}
      />
    );

    await user.click(screen.getByRole('checkbox', { name: /seleccionar fila r0/i }));
    expect(screen.getByText(/1 seleccionado/i)).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /borrar/i }));
    expect(action).toHaveBeenCalledWith(['r0']);
    expect(screen.queryByText(/seleccionado/i)).not.toBeInTheDocument();
  });

  it('selects and deselects all rows via the header checkbox', async () => {
    const user = userEvent.setup();
    render(<DataTable columns={columns} data={rows(3)} keyField="id" selectable bulkActions={[]} />);

    await user.click(screen.getByRole('checkbox', { name: /seleccionar todos/i }));
    expect(screen.getByRole('checkbox', { name: /seleccionar fila r0/i })).toBeChecked();

    await user.click(screen.getByRole('checkbox', { name: /seleccionar todos/i }));
    expect(screen.getByRole('checkbox', { name: /seleccionar fila r0/i })).not.toBeChecked();
  });

  it('cancels the selection via the Cancelar button', async () => {
    const user = userEvent.setup();
    render(<DataTable columns={columns} data={rows(2)} keyField="id" selectable bulkActions={[]} />);
    await user.click(screen.getByRole('checkbox', { name: /seleccionar fila r0/i }));

    await user.click(screen.getByRole('button', { name: /cancelar/i }));
    expect(screen.queryByText(/seleccionado/i)).not.toBeInTheDocument();
  });

  it('clicking a row and pressing Enter on a row both call onRowClick', async () => {
    const onRowClick = vi.fn();
    const user = userEvent.setup();
    render(<DataTable columns={columns} data={rows(1)} keyField="id" onRowClick={onRowClick} />);

    await user.click(screen.getByText('Item 0'));
    expect(onRowClick).toHaveBeenCalledWith(rows(1)[0]);

    onRowClick.mockClear();
    const row = screen.getByRole('row', { name: /Item 0/i });
    row.focus();
    fireEvent.keyDown(row, { key: 'Enter' });
    expect(onRowClick).toHaveBeenCalledWith(rows(1)[0]);
  });

  it('paginates: buttons disabled at the edges, onPageChange fires', async () => {
    const onPageChange = vi.fn();
    const user = userEvent.setup();
    render(<DataTable columns={columns} data={rows(20)} keyField="id" page={0} pageSize={20} total={60} onPageChange={onPageChange} />);

    expect(screen.getByText('1 / 3')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /pagina anterior/i })).toBeDisabled();

    await user.click(screen.getByRole('button', { name: /pagina siguiente/i }));
    expect(onPageChange).toHaveBeenCalledWith(1);
  });
});

describe('DataTable (mobile)', () => {
  beforeEach(() => setViewportWidth(500));

  it('renders rows as cards instead of a table', () => {
    render(<DataTable columns={columns} data={rows(1)} keyField="id" />);
    expect(screen.queryByRole('grid')).not.toBeInTheDocument();
    expect(screen.getByText('Item 0')).toBeInTheDocument();
    expect(screen.getByText('Nombre')).toBeInTheDocument();
  });

  it('shows the empty message as a card too', () => {
    render(<DataTable columns={columns} data={[]} keyField="id" emptyMessage="Vacío" />);
    expect(screen.getByText('Vacío')).toBeInTheDocument();
  });

  it('toggles row selection from a card checkbox without triggering onRowClick', async () => {
    const onRowClick = vi.fn();
    const user = userEvent.setup();
    render(<DataTable columns={columns} data={rows(1)} keyField="id" selectable onRowClick={onRowClick} bulkActions={[]} />);

    await user.click(screen.getByRole('checkbox', { name: /seleccionar fila r0/i }));
    expect(screen.getByText(/1 seleccionado/i)).toBeInTheDocument();
    expect(onRowClick).not.toHaveBeenCalled();
  });
});
