import { useState, useEffect } from 'react';
import { Plus, Edit3, Trash2, ChevronDown, ChevronRight, Loader2, Check, X, Star, GripVertical } from 'lucide-react';
import {
  DndContext, PointerSensor, useSensor, useSensors,
  type DragEndEvent,
} from '@dnd-kit/core';
import {
  SortableContext, useSortable, verticalListSortingStrategy, arrayMove,
} from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import {
  getCategoryTree, createCategory, updateCategory, deleteCategory, reorderCategories,
  type CategoryTreeNode, type CategoryDto,
} from '../../lib/api';
import { useAuthStore, readAuthTokenCookie } from '../../lib/authStore';
import ImageDropzone from './ImageDropzone';
import { useToast, Toaster } from './Toast';

type EditForm = {
  slug: string; nameEs: string; nameEn: string;
  parentId: string; sortOrder: string; imageUrl: string; active: boolean; featured: boolean;
  menuVisible: boolean; heroImageUrl: string;
  /** No UI control any more -- carried through unchanged so saving doesn't reset it. */
  categoryType: CategoryDto['categoryType'];
};

const EMPTY_FORM: EditForm = {
  slug: '', nameEs: '', nameEn: '', parentId: '', sortOrder: '0', imageUrl: '', active: true, featured: false,
  menuVisible: true, heroImageUrl: '', categoryType: 'GENERIC',
};

function fromDto(dto: CategoryDto): EditForm {
  return {
    slug: dto.slug, nameEs: dto.nameEs, nameEn: dto.nameEn,
    parentId: dto.parentId ?? '', sortOrder: String(dto.sortOrder),
    imageUrl: dto.imageUrl ?? '', active: dto.active, featured: dto.featured,
    menuVisible: dto.menuVisible, heroImageUrl: dto.heroImageUrl ?? '',
    categoryType: dto.categoryType,
  };
}

const INPUT_CLASS = 'font-sans text-[0.78rem] border border-pe-black/12 bg-pe-white px-2 py-1.5 text-pe-charcoal focus:outline-hidden focus:border-pe-rose/50 transition-colors';

function slugify(text: string): string {
  return text
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/(^-+|-+$)/g, '');
}

// ─── FormRow ─────────────────────────────────────────────────────────────────

interface FormRowProps {
  readonly form: EditForm;
  readonly setForm: React.Dispatch<React.SetStateAction<EditForm>>;
  readonly saving: boolean;
  readonly onSubmit: () => void;
  readonly onCancel: () => void;
  readonly token: string | null;
}

