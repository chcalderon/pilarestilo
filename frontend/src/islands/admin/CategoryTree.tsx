import { useState, useEffect } from 'react';
import { Plus, Edit3, Trash2, ChevronDown, ChevronRight, Loader2, Check, X } from 'lucide-react';
import {
  getCategoryTree, createCategory, updateCategory, deleteCategory,
  type CategoryTreeNode, type CategoryDto,
} from '../../lib/api';
import { useAuthStore, readAuthTokenCookie } from '../../lib/authStore';
import ImageDropzone from './ImageDropzone';
import { useToast, Toaster } from './Toast';

type EditForm = {
  slug: string; nameEs: string; nameEn: string;
  parentId: string; sortOrder: string; imageUrl: string; active: boolean;
};

const EMPTY_FORM: EditForm = {
  slug: '', nameEs: '', nameEn: '', parentId: '', sortOrder: '0', imageUrl: '', active: true,
};

function fromDto(dto: CategoryDto): EditForm {
  return {
    slug: dto.slug, nameEs: dto.nameEs, nameEn: dto.nameEn,
    parentId: dto.parentId ?? '', sortOrder: String(dto.sortOrder),
    imageUrl: dto.imageUrl ?? '', active: dto.active,
  };
}

const INPUT_CLASS = 'font-sans text-[0.78rem] border border-pe-black/12 bg-pe-white px-2 py-1.5 text-pe-charcoal focus:outline-none focus:border-pe-rose/50 transition-colors';

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
  form: EditForm;
  setForm: React.Dispatch<React.SetStateAction<EditForm>>;
  saving: boolean;
  onSubmit: () => void;
  onCancel: () => void;
  token: string | null;
}

function FormRow({ form, setForm, saving, onSubmit, onCancel, token }: FormRowProps) {
  return (
    <div className="bg-pe-cream/50 border border-pe-black/8 p-3 mt-2 flex flex-col gap-3">
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-2">
        <div className="flex flex-col gap-0.5">
          <label className="font-sans text-[0.62rem] uppercase tracking-wider text-pe-charcoal/45">Slug *</label>
          <input className={INPUT_CLASS} value={form.slug}
            onChange={e => setForm(f => ({ ...f, slug: slugify(e.target.value) }))} placeholder="ej: zapatos" />
        </div>
        <div className="flex flex-col gap-0.5">
          <label className="font-sans text-[0.62rem] uppercase tracking-wider text-pe-charcoal/45">Nombre ES *</label>
          <input className={INPUT_CLASS} value={form.nameEs}
            onChange={e => setForm(f => ({ ...f, nameEs: e.target.value }))} placeholder="Zapatos" />
        </div>
        <div className="flex flex-col gap-0.5">
          <label className="font-sans text-[0.62rem] uppercase tracking-wider text-pe-charcoal/45">Nombre EN</label>
          <input className={INPUT_CLASS} value={form.nameEn}
            onChange={e => setForm(f => ({ ...f, nameEn: e.target.value }))} placeholder="Shoes" />
        </div>
        <div className="flex flex-col gap-0.5">
          <label className="font-sans text-[0.62rem] uppercase tracking-wider text-pe-charcoal/45">Orden</label>
          <input type="number" min="0" className={INPUT_CLASS} value={form.sortOrder}
            onChange={e => setForm(f => ({ ...f, sortOrder: e.target.value }))} />
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
        <label className="flex items-center gap-1.5 font-sans text-[0.78rem] text-pe-charcoal/70 cursor-pointer">
          <input type="checkbox" checked={form.active} onChange={e => setForm(f => ({ ...f, active: e.target.checked }))} className="accent-pe-rose" />
          Activa
        </label>
        <button onClick={onSubmit} disabled={saving}
          className="flex items-center gap-1 bg-pe-rose text-pe-offwhite font-sans text-[0.68rem] uppercase tracking-wider px-3 py-1.5 hover:bg-pe-rose-deep transition-colors disabled:opacity-50">
          {saving ? <Loader2 size={12} className="animate-spin" /> : <Check size={12} />}
          Guardar
        </button>
        <button onClick={onCancel}
          className="flex items-center gap-1 border border-pe-black/12 font-sans text-[0.68rem] uppercase tracking-wider px-3 py-1.5 hover:border-pe-charcoal transition-colors text-pe-charcoal/60">
          <X size={12} /> Cancelar
        </button>
      </div>
    </div>
  );
}

// ─── CategoryRow ──────────────────────────────────────────────────────────────

interface CategoryRowProps {
  node: CategoryTreeNode;
  depth: number;
  editing: string | null;
  creating: string | null;
  expanded: Set<string>;
  form: EditForm;
  setForm: React.Dispatch<React.SetStateAction<EditForm>>;
  saving: boolean;
  token: string | null;
  setExpanded: React.Dispatch<React.SetStateAction<Set<string>>>;
  setEditing: (id: string | null) => void;
  setCreating: (id: string | null) => void;
  onSaveEdit: (id: string) => void;
  onDelete: (id: string, name: string) => void;
  onCreate: (parentId: string | null) => void;
}

