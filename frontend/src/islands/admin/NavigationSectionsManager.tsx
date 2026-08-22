import { useState, useEffect } from 'react';
import { Plus, Edit3, Trash2, Loader2, Check, X } from 'lucide-react';
import {
  listNavigationSections,
  createNavigationSection,
  updateNavigationSection,
  deleteNavigationSection,
  uploadNavigationBanner,
  getCategories,
  type AdminNavigationSectionDto,
  type UpsertNavigationSectionRequest,
  type CategoryDto,
} from '../../lib/api';
import { useAuthStore, readAuthTokenCookie } from '../../lib/authStore';
import ImageDropzone from './ImageDropzone';

// ─── Types ────────────────────────────────────────────────────────────────────

type LayoutOption = 'COLUMNS' | 'FEATURED_GRID' | 'EDITORIAL';

interface SectionForm {
  rootCategoryId: string;
  layout: LayoutOption;
  columnCount: string;
  bannerImageUrl: string;
  bannerTitle: string;
  bannerSubtitle: string;
  bannerLink: string;
  active: boolean;
  sortOrder: string;
}

const EMPTY_FORM: SectionForm = {
  rootCategoryId: '',
  layout: 'COLUMNS',
  columnCount: '3',
  bannerImageUrl: '',
  bannerTitle: '',
  bannerSubtitle: '',
  bannerLink: '',
  active: true,
  sortOrder: '0',
};

const LAYOUT_LABELS: Record<LayoutOption, string> = {
  COLUMNS: 'Columnas',
  FEATURED_GRID: 'Grilla destacada',
  EDITORIAL: 'Editorial',
};

// ─── Helpers ──────────────────────────────────────────────────────────────────

const INPUT_CLASS =
  'font-sans text-[0.78rem] border border-pe-black/12 bg-pe-white px-2 py-1.5 text-pe-charcoal focus:outline-hidden focus:border-pe-rose/50 transition-colors w-full';

const SELECT_CLASS =
  'font-sans text-[0.78rem] border border-pe-black/12 bg-pe-white px-2 py-1.5 text-pe-charcoal focus:outline-hidden focus:border-pe-rose/50 transition-colors w-full';

function fromDto(dto: AdminNavigationSectionDto): SectionForm {
  return {
    rootCategoryId: dto.rootCategoryId,
    layout: dto.layout as LayoutOption,
    columnCount: String(dto.columnCount),
    bannerImageUrl: dto.bannerImageUrl ?? '',
    bannerTitle: dto.bannerTitle ?? '',
    bannerSubtitle: dto.bannerSubtitle ?? '',
    bannerLink: dto.bannerLink ?? '',
    active: dto.active,
    sortOrder: String(dto.sortOrder),
  };
}

function toRequest(form: SectionForm): UpsertNavigationSectionRequest {
  return {
    rootCategoryId: form.rootCategoryId,
    layout: form.layout,
    columnCount: Number(form.columnCount) || 3,
    bannerImageUrl: form.bannerImageUrl || undefined,
    bannerTitle: form.bannerTitle || undefined,
    bannerSubtitle: form.bannerSubtitle || undefined,
    bannerLink: form.bannerLink || undefined,
    active: form.active,
    sortOrder: Number(form.sortOrder) || 0,
  };
}

// ─── Toast (inline, no library) ───────────────────────────────────────────────

interface ToastState {
  type: 'success' | 'error';
  message: string;
}

// ─── SectionForm ──────────────────────────────────────────────────────────────

interface SectionFormPanelProps {
  readonly form: SectionForm;
  readonly setForm: React.Dispatch<React.SetStateAction<SectionForm>>;
  readonly saving: boolean;
  readonly categories: CategoryDto[];
  readonly token: string;
  readonly onSubmit: () => void;
  readonly onCancel: () => void;
}