function FormRow({ form, setForm, saving, onSubmit, onCancel, token }: FormRowProps) {
  return (
    <div className="bg-pe-cream/50 border border-pe-black/8 p-3 mt-2 flex flex-col gap-3">
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-2">
        <div className="flex flex-col gap-0.5">
          <label className="font-sans text-[0.62rem] uppercase tracking-wider text-pe-muted">Slug *</label>
          <input className={INPUT_CLASS} value={form.slug}
            onChange={e => setForm(f => ({ ...f, slug: slugify(e.target.value) }))} placeholder="ej: zapatos" />
        </div>
        <div className="flex flex-col gap-0.5">
          <label className="font-sans text-[0.62rem] uppercase tracking-wider text-pe-muted">Nombre ES *</label>
          <input className={INPUT_CLASS} value={form.nameEs}
            onChange={e => setForm(f => ({ ...f, nameEs: e.target.value }))} placeholder="Zapatos" />
        </div>
        <div className="flex flex-col gap-0.5">
          <label className="font-sans text-[0.62rem] uppercase tracking-wider text-pe-muted">Nombre EN</label>
          <input className={INPUT_CLASS} value={form.nameEn}
            onChange={e => setForm(f => ({ ...f, nameEn: e.target.value }))} placeholder="Shoes" />
        </div>
        <div className="flex flex-col gap-0.5">
          <label className="font-sans text-[0.62rem] uppercase tracking-wider text-pe-muted">Orden</label>
          <input type="number" min="0" className={INPUT_CLASS} value={form.sortOrder}
            onChange={e => setForm(f => ({ ...f, sortOrder: e.target.value }))} />
        </div>
      </div>
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-2">
        <div className="sm:col-span-3 flex flex-col gap-0.5">
          <label className="font-sans text-[0.62rem] uppercase tracking-wider text-pe-muted">Hero imagen</label>
          <input
            className={INPUT_CLASS}
            value={form.heroImageUrl}
            onChange={e => setForm(f => ({ ...f, heroImageUrl: e.target.value }))}
            placeholder="https://..."
          />
        </div>
      </div>
      <ImageDropzone
        label="Imagen"
        folder="categories"
        value={form.imageUrl || undefined}
        onUpload={url => setForm(f => ({ ...f, imageUrl: url }))}
        token={token ?? ''}
      />
      <div className="flex flex-wrap items-center gap-2">
        <label className="flex items-center gap-1.5 font-sans text-[0.78rem] text-pe-muted cursor-pointer">
          <input type="checkbox" checked={form.active} onChange={e => setForm(f => ({ ...f, active: e.target.checked }))} className="accent-pe-rose" />
          Activa
        </label>
        <label className="flex items-center gap-1.5 font-sans text-[0.78rem] text-pe-muted cursor-pointer">
          <input type="checkbox" checked={form.featured} onChange={e => setForm(f => ({ ...f, featured: e.target.checked }))} className="accent-pe-rose" />
          <Star size={11} className="text-amber-500" /> Destacada en inicio
        </label>
        <label className="flex items-center gap-1.5 font-sans text-[0.78rem] text-pe-muted cursor-pointer">
          <input type="checkbox" checked={form.menuVisible} onChange={e => setForm(f => ({ ...f, menuVisible: e.target.checked }))} className="accent-pe-rose" />
          Visible en menu
        </label>
        <button type="button" onClick={onSubmit} disabled={saving}
          className="flex items-center gap-1 bg-pe-rose-action text-pe-offwhite font-sans text-[0.68rem] uppercase tracking-wider px-3 py-1.5 hover:bg-pe-rose-action-action-deep transition-colors disabled:opacity-50">
          {saving ? <Loader2 size={12} className="animate-spin" /> : <Check size={12} />}
          Guardar
        </button>
        <button type="button" onClick={onCancel}
          className="flex items-center gap-1 border border-pe-black/12 font-sans text-[0.68rem] uppercase tracking-wider px-3 py-1.5 hover:border-pe-charcoal transition-colors text-pe-muted">
          <X size={12} /> Cancelar
        </button>
      </div>
    </div>
  );
}

// ─── CategoryRow ──────────────────────────────────────────────────────────────

interface DragHandleProps {
  listeners?: Record<string, any>;
  attributes?: Record<string, any>;
}

interface CategoryRowProps {
  readonly node: CategoryTreeNode;
  readonly depth: number;
  readonly editing: string | null;
  readonly creating: string | null;
  readonly expanded: Set<string>;
  readonly form: EditForm;
  readonly setForm: React.Dispatch<React.SetStateAction<EditForm>>;
  readonly saving: boolean;
  readonly token: string | null;
  readonly dragHandle?: DragHandleProps;
  readonly setExpanded: React.Dispatch<React.SetStateAction<Set<string>>>;
  readonly setEditing: (id: string | null) => void;
  readonly setCreating: (id: string | null) => void;
  readonly onSaveEdit: (id: string) => void;
  readonly onDelete: (id: string, name: string) => void;
  readonly onCreate: (parentId: string | null) => void;
  readonly onReorder: (items: { id: string; sortOrder: number }[]) => void;
}