function CategoryRow({
  node, depth, editing, creating, expanded, form, setForm, saving, token,
  setExpanded, setEditing, setCreating, onSaveEdit, onDelete, onCreate,
}: CategoryRowProps) {
  const isExpanded = expanded.has(node.id);
  const hasChildren = node.children.length > 0;
  const isEditing = editing === node.id;
  const isCreatingChild = creating === node.id;

  const handleCancel = () => { setEditing(null); setCreating(null); };

  return (
    <div>
      <div
        className="group flex items-center gap-2 rounded px-2 py-2 hover:bg-pe-cream/40 transition-colors"
        style={{ paddingLeft: `${(depth + 1) * 16}px` }}
      >
        <button
          onClick={() => setExpanded(prev => {
            const next = new Set(prev);
            next.has(node.id) ? next.delete(node.id) : next.add(node.id);
            return next;
          })}
          className={['p-0.5 text-pe-charcoal/30 hover:text-pe-charcoal transition-colors', hasChildren ? '' : 'invisible'].join(' ')}
        >
          {isExpanded ? <ChevronDown size={13} /> : <ChevronRight size={13} />}
        </button>

        {node.imageUrl ? (
          <img src={node.imageUrl} alt="" className="w-6 h-6 object-cover shrink-0 rounded-sm opacity-80" />
        ) : (
          <span className="w-6 h-6 shrink-0" />
        )}
        <span className={['min-w-0 truncate font-sans text-[0.82rem]', node.active ? 'text-pe-charcoal' : 'text-pe-charcoal/35 line-through'].join(' ')}>
          {node.nameEs}
        </span>
        <span className="min-w-0 truncate font-sans text-[0.65rem] text-pe-charcoal/35 ml-1">/{node.slug}</span>
        {!node.active && (
          <span className="font-sans text-[0.6rem] uppercase tracking-wider text-pe-charcoal/30 bg-pe-cream px-1.5 py-0.5">
            Inactiva
          </span>
        )}

        <div className="ml-auto flex items-center gap-1 opacity-100 md:opacity-0 md:group-hover:opacity-100 transition-opacity">
          {depth === 0 && (
            <button
              onClick={() => { setCreating(node.id); setForm({ ...EMPTY_FORM }); setEditing(null); }}
              className="p-1 text-pe-charcoal/40 hover:text-pe-rose transition-colors"
              title="Agregar subcategoría"
            >
              <Plus size={13} />
            </button>
          )}
          <button
            onClick={() => { setEditing(node.id); setForm(fromDto(node)); setCreating(null); }}
            className="p-1 text-pe-charcoal/40 hover:text-pe-rose transition-colors"
            title="Editar"
          >
            <Edit3 size={13} />
          </button>
          <button
            onClick={() => onDelete(node.id, node.nameEs)}
            className="p-1 text-pe-charcoal/40 hover:text-red-500 transition-colors"
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

      {isExpanded && hasChildren && node.children.map(child => (
        <CategoryRow key={child.id} node={child} depth={depth + 1}
          editing={editing} creating={creating} expanded={expanded}
          form={form} setForm={setForm} saving={saving} token={token}
          setExpanded={setExpanded} setEditing={setEditing} setCreating={setCreating}
          onSaveEdit={onSaveEdit} onDelete={onDelete} onCreate={onCreate} />
      ))}

      {isCreatingChild && (
        <div style={{ paddingLeft: `${(depth + 2) * 16}px` }}>
          <p className="font-sans text-[0.65rem] uppercase tracking-wider text-pe-charcoal/40 mb-1 mt-2 px-2">
            Nueva subcategoría en {node.nameEs}
          </p>
          <FormRow form={form} setForm={setForm} saving={saving}
            onSubmit={() => onCreate(node.id)} onCancel={handleCancel} token={token} />
        </div>
      )}
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

  async function handleSaveEdit(id: string) {
    if (!effectiveToken || !form.slug || !form.nameEs) {
      show('error', 'Slug y Nombre ES son requeridos.'); return;
    }
    setSaving(true);
    try {
      await updateCategory(id, {
        slug: form.slug, nameEs: form.nameEs, nameEn: form.nameEn,
        parentId: form.parentId || undefined, sortOrder: Number(form.sortOrder),
        imageUrl: form.imageUrl || undefined, active: form.active,
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
  };

  if (loading) {
    return <div className="flex justify-center py-16"><Loader2 size={24} className="animate-spin text-pe-rose/50" /></div>;
  }

  return (
    <div>
      <div className="mb-4 flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <p className="font-sans text-[0.72rem] text-pe-charcoal/40">{tree.length} categorías raíz</p>
        <button
          onClick={() => { setCreating('__root__'); setForm({ ...EMPTY_FORM }); setEditing(null); }}
          className="inline-flex w-full sm:w-auto items-center justify-center gap-2 bg-pe-rose text-pe-offwhite font-sans text-[0.72rem] tracking-[0.14em] uppercase px-4 py-2 hover:bg-pe-rose-deep transition-colors duration-200"
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

      <div className="bg-pe-white border border-pe-black/6 shadow-sm py-1">
        {tree.length === 0 ? (
          <p className="font-sans text-[0.82rem] text-pe-charcoal/35 text-center py-12">
            No hay categorías. Crea la primera.
          </p>
        ) : (
          tree.map(node => <CategoryRow key={node.id} node={node} depth={0} {...rowProps} />)
        )}
      </div>

      <Toaster toasts={toasts} dismiss={dismiss} />
    </div>
  );
}
