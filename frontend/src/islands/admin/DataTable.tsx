import { useState, useEffect } from 'react';
import {
  ChevronUp,
  ChevronDown,
  ChevronsUpDown,
  Loader2,
  ChevronLeft,
  ChevronRight,
  X,
} from 'lucide-react';

export interface Column<T> {
  key: string;
  header: string;
  sortable?: boolean;
  render?: (row: T) => React.ReactNode;
  width?: string;
}

export interface BulkAction {
  label: string;
  icon?: React.ReactNode;
  variant?: 'danger' | 'default';
  action: (ids: string[]) => void;
}

export interface DataTableProps<T> {
  readonly columns: Column<T>[];
  readonly data: T[];
  readonly keyField: keyof T;
  readonly loading?: boolean;
  readonly emptyMessage?: string;
  readonly page?: number;
  readonly pageSize?: number;
  readonly total?: number;
  readonly onPageChange?: (page: number) => void;
  readonly sortKey?: string;
  readonly sortDir?: 'asc' | 'desc';
  readonly onSort?: (key: string) => void;
  readonly selectable?: boolean;
  readonly bulkActions?: BulkAction[];
  readonly onRowClick?: (row: T) => void;
}

function formatCellValue(value: unknown): string {
  if (value === null || value === undefined) return '-';
  switch (typeof value) {
    case 'string':
    case 'number':
    case 'boolean':
    case 'bigint':
      return String(value);
    default:
      return JSON.stringify(value);
  }
}

function rowKeyDownHandler<T>(onRowClick: (row: T) => void, row: T) {
  return (event: React.KeyboardEvent) => {
    if (event.target !== event.currentTarget) return;
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      onRowClick(row);
    }
  };
}

interface BulkActionBarProps {
  readonly count: number;
  readonly actions: BulkAction[];
  readonly onRun: (action: BulkAction) => void;
  readonly onCancel: () => void;
}

function BulkActionBar({ count, actions, onRun, onCancel }: BulkActionBarProps) {
  return (
    <div className="flex flex-wrap items-center gap-3 px-4 py-2.5 bg-pe-rose/8 border border-pe-rose/20 mb-2">
      <span className="font-sans text-[0.78rem] text-pe-rose-ink">
        {count} seleccionado{count > 1 ? 's' : ''}
      </span>
      <div className="flex flex-wrap gap-2">
        {actions.map((action) => (
          <button
            type="button"
            key={action.label}
            onClick={() => onRun(action)}
            className={[
              'flex items-center gap-1.5 font-sans text-[0.72rem] tracking-[0.08em] uppercase px-3 py-1.5 transition-colors duration-150',
              action.variant === 'danger'
                ? 'bg-red-600 text-white hover:bg-red-700'
                : 'bg-pe-black text-pe-offwhite hover:bg-pe-charcoal',
            ].join(' ')}
          >
            {action.icon}
            {action.label}
          </button>
        ))}
      </div>
      <button
        type="button"
        onClick={onCancel}
        className="ml-auto inline-flex items-center gap-1 font-sans text-[0.72rem] text-pe-muted hover:text-pe-charcoal transition-colors"
      >
        <X size={12} />
        Cancelar
      </button>
    </div>
  );
}

interface PaginationProps {
  readonly page: number;
  readonly totalPages: number;
  readonly displayedFrom: number;
  readonly displayedTo: number;
  readonly total: number;
  readonly onPageChange?: (page: number) => void;
}

function Pagination({ page, totalPages, displayedFrom, displayedTo, total, onPageChange }: PaginationProps) {
  return (
    <div className="flex flex-col gap-2 px-1 pt-3 sm:flex-row sm:items-center sm:justify-between">
      <p className="font-sans text-[0.72rem] text-pe-muted">
        {displayedFrom}-{displayedTo} de {total}
      </p>
      <div className="flex items-center gap-1">
        <button
          type="button"
          onClick={() => onPageChange?.(page - 1)}
          disabled={page === 0}
          className="p-1.5 text-pe-muted hover:text-pe-charcoal disabled:opacity-25 disabled:cursor-not-allowed transition-colors"
          aria-label="Pagina anterior"
        >
          <ChevronLeft size={16} />
        </button>
        <span className="font-sans text-[0.78rem] text-pe-muted px-2">
          {page + 1} / {totalPages}
        </span>
        <button
          type="button"
          onClick={() => onPageChange?.(page + 1)}
          disabled={page + 1 >= totalPages}
          className="p-1.5 text-pe-muted hover:text-pe-charcoal disabled:opacity-25 disabled:cursor-not-allowed transition-colors"
          aria-label="Pagina siguiente"
        >
          <ChevronRight size={16} />
        </button>
      </div>
    </div>
  );
}