function CategoryRow({
  node, depth, editing, creating, expanded, form, setForm, saving, token, dragHandle,
  setExpanded, setEditing, setCreating, onSaveEdit, onDelete, onCreate, onReorder,
}: CategoryRowProps) {
  const isExpanded = expanded.has(node.id);
  const hasChildren = node.children.length > 0;
  const isEditing = editing === node.id;
  const isCreatingChild = creating === node.id;

  const handleCancel = () => { setEditing(null); setCreating(null); };

  const childProps = {
    editing, creating, expanded, form, setForm, saving, token,
    setExpanded, setEditing, setCreating,
    onSaveEdit, onDelete, onCreate, onReorder,
  };

  return (
    <div>
      <div
        className="group flex items-center gap-2 rounded-sm px-2 py-2 hover:bg-pe-cream/40 transition-colors"
        style={{ paddingLeft: `${(depth + 1) * 16}px` }}
      >
        {/* Drag handle */}
        <button
          type="button"
          {...dragHandle?.listeners}
          {...dragHandle?.attributes}
          className="p-0.5 text-pe-muted hover:text-pe-muted transition-colors cursor-grab active:cursor-grabbing touch-none shrink-0"
          title="Arrastrar para reordenar"
          tabIndex={-1}
        >
          <GripVertical size={13} />
        </button>

        <button
          type="button"
          onClick={() => setExpanded(prev => {
            const next = new Set(prev);
            if (next.has(node.id)) {
              next.delete(node.id);
            } else {
              next.add(node.id);
            }
            return next;
          })}
          className={['p-0.5 text-pe-muted hover:text-pe-charcoal transition-colors', hasChildren ? '' : 'invisible'].join(' ')}
        >
          {isExpanded ? <ChevronDown size={13} /> : <ChevronRight size={13} />}
        </button>

        {node.imageUrl ? (
          <img src={node.imageUrl} alt="" className="w-6 h-6 object-cover shrink-0 rounded-xs opacity-80" />
        ) : (
          <span className="w-6 h-6 shrink-0" />
        )}
        <span className={['min-w-0 truncate font-sans text-[0.82rem]', node.active ? 'text-pe-charcoal' : 'text-pe-muted line-through'].join(' ')}>
          {node.nameEs}
        </span>
        <span className="min-w-0 truncate font-sans text-[0.65rem] text-pe-muted ml-1">/{node.slug}</span>
        {!node.active && (
          <span className="font-sans text-[0.6rem] uppercase tracking-wider text-pe-muted bg-pe-cream px-1.5 py-0.5">
            Inactiva
          </span>
        )}
        {!node.menuVisible && (
          <span className="font-sans text-[0.6rem] uppercase tracking-wider text-pe-muted bg-pe-cream px-1.5 py-0.5">
            Oculta menu
          </span>
        )}
        {node.featured && (
          <span title="Destacada en inicio">
            <Star size={11} className="shrink-0 text-amber-400 fill-amber-400" />
          </span>
        )}

        <div className="ml-auto flex items-center gap-1 opacity-100 md:opacity-0 md:group-hover:opacity-100 transition-opacity">
          {depth < 3 && (
            <button
              type="button"
              onClick={() => { setCreating(node.id); setForm({ ...EMPTY_FORM }); setEditing(null); }}
              className="p-1 text-pe-muted hover:text-pe-rose-ink transition-colors"
              title="Agregar subcategoría"
            >
              <Plus size={13} />
            </button>
          )}
          <button
            type="button"
            onClick={() => { setEditing(node.id); setForm(fromDto(node)); setCreating(null); }}
            className="p-1 text-pe-muted hover:text-pe-rose-ink transition-colors"
            title="Editar"
          >
            <Edit3 size={13} />
          </button>
          <button
            type="button"
            onClick={() => onDelete(node.id, node.nameEs)}
            className="p-1 text-pe-muted hover:text-red-500 transition-colors"
            title="Eliminar"
          >
            <Trash2 size={13} />
          </button>
        </div>
      </div>

      {isEditing && (
        <div style={{ paddingLeft: `${(depth + 1) * 16}px` }}>
          <FormRow form={form} setForm={setForm} saving={saving}
            onSubmit={() => onSaveEdit(node.id)} onCancel={handleCancel} token={token} />
        </div>
      )}

      {isExpanded && hasChildren && (
        <SortableContext items={node.children.map(c => c.id)} strategy={verticalListSortingStrategy}>
          {node.children.map(child => (
            <SortableCategoryRow key={child.id} node={child} depth={depth + 1} {...childProps} />
          ))}
        </SortableContext>
      )}

      {isCreatingChild && (
        <div style={{ paddingLeft: `${(depth + 2) * 16}px` }}>
          <p className="font-sans text-[0.65rem] uppercase tracking-wider text-pe-muted mb-1 mt-2 px-2">
            Nueva subcategoría en {node.nameEs}
          </p>
          <FormRow form={form} setForm={setForm} saving={saving}
            onSubmit={() => onCreate(node.id)} onCancel={handleCancel} token={token} />
        </div>
      )}
    </div>
  );
}