function SectionFormPanel({ form, setForm, saving, categories, token, onSubmit, onCancel }: SectionFormPanelProps) {
  const selectedSlug = categories.find(c => c.id === form.rootCategoryId)?.slug ?? '';
  return (
    <div className="bg-pe-cream/50 border border-pe-black/8 p-4 mt-2 flex flex-col gap-4">
      {/* Row 1: category, layout, columns, sortOrder */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
        <div className="flex flex-col gap-0.5">
          <label className="font-sans text-[0.62rem] uppercase tracking-wider text-pe-muted">
            Categoría raíz *
          </label>
          <select
            className={SELECT_CLASS}
            value={form.rootCategoryId}
            onChange={e => setForm(f => ({ ...f, rootCategoryId: e.target.value }))}
          >
            <option value="">-- Seleccionar --</option>
            {categories
              .filter(c => c.parentId === null)
              .map(c => (
                <option key={c.id} value={c.id}>
                  {c.nameEs}
                </option>
              ))}
          </select>
        </div>

        <div className="flex flex-col gap-0.5">
          <label className="font-sans text-[0.62rem] uppercase tracking-wider text-pe-muted">
            Diseño
          </label>
          <select
            className={SELECT_CLASS}
            value={form.layout}
            onChange={e => setForm(f => ({ ...f, layout: e.target.value as LayoutOption }))}
          >
            {(Object.entries(LAYOUT_LABELS) as [LayoutOption, string][]).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </div>

        <div className="flex flex-col gap-0.5">
          <label className="font-sans text-[0.62rem] uppercase tracking-wider text-pe-muted">
            Columnas (1–6)
          </label>
          <input
            type="number"
            min="1"
            max="6"
            className={INPUT_CLASS}
            value={form.columnCount}
            onChange={e => setForm(f => ({ ...f, columnCount: e.target.value }))}
          />
        </div>

        <div className="flex flex-col gap-0.5">
          <label className="font-sans text-[0.62rem] uppercase tracking-wider text-pe-muted">
            Orden
          </label>
          <input
            type="number"
            min="0"
            className={INPUT_CLASS}
            value={form.sortOrder}
            onChange={e => setForm(f => ({ ...f, sortOrder: e.target.value }))}
          />
        </div>
      </div>

      {/* Row 2: banner fields */}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
        {/* Banner image — upload replaces URL input; stored as banner-{slug}.jpg */}
        <div className="flex flex-col gap-0.5 sm:row-span-2">
          {selectedSlug ? (
            <ImageDropzone
              label="Imagen del banner"
              value={form.bannerImageUrl || undefined}
              folder="navigation"
              token={token}
              customUpload={file => uploadNavigationBanner(file, selectedSlug, token)}
              onUpload={url => setForm(f => ({ ...f, bannerImageUrl: url }))}
              allowClear
            />
          ) : (
            <div className="flex flex-col gap-0.5">
              <span className="font-sans text-[0.62rem] uppercase tracking-wider text-pe-muted">
                Imagen del banner
              </span>
              <div className="h-48 border border-dashed border-pe-black/15 bg-pe-cream/20 flex items-center justify-center">
                <span className="font-sans text-[0.68rem] text-pe-muted text-center px-4">
                  Selecciona una categoría raíz primero
                </span>
              </div>
            </div>
          )}
        </div>

        <div className="flex flex-col gap-0.5">
          <label className="font-sans text-[0.62rem] uppercase tracking-wider text-pe-muted">
            Banner: título
          </label>
          <input
            type="text"
            className={INPUT_CLASS}
            value={form.bannerTitle}
            placeholder="Ej: Novedades"
            onChange={e => setForm(f => ({ ...f, bannerTitle: e.target.value }))}
          />
        </div>

        <div className="flex flex-col gap-0.5">
          <label className="font-sans text-[0.62rem] uppercase tracking-wider text-pe-muted">
            Banner: subtítulo
          </label>
          <input
            type="text"
            className={INPUT_CLASS}
            value={form.bannerSubtitle}
            placeholder="Texto secundario"
            onChange={e => setForm(f => ({ ...f, bannerSubtitle: e.target.value }))}
          />
        </div>

        <div className="flex flex-col gap-0.5">
          <label className="font-sans text-[0.62rem] uppercase tracking-wider text-pe-muted">
            Banner: enlace
          </label>
          <input
            type="text"
            className={INPUT_CLASS}
            value={form.bannerLink}
            placeholder="/es/categorias/..."
            onChange={e => setForm(f => ({ ...f, bannerLink: e.target.value }))}
          />
        </div>
      </div>

      {/* Row 3: active + buttons */}
      <div className="flex flex-wrap items-center gap-3">
        <label className="flex items-center gap-1.5 font-sans text-[0.78rem] text-pe-muted cursor-pointer">
          <input
            type="checkbox"
            checked={form.active}
            onChange={e => setForm(f => ({ ...f, active: e.target.checked }))}
            className="accent-pe-rose"
          />
          Activa
        </label>

        <button
          type="button"
          onClick={onSubmit}
          disabled={saving}
          className="flex items-center gap-1 bg-pe-rose-action text-pe-offwhite font-sans text-[0.68rem] uppercase tracking-wider px-3 py-1.5 hover:bg-pe-rose-action-action-deep transition-colors disabled:opacity-50"
        >
          {saving ? <Loader2 size={12} className="animate-spin" /> : <Check size={12} />}
          Guardar
        </button>

        <button
          type="button"
          onClick={onCancel}
          className="flex items-center gap-1 border border-pe-black/12 font-sans text-[0.68rem] uppercase tracking-wider px-3 py-1.5 hover:border-pe-charcoal transition-colors text-pe-muted"
        >
          <X size={12} /> Cancelar
        </button>
      </div>
    </div>
  );
}

// ─── NavigationSectionsManager ────────────────────────────────────────────────

export default function NavigationSectionsManager() {
  const { token } = useAuthStore();
  const effectiveToken = token ?? readAuthTokenCookie();

  const [sections, setSections] = useState<AdminNavigationSectionDto[]>([]);
  const [categories, setCategories] = useState<CategoryDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [editing, setEditing] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);
  const [form, setForm] = useState<SectionForm>({ ...EMPTY_FORM });
  const [saving, setSaving] = useState(false);
  const [toast, setToast] = useState<ToastState | null>(null);

  function showToast(type: 'success' | 'error', message: string) {
    setToast({ type, message });
    setTimeout(() => setToast(null), 3500);
  }

  async function loadData() {
    setLoading(true);
    try {
      const [secs, cats] = await Promise.all([
        listNavigationSections(effectiveToken ?? ''),
        getCategories(),
      ]);
      setSections(secs);
      setCategories(cats);
    } catch {
      showToast('error', 'Error al cargar los datos.');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadData();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  function categoryName(id: string): string {
    const cat = categories.find(c => c.id === id);
    return cat ? cat.nameEs : id;
  }

  function handleEdit(section: AdminNavigationSectionDto) {
    setEditing(section.id);
    setForm(fromDto(section));
    setCreating(false);
  }

  function handleCancel() {
    setEditing(null);
    setCreating(false);
    setForm({ ...EMPTY_FORM });
  }

  async function handleSaveEdit(id: string) {
    if (!effectiveToken || !form.rootCategoryId) {
      showToast('error', 'Selecciona una categoría raíz.');
      return;
    }
    setSaving(true);
    try {
      await updateNavigationSection(id, toRequest(form), effectiveToken);
      setEditing(null);
      setForm({ ...EMPTY_FORM });
      showToast('success', 'Sección actualizada.');
      await loadData();
    } catch (err) {
      showToast('error', err instanceof Error ? err.message : 'Error al guardar.');
    } finally {
      setSaving(false);
    }
  }

  async function handleCreate() {
    if (!effectiveToken || !form.rootCategoryId) {
      showToast('error', 'Selecciona una categoría raíz.');
      return;
    }
    setSaving(true);
    try {
      await createNavigationSection(toRequest(form), effectiveToken);
      setCreating(false);
      setForm({ ...EMPTY_FORM });
      showToast('success', 'Sección creada.');
      await loadData();
    } catch (err) {
      showToast('error', err instanceof Error ? err.message : 'Error al crear la sección.');
    } finally {
      setSaving(false);
    }
  }

  async function handleDelete(section: AdminNavigationSectionDto) {
    if (
      !effectiveToken ||
      !confirm(
        `¿Eliminar la sección "${categoryName(section.rootCategoryId)}"?\n\nEsta acción no se puede deshacer.`
      )
    ) {
      return;
    }
    try {
      await deleteNavigationSection(section.id, effectiveToken);
      showToast('success', 'Sección eliminada.');
      await loadData();
    } catch (err) {
      showToast('error', err instanceof Error ? err.message : 'Error al eliminar la sección.');
    }
  }

  if (loading) {
    return (
      <div className="flex justify-center py-16">
        <Loader2 size={24} className="animate-spin text-pe-rose-ink" />
      </div>
    );
  }

  return (
    <div>
      {/* Toast */}
      {toast && (
        <div
          className={[
            'mb-4 px-4 py-2.5 font-sans text-[0.78rem] flex items-center gap-2',
            toast.type === 'success'
              ? 'bg-emerald-50 border border-emerald-200 text-emerald-800'
              : 'bg-red-50 border border-red-200 text-red-700',
          ].join(' ')}
          role="status"
          aria-live="polite"
        >
          {toast.type === 'success' ? <Check size={14} /> : <X size={14} />}
          {toast.message}
        </div>
      )}

      {/* Toolbar */}
      <div className="mb-4 flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <p className="font-sans text-[0.72rem] text-pe-muted">
          {sections.length} {sections.length === 1 ? 'sección' : 'secciones'}
        </p>
        <button
          type="button"
          onClick={() => {
            setCreating(true);
            setForm({ ...EMPTY_FORM });
            setEditing(null);
          }}
          className="inline-flex w-full sm:w-auto items-center justify-center gap-2 bg-pe-rose-action text-pe-offwhite font-sans text-[0.72rem] tracking-[0.14em] uppercase px-4 py-2 hover:bg-pe-rose-action-action-deep transition-colors duration-200"
        >
          <Plus size={13} />
          Nueva sección
        </button>
      </div>

      {/* Create form */}
      {creating && (
        <div className="mb-4">
          <SectionFormPanel
            form={form}
            setForm={setForm}
            saving={saving}
            categories={categories}
            token={effectiveToken ?? ''}
            onSubmit={handleCreate}
            onCancel={handleCancel}
          />
        </div>
      )}

      {/* Sections list */}
      <div className="bg-pe-white border border-pe-black/6 shadow-xs">
        {sections.length === 0 ? (
          <p className="font-sans text-[0.82rem] text-pe-muted text-center py-12">
            No hay secciones configuradas. Crea la primera.
          </p>
        ) : (
          <ul className="divide-y divide-pe-black/5">
            {sections.map(section => (
              <li key={section.id}>
                <div className="group flex flex-wrap items-center gap-3 px-4 py-3 hover:bg-pe-cream/30 transition-colors">
                  {/* Status badge */}
                  <span
                    className={[
                      'font-sans text-[0.58rem] uppercase tracking-wider px-1.5 py-0.5 shrink-0',
                      section.active
                        ? 'bg-emerald-50 text-emerald-800 border border-emerald-200'
                        : 'bg-pe-cream text-pe-muted border border-pe-black/8',
                    ].join(' ')}
                  >
                    {section.active ? 'Activa' : 'Inactiva'}
                  </span>

                  {/* Category name */}
                  <span className="font-sans text-[0.82rem] text-pe-charcoal font-medium min-w-0 truncate">
                    {categoryName(section.rootCategoryId)}
                  </span>

                  {/* Layout */}
                  <span className="font-sans text-[0.72rem] text-pe-muted bg-pe-cream px-2 py-0.5 shrink-0">
                    {LAYOUT_LABELS[section.layout as LayoutOption] ?? section.layout}
                  </span>

                  {/* Column count */}
                  <span className="font-sans text-[0.68rem] text-pe-muted shrink-0">
                    {section.columnCount} col.
                  </span>

                  {/* Sort order */}
                  <span className="font-sans text-[0.65rem] text-pe-muted shrink-0">
                    orden: {section.sortOrder}
                  </span>

                  {/* Actions */}
                  <div className="ml-auto flex items-center gap-1 opacity-100 md:opacity-0 md:group-hover:opacity-100 transition-opacity">
                    <button
                      type="button"
                      onClick={() => handleEdit(section)}
                      className="p-1 text-pe-muted hover:text-pe-rose-ink transition-colors"
                      title="Editar"
                    >
                      <Edit3 size={14} />
                    </button>
                    <button
                      type="button"
                      onClick={() => void handleDelete(section)}
                      className="p-1 text-pe-muted hover:text-red-500 transition-colors"
                      title="Eliminar"
                    >
                      <Trash2 size={14} />
                    </button>
                  </div>
                </div>

                {/* Edit form inline */}
                {editing === section.id && (
                  <div className="px-4 pb-3">
                    <SectionFormPanel
                      form={form}
                      setForm={setForm}
                      saving={saving}
                      categories={categories}
                      token={effectiveToken ?? ''}
                      onSubmit={() => void handleSaveEdit(section.id)}
                      onCancel={handleCancel}
                    />
                  </div>
                )}
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