interface ViewProps<T> {
  readonly columns: Column<T>[];
  readonly data: T[];
  readonly loading?: boolean;
  readonly emptyMessage: string;
  readonly selectable?: boolean;
  readonly selected: Set<string>;
  readonly getRowId: (row: T) => string;
  readonly toggleRow: (id: string) => void;
  readonly onRowClick?: (row: T) => void;
}

function MobileCards<T>({ columns, data, loading, emptyMessage, selectable, selected, getRowId, toggleRow, onRowClick }: ViewProps<T>) {
  const wrapperClass = 'bg-[var(--pe-surface-card)] border border-[var(--pe-border)] shadow-xs divide-y divide-[var(--pe-border)]';

  if (loading) {
    return (
      <div className={wrapperClass}>
        <div className="py-16 text-center">
          <Loader2 size={22} className="animate-spin text-pe-rose-ink inline-block" />
        </div>
      </div>
    );
  }

  if (data.length === 0) {
    return (
      <div className={wrapperClass}>
        <div className="py-14 text-center font-sans text-[0.82rem] text-pe-muted">{emptyMessage}</div>
      </div>
    );
  }

  return (
    <div className={wrapperClass}>
      {data.map((row) => {
          const id = getRowId(row);
          const isSelected = selected.has(id);

          return (
            <article
              key={id}
              className={[
                'p-3 transition-colors duration-100',
                isSelected ? 'bg-pe-rose/6' : 'hover:bg-pe-cream/40',
                onRowClick ? 'cursor-pointer' : '',
              ].join(' ')}
              tabIndex={onRowClick ? 0 : undefined}
              role={onRowClick ? 'button' : undefined}
              onKeyDown={onRowClick ? rowKeyDownHandler(onRowClick, row) : undefined}
              onClick={onRowClick ? () => onRowClick(row) : undefined}
            >
              {selectable && (
                <div className="mb-2">
                  <label className="inline-flex items-center gap-2 font-sans text-[0.7rem] uppercase tracking-[0.1em] text-pe-muted">
                    <input
                      type="checkbox"
                      checked={isSelected}
                      onChange={() => toggleRow(id)}
                      onClick={(e) => e.stopPropagation()}
                      className="accent-pe-rose cursor-pointer"
                      aria-label={`Seleccionar fila ${id}`}
                    />{' '}
                    Seleccionar
                  </label>
                </div>
              )}

              <div className="flex flex-col gap-2">
                {columns.map((col) => (
                  <div key={col.key} className="flex flex-col gap-1">
                    {col.header ? (
                      <span className="font-sans text-[0.62rem] uppercase tracking-[0.12em] text-pe-muted">
                        {col.header}
                      </span>
                    ) : null}
                    <div className="font-sans text-[0.82rem] text-pe-charcoal">
                      {col.render ? col.render(row) : formatCellValue((row as Record<string, unknown>)[col.key])}
                    </div>
                  </div>
                ))}
              </div>
            </article>
          );
        })}
    </div>
  );
}

interface DesktopTableProps<T> extends ViewProps<T> {
  readonly toggleAll: () => void;
  readonly sortKey?: string;
  readonly sortDir?: 'asc' | 'desc';
  readonly onSort?: (key: string) => void;
}

interface SortIconProps {
  readonly colKey: string;
  readonly sortKey?: string;
  readonly sortDir?: 'asc' | 'desc';
}

function SortIcon({ colKey, sortKey, sortDir }: SortIconProps) {
  if (sortKey !== colKey) return <ChevronsUpDown size={12} className="text-pe-muted" />;
  return sortDir === 'asc' ? <ChevronUp size={12} className="text-pe-rose-ink" /> : <ChevronDown size={12} className="text-pe-rose-ink" />;
}