// ─── SortableCategoryRow ──────────────────────────────────────────────────────

function SortableCategoryRow(props: Omit<CategoryRowProps, 'dragHandle'>) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({ id: props.node.id });

  return (
    <div
      ref={setNodeRef}
      style={{
        transform: CSS.Transform.toString(transform),
        transition,
        opacity: isDragging ? 0.4 : undefined,
        position: 'relative',
        zIndex: isDragging ? 1 : undefined,
      }}
    >
      <CategoryRow {...props} dragHandle={{ listeners, attributes }} />
    </div>
  );
}

// ─── CategoryTree ─────────────────────────────────────────────────────────────

export default function CategoryTree() {
  const { token } = useAuthStore();
  const effectiveToken = token ?? readAuthTokenCookie();
  const [tree, setTree]       = useState<CategoryTreeNode[]>([]);
  const [loading, setLoading] = useState(true);
  const [expanded, setExpanded] = useState<Set<string>>(new Set());
  const [editing, setEditing]   = useState<string | null>(null);
  const [creating, setCreating] = useState<string | null>(null);
  const [form, setForm]         = useState<EditForm>({ ...EMPTY_FORM });
  const [saving, setSaving]     = useState(false);
  const { toasts, show, dismiss } = useToast();

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 8 } })
  );

  async function loadTree() {
    setLoading(true);
    const data = await getCategoryTree();
    setTree(data);
    setLoading(false);
    const ids = new Set<string>();
    function collect(nodes: CategoryTreeNode[]) { nodes.forEach(n => { ids.add(n.id); collect(n.children); }); }
    collect(data);
    setExpanded(ids);
  }

  useEffect(() => { loadTree(); }, []);

  async function persistReorder(items: { id: string; sortOrder: number }[]) {
    if (!effectiveToken) return;
    try {
      await reorderCategories(items, effectiveToken);
    } catch {
      show('error', 'Error al guardar el nuevo orden.');
      await loadTree();
    }
  }

  function handleDragEnd(event: DragEndEvent) {
    const { active, over } = event;
    if (!over || active.id === over.id) return;

    const activeId = String(active.id);
    const overId = String(over.id);

    // Root level reorder
    const rootIds = tree.map(n => n.id);
    if (rootIds.includes(activeId) && rootIds.includes(overId)) {
      const oldIdx = tree.findIndex(n => n.id === activeId);
      const newIdx = tree.findIndex(n => n.id === overId);
      const reordered = arrayMove(tree, oldIdx, newIdx);
      setTree(reordered);
      void persistReorder(reordered.map((n, i) => ({ id: n.id, sortOrder: i })));
      return;
    }

    // Recursive search: find the parent whose direct children include both ids
    function reorderInTree(nodes: CategoryTreeNode[]): CategoryTreeNode[] | null {
      for (const node of nodes) {
        const childIds = node.children.map(c => c.id);
        if (childIds.includes(activeId) && childIds.includes(overId)) {
          const oldIdx = node.children.findIndex(c => c.id === activeId);
          const newIdx = node.children.findIndex(c => c.id === overId);
          const reorderedChildren = arrayMove(node.children, oldIdx, newIdx);
          void persistReorder(reorderedChildren.map((c, i) => ({ id: c.id, sortOrder: i })));
          return nodes.map(n => n.id === node.id ? { ...n, children: reorderedChildren } : n);
        }
        const updated = reorderInTree(node.children);
        if (updated) return nodes.map(n => n.id === node.id ? { ...n, children: updated } : n);
      }
      return null;
    }

    const updated = reorderInTree(tree);
    if (updated) setTree(updated);
  }

  async function handleSaveEdit(id: string) {
    if (!effectiveToken || !form.slug || !form.nameEs) {
      show('error', 'Slug y Nombre ES son requeridos.'); return;
    }
    setSaving(true);
    try {
      await updateCategory(id, {
        slug: form.slug, nameEs: form.nameEs, nameEn: form.nameEn,
        parentId: form.parentId || undefined, sortOrder: Number(form.sortOrder),
        imageUrl: form.imageUrl || undefined, active: form.active, featured: form.featured,
        menuVisible: form.menuVisible, categoryType: form.categoryType, heroImageUrl: form.heroImageUrl || undefined,
      }, effectiveToken);
      setEditing(null);
      show('success', 'Categoría actualizada.');
      await loadTree();
    } catch (err) {
      show('error', err instanceof Error ? err.message : 'Error al guardar.');
    } finally { setSaving(false); }
  }

  async function handleCreate(parentId: string | null) {
    if (!effectiveToken || !form.slug || !form.nameEs) {
      show('error', 'Slug y Nombre ES son requeridos.'); return;
    }
    setSaving(true);
    try {
      await createCategory({
        slug: form.slug, nameEs: form.nameEs, nameEn: form.nameEn,
        parentId: parentId ?? undefined, sortOrder: Number(form.sortOrder),
        imageUrl: form.imageUrl || undefined,
        active: form.active,
        featured: form.featured,
        menuVisible: form.menuVisible,
        categoryType: form.categoryType,
        heroImageUrl: form.heroImageUrl || undefined,
      }, effectiveToken);
      setCreating(null);
      setForm({ ...EMPTY_FORM });
      show('success', 'Categoría creada.');
      await loadTree();
    } catch (err) {
      show('error', err instanceof Error ? err.message : 'Error al crear categoría.');
    } finally { setSaving(false); }
  }

  async function handleDelete(id: string, name: string) {
    if (!effectiveToken || !confirm(`¿Eliminar categoría "${name}"?\n\nEsta acción no se puede deshacer.`)) return;
    try {
      await deleteCategory(id, effectiveToken);
      show('success', `Categoría "${name}" eliminada.`);
      await loadTree();
    } catch (err) {
      show('error', err instanceof Error ? err.message : 'Error al eliminar la categoría.');
    }
  }

  const handleCancel = () => { setEditing(null); setCreating(null); };

  const rowProps = {
    editing, creating, expanded, form, setForm, saving, token: effectiveToken,
    setExpanded, setEditing, setCreating,
    onSaveEdit: handleSaveEdit, onDelete: handleDelete, onCreate: handleCreate,
    onReorder: persistReorder,
  };

  if (loading) {
    return <div className="flex justify-center py-16"><Loader2 size={24} className="animate-spin text-pe-rose-ink" /></div>;
  }

  return (
    <div>
      <div className="mb-4 flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <p className="font-sans text-[0.72rem] text-pe-muted">{tree.length} categorías raíz</p>
        <button
          type="button"
          onClick={() => { setCreating('__root__'); setForm({ ...EMPTY_FORM }); setEditing(null); }}
          className="inline-flex w-full sm:w-auto items-center justify-center gap-2 bg-pe-rose-action text-pe-offwhite font-sans text-[0.72rem] tracking-[0.14em] uppercase px-4 py-2 hover:bg-pe-rose-action-action-deep transition-colors duration-200"
        >
          <Plus size={13} />
          Nueva categoría raíz
        </button>
      </div>

      {creating === '__root__' && (
        <div className="mb-4">
          <FormRow form={form} setForm={setForm} saving={saving}
            onSubmit={() => handleCreate(null)} onCancel={handleCancel} token={effectiveToken} />
        </div>
      )}

      <DndContext sensors={sensors} onDragEnd={handleDragEnd}>
        <div className="bg-pe-white border border-pe-black/6 shadow-xs py-1">
          {tree.length === 0 ? (
            <p className="font-sans text-[0.82rem] text-pe-muted text-center py-12">
              No hay categorías. Crea la primera.
            </p>
          ) : (
            <SortableContext items={tree.map(n => n.id)} strategy={verticalListSortingStrategy}>
              {tree.map(node => <SortableCategoryRow key={node.id} node={node} depth={0} {...rowProps} />)}
            </SortableContext>
          )}
        </div>
      </DndContext>

      <Toaster toasts={toasts} dismiss={dismiss} />
    </div>
  );
}
