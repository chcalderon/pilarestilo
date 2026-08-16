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
  columns: Column<T>[];
  data: T[];
  keyField: keyof T;
  loading?: boolean;
  emptyMessage?: string;
  page?: number;
  pageSize?: number;
  total?: number;
  onPageChange?: (page: number) => void;
  sortKey?: string;
  sortDir?: 'asc' | 'desc';
  onSort?: (key: string) => void;
  selectable?: boolean;
  bulkActions?: BulkAction[];
  onRowClick?: (row: T) => void;
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
      next.has(id) ? next.delete(id) : next.add(id);
      return next;
    });
  }

  function SortIcon({ colKey }: { colKey: string }) {
    if (sortKey !== colKey) return <ChevronsUpDown size={12} className="text-pe-charcoal/20" />;
    return sortDir === 'asc' ? <ChevronUp size={12} className="text-pe-rose" /> : <ChevronDown size={12} className="text-pe-rose" />;
  }

  const thBase =
    'font-sans text-[0.68rem] tracking-[0.1em] uppercase text-pe-charcoal/50 px-3 py-2.5 text-left whitespace-nowrap border-b border-pe-black/8 bg-pe-cream/60';
  const tdBase = 'font-sans text-[0.82rem] px-3 py-2.5 border-b border-pe-black/5';

  return (
    <div className="flex flex-col gap-0">
      {selectable && selected.size > 0 && bulkActions && (
        <div className="flex flex-wrap items-center gap-3 px-4 py-2.5 bg-pe-rose/8 border border-pe-rose/20 mb-2">
          <span className="font-sans text-[0.78rem] text-pe-rose-deep">
            {selected.size} seleccionado{selected.size > 1 ? 's' : ''}
          </span>
          <div className="flex flex-wrap gap-2">
            {bulkActions.map((action, i) => (
              <button
                key={i}
                onClick={() => {
                  action.action(Array.from(selected));
                  setSelected(new Set());
                }}
                className={[
                  'flex items-center gap-1.5 font-sans text-[0.72rem] tracking-[0.08em] uppercase px-3 py-1.5 transition-colors duration-150',
                  action.variant === 'danger'
                    ? 'bg-red-600 text-white hover:bg-red-700'
                    : 'bg-pe-black text-pe-offwhite hover:bg-[#3A3A3A]',
                ].join(' ')}
              >
                {action.icon}
                {action.label}
              </button>
            ))}
          </div>
          <button
            onClick={() => setSelected(new Set())}
            className="ml-auto inline-flex items-center gap-1 font-sans text-[0.72rem] text-pe-charcoal/40 hover:text-pe-charcoal transition-colors"
          >
            <X size={12} />
            Cancelar
          </button>
        </div>
      )}

      {isMobile ? (
        <div className="bg-[var(--pe-surface-card)] border border-[var(--pe-border)] shadow-sm divide-y divide-[var(--pe-border)]">
          {loading ? (
            <div className="py-16 text-center">
              <Loader2 size={22} className="animate-spin text-pe-rose/50 inline-block" />
            </div>
          ) : data.length === 0 ? (
            <div className="py-14 text-center font-sans text-[0.82rem] text-pe-charcoal/35">{emptyMessage}</div>
          ) : (
            data.map((row) => {
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
                  onClick={onRowClick ? () => onRowClick(row) : undefined}
                >
                  {selectable && (
                    <div className="mb-2">
                      <label className="inline-flex items-center gap-2 font-sans text-[0.7rem] uppercase tracking-[0.1em] text-pe-charcoal/50">
                        <input
                          type="checkbox"
                          checked={isSelected}
                          onChange={() => toggleRow(id)}
                          onClick={(e) => e.stopPropagation()}
                          className="accent-pe-rose cursor-pointer"
                          aria-label={`Seleccionar fila ${id}`}
                        />
                        Seleccionar
                      </label>
                    </div>
                  )}

                  <div className="flex flex-col gap-2">
                    {columns.map((col) => (
                      <div key={col.key} className="flex flex-col gap-1">
                        {col.header ? (
                          <span className="font-sans text-[0.62rem] uppercase tracking-[0.12em] text-pe-charcoal/45">
                            {col.header}
                          </span>
                        ) : null}
                        <div className="font-sans text-[0.82rem] text-pe-charcoal">
                          {col.render ? col.render(row) : String((row as Record<string, unknown>)[col.key] ?? '-')}
                        </div>
                      </div>
                    ))}
                  </div>
                </article>
              );
            })
          )}
        </div>
      ) : (
        <div className="overflow-x-auto bg-[var(--pe-surface-card)] border border-[var(--pe-border)] shadow-sm">
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
                      {col.sortable && onSort && <SortIcon colKey={col.key} />}
                    </span>
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr>
                  <td colSpan={columns.length + (selectable ? 1 : 0)} className="py-16 text-center">
                    <Loader2 size={22} className="animate-spin text-pe-rose/50 inline-block" />
                  </td>
                </tr>
              ) : data.length === 0 ? (
                <tr>
                  <td colSpan={columns.length + (selectable ? 1 : 0)} className="py-14 text-center font-sans text-[0.82rem] text-pe-charcoal/35">
                    {emptyMessage}
                  </td>
                </tr>
              ) : (
                data.map((row) => {
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
                      onClick={onRowClick ? () => onRowClick(row) : undefined}
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
                          {col.render ? col.render(row) : String((row as Record<string, unknown>)[col.key] ?? '-')}
                        </td>
                      ))}
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      )}

      {totalPages !== undefined && totalPages > 1 && (
        <div className="flex flex-col gap-2 px-1 pt-3 sm:flex-row sm:items-center sm:justify-between">
          <p className="font-sans text-[0.72rem] text-pe-charcoal/40">
            {displayedFrom}-{displayedTo} de {total}
          </p>
          <div className="flex items-center gap-1">
            <button
              onClick={() => onPageChange?.(page - 1)}
              disabled={page === 0}
              className="p-1.5 text-pe-charcoal/50 hover:text-pe-charcoal disabled:opacity-25 disabled:cursor-not-allowed transition-colors"
              aria-label="Pagina anterior"
            >
              <ChevronLeft size={16} />
            </button>
            <span className="font-sans text-[0.78rem] text-pe-charcoal/60 px-2">
              {page + 1} / {totalPages}
            </span>
            <button
              onClick={() => onPageChange?.(page + 1)}
              disabled={page + 1 >= totalPages}
              className="p-1.5 text-pe-charcoal/50 hover:text-pe-charcoal disabled:opacity-25 disabled:cursor-not-allowed transition-colors"
              aria-label="Pagina siguiente"
            >
              <ChevronRight size={16} />
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