function DesktopTable<T>({
  columns, data, loading, emptyMessage, selectable, selected, getRowId, toggleRow, toggleAll, onRowClick, sortKey, sortDir, onSort,
}: DesktopTableProps<T>) {
  const thBase =
    'font-sans text-[0.68rem] tracking-[0.1em] uppercase text-pe-muted px-3 py-2.5 text-left whitespace-nowrap border-b border-pe-black/8 bg-pe-cream/60';
  const tdBase = 'font-sans text-[0.82rem] px-3 py-2.5 border-b border-pe-black/5';

  let tbodyContent: React.ReactNode;
  if (loading) {
    tbodyContent = (
      <tr>
        <td colSpan={columns.length + (selectable ? 1 : 0)} className="py-16 text-center">
          <Loader2 size={22} className="animate-spin text-pe-rose-ink inline-block" />
        </td>
      </tr>
    );
  } else if (data.length === 0) {
    tbodyContent = (
      <tr>
        <td colSpan={columns.length + (selectable ? 1 : 0)} className="py-14 text-center font-sans text-[0.82rem] text-pe-muted">
          {emptyMessage}
        </td>
      </tr>
    );
  } else {
    tbodyContent = data.map((row) => {
      const id = getRowId(row);
      const isSelected = selected.has(id);
      return (
        <tr
          key={id}
          className={[
            'transition-colors duration-100',
            isSelected ? 'bg-pe-rose/4' : 'hover:bg-pe-cream/50',
            onRowClick ? 'cursor-pointer' : '',
          ].join(' ')}
          /*
           * A row that opens a drawer is the only way into most of these screens, so
           * it cannot be mouse-only. tabIndex puts it in the tab order and Enter or
           * Space opens it, which is what a button would have done.
           */
          tabIndex={onRowClick ? 0 : undefined}
          onClick={onRowClick ? () => onRowClick(row) : undefined}
          onKeyDown={onRowClick ? rowKeyDownHandler(onRowClick, row) : undefined}
        >
          {selectable && (
            <td
              className={tdBase}
              onClick={(e) => {
                e.stopPropagation();
                if ((e.target as HTMLElement).closest('input[type="checkbox"]')) {
                  return;
                }
                toggleRow(id);
              }}
            >
              <input
                type="checkbox"
                checked={isSelected}
                onChange={() => toggleRow(id)}
                className="accent-pe-rose cursor-pointer"
                aria-label={`Seleccionar fila ${id}`}
              />
            </td>
          )}
          {columns.map((col) => (
            <td key={col.key} className={tdBase}>
              {col.render ? col.render(row) : formatCellValue((row as Record<string, unknown>)[col.key])}
            </td>
          ))}
        </tr>
      );
    });
  }

  return (
    <div className="overflow-x-auto bg-[var(--pe-surface-card)] border border-[var(--pe-border)] shadow-xs">
      <table className="w-full table-fixed" role="grid">
        <thead>
          <tr>
            {selectable && (
              <th className={thBase} style={{ width: '40px' }}>
                <input
                  type="checkbox"
                  checked={data.length > 0 && selected.size === data.length}
                  onChange={toggleAll}
                  className="accent-pe-rose cursor-pointer"
                  aria-label="Seleccionar todos"
                />
              </th>
            )}
            {columns.map((col) => (
              <th
                key={col.key}
                className={thBase + (col.sortable && onSort ? ' cursor-pointer select-none hover:text-pe-charcoal transition-colors' : '')}
                style={col.width ? { width: col.width } : undefined}
                onClick={col.sortable && onSort ? () => onSort(col.key) : undefined}
              >
                <span className="inline-flex items-center gap-1">
                  {col.header}
                  {col.sortable && onSort && <SortIcon colKey={col.key} sortKey={sortKey} sortDir={sortDir} />}
                </span>
              </th>
            ))}
          </tr>
        </thead>
        <tbody>{tbodyContent}</tbody>
      </table>
    </div>
  );
}

export default function DataTable<T>({
  columns,
  data,
  keyField,
  loading,
  emptyMessage = 'No hay datos.',
  page = 0,
  pageSize = 20,
  total,
  onPageChange,
  sortKey,
  sortDir,
  onSort,
  selectable,
  bulkActions,
  onRowClick,
}: DataTableProps<T>) {
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [isMobile, setIsMobile] = useState(false);

  useEffect(() => {
    if (typeof window === 'undefined') return;
    const mq = () => setIsMobile(window.innerWidth < 1024);
    mq();
    window.addEventListener('resize', mq);
    return () => window.removeEventListener('resize', mq);
  }, []);

  const totalPages = total !== undefined ? Math.ceil(total / pageSize) : undefined;
  const displayedFrom = total === 0 ? 0 : page * pageSize + 1;
  const displayedTo = Math.min((page + 1) * pageSize, total ?? data.length);

  function getRowId(row: T): string {
    return String((row as Record<string, unknown>)[String(keyField)]);
  }

  function toggleAll() {
    if (selected.size === data.length) {
      setSelected(new Set());
    } else {
      setSelected(new Set(data.map((row) => getRowId(row))));
    }
  }

  function toggleRow(id: string) {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  }

  const viewProps: ViewProps<T> = {
    columns, data, loading, emptyMessage, selectable, selected, getRowId, toggleRow, onRowClick,
  };

  return (
    <div className="flex flex-col gap-0">
      {selectable && selected.size > 0 && bulkActions && (
        <BulkActionBar
          count={selected.size}
          actions={bulkActions}
          onRun={(action) => { action.action(Array.from(selected)); setSelected(new Set()); }}
          onCancel={() => setSelected(new Set())}
        />
      )}

      {isMobile ? (
        <MobileCards {...viewProps} />
      ) : (
        <DesktopTable {...viewProps} toggleAll={toggleAll} sortKey={sortKey} sortDir={sortDir} onSort={onSort} />
      )}

      {totalPages !== undefined && totalPages > 1 && (
        <Pagination page={page} totalPages={totalPages} displayedFrom={displayedFrom} displayedTo={displayedTo} total={total ?? data.length} onPageChange={onPageChange} />
      )}
    </div>
  );
}
