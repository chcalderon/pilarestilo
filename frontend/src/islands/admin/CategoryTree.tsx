import { useState, useEffect } from 'react';
import { Plus, Edit3, Trash2, ChevronDown, ChevronRight, Loader2, Check, X } from 'lucide-react';
import {
  getCategoryTree, createCategory, updateCategory, deleteCategory,
  type CategoryTreeNode, type CategoryDto,
} from '../../lib/api';
import { useAuthStore, readAuthTokenCookie } from '../../lib/authStore';

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

export default function CategoryTree() {
  const { token } = useAuthStore();
  const effectiveToken = token ?? readAuthTokenCookie();
  const [tree, setTree]           = useState<CategoryTreeNode[]>([]);
  const [loading, setLoading]     = useState(true);
  const [expanded, setExpanded]   = useState<Set<string>>(new Set());
  const [editing, setEditing]     = useState<string | null>(null);
  const [creating, setCreating]   = useState<string | null>(null); // parentId or '' for root
  const [form, setForm]           = useState<EditForm>({ ...EMPTY_FORM });
  const [saving, setSaving]       = useState(false);
  const [error, setError]         = useState('');

  async function loadTree() {
    setLoading(true);
    const data = await getCategoryTree();
    setTree(data);
    setLoading(false);
    // Expand all by default
    const ids = new Set<string>();
    function collect(nodes: CategoryTreeNode[]) { nodes.forEach(n => { ids.add(n.id); collect(n.children); }); }
    collect(data);
    setExpanded(ids);
  }

  useEffect(() => { loadTree(); }, []);

  async function handleSaveEdit(id: string) {
    if (!effectiveToken || !form.slug || !form.nameEs) { setError('Slug y nombre ES son requeridos.'); return; }
    setSaving(true); setError('');
    try {
      await updateCategory(id, {
        slug: form.slug, nameEs: form.nameEs, nameEn: form.nameEn,
        parentId: form.parentId || undefined, sortOrder: Number(form.sortOrder),
        imageUrl: form.imageUrl || undefined, active: form.active,
      }, effectiveToken);
      setEditing(null);
      await loadTree();
    } catch { setError('Error al guardar.'); } finally { setSaving(false); }
  }

  async function handleCreate(parentId: string | null) {
    if (!effectiveToken || !form.slug || !form.nameEs) { setError('Slug y nombre ES son requeridos.'); return; }
    setSaving(true); setError('');
    try {
      await createCategory({
        slug: form.slug, nameEs: form.nameEs, nameEn: form.nameEn,
        parentId: parentId ?? undefined, sortOrder: Number(form.sortOrder),
        imageUrl: form.imageUrl || undefined,
      }, effectiveToken);
      setCreating(null);
      setForm({ ...EMPTY_FORM });
      await loadTree();
    } catch { setError('Error al crear categoría.'); } finally { setSaving(false); }
  }

  async function handleDelete(id: string, name: string) {
    if (!effectiveToken || !confirm(`¿Eliminar categoría "${name}"?`)) return;
    try { await deleteCategory(id, effectiveToken); await loadTree(); }
    catch { alert('Error al eliminar.'); }
  }

  const inputClass = 'font-sans text-[0.78rem] border border-pe-black/12 bg-pe-white px-2 py-1.5 text-pe-charcoal focus:outline-none focus:border-pe-rose/50 transition-colors';

  function FormRow({ onSubmit }: { onSubmit: () => void }) {
    return (
      <div className="bg-pe-cream/50 border border-pe-black/8 p-3 mt-2 flex flex-col gap-2">
        {error && <p className="font-sans text-[0.72rem] text-pe-rose-deep">{error}</p>}
        <div className="grid grid-cols-2 sm:grid-cols-3 gap-2">
          <div className="flex flex-col gap-0.5">
            <label className="font-sans text-[0.62rem] uppercase tracking-wider text-pe-charcoal/45">Slug *</label>
            <input className={inputClass} value={form.slug}
              onChange={e => setForm(f => ({ ...f, slug: e.target.value }))} placeholder="ej: zapatos" />
          </div>
          <div className="flex flex-col gap-0.5">
            <label className="font-sans text-[0.62rem] uppercase tracking-wider text-pe-charcoal/45">Nombre ES *</label>
            <input className={inputClass} value={form.nameEs}
              onChange={e => setForm(f => ({ ...f, nameEs: e.target.value }))} placeholder="Zapatos" />
          </div>
          <div className="flex flex-col gap-0.5">
            <label className="font-sans text-[0.62rem] uppercase tracking-wider text-pe-charcoal/45">Nombre EN</label>
            <input className={inputClass} value={form.nameEn}
              onChange={e => setForm(f => ({ ...f, nameEn: e.target.value }))} placeholder="Shoes" />
          </div>
          <div className="flex flex-col gap-0.5">
            <label className="font-sans text-[0.62rem] uppercase tracking-wider text-pe-charcoal/45">Orden</label>
            <input type="number" min="0" className={inputClass} value={form.sortOrder}
              onChange={e => setForm(f => ({ ...f, sortOrder: e.target.value }))} />
          </div>
          <div className="flex flex-col gap-0.5 col-span-2">
            <label className="font-sans text-[0.62rem] uppercase tracking-wider text-pe-charcoal/45">URL imagen</label>
            <input className={inputClass} value={form.imageUrl}
              onChange={e => setForm(f => ({ ...f, imageUrl: e.target.value }))} placeholder="https://…" />
          </div>
        </div>
        <div className="flex items-center gap-3 mt-1">
          <label className="flex items-center gap-1.5 font-sans text-[0.78rem] text-pe-charcoal/70 cursor-pointer">
            <input type="checkbox" checked={form.active} onChange={e => setForm(f => ({ ...f, active: e.target.checked }))} className="accent-pe-rose" />
            Activa
          </label>
          <button onClick={onSubmit} disabled={saving}
            className="flex items-center gap-1 bg-pe-rose text-pe-offwhite font-sans text-[0.68rem] uppercase tracking-wider px-3 py-1.5 hover:bg-pe-rose-deep transition-colors disabled:opacity-50">
            {saving ? <Loader2 size={12} className="animate-spin" /> : <Check size={12} />}
            Guardar
          </button>
          <button onClick={() => { setEditing(null); setCreating(null); setError(''); }}
            className="flex items-center gap-1 border border-pe-black/12 font-sans text-[0.68rem] uppercase tracking-wider px-3 py-1.5 hover:border-pe-charcoal transition-colors text-pe-charcoal/60">
            <X size={12} /> Cancelar
          </button>
        </div>
      </div>
    );
  }

  function CategoryRow({ node, depth = 0 }: { node: CategoryTreeNode; depth?: number }) {
    const isExpanded = expanded.has(node.id);
    const hasChildren = node.children.length > 0;
    const isEditing = editing === node.id;
    const isCreatingChild = creating === node.id;

    return (
      <div>
        <div
          className="flex items-center gap-2 py-2 px-2 hover:bg-pe-cream/40 transition-colors rounded group"
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

          <span className={['font-sans text-[0.82rem]', node.active ? 'text-pe-charcoal' : 'text-pe-charcoal/35 line-through'].join(' ')}>
            {node.nameEs}
          </span>
          <span className="font-sans text-[0.65rem] text-pe-charcoal/35 ml-1">/{node.slug}</span>
          {!node.active && (
            <span className="font-sans text-[0.6rem] uppercase tracking-wider text-pe-charcoal/30 bg-pe-cream px-1.5 py-0.5">
              Inactiva
            </span>
          )}

          <div className="ml-auto flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
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
              onClick={() => { setEditing(node.id); setForm(fromDto(node)); setCreating(null); setError(''); }}
              className="p-1 text-pe-charcoal/40 hover:text-pe-rose transition-colors"
              title="Editar"
            >
              <Edit3 size={13} />
            </button>
            <button
              onClick={() => handleDelete(node.id, node.nameEs)}
              className="p-1 text-pe-charcoal/40 hover:text-red-500 transition-colors"
              title="Eliminar"
            >
              <Trash2 size={13} />
            </button>
          </div>
        </div>

        {isEditing && <div style={{ paddingLeft: `${(depth + 1) * 16}px` }}>
          <FormRow onSubmit={() => handleSaveEdit(node.id)} />
        </div>}

        {isExpanded && hasChildren && node.children.map(child => (
          <CategoryRow key={child.id} node={child} depth={depth + 1} />
        ))}

        {isCreatingChild && (
          <div style={{ paddingLeft: `${(depth + 2) * 16}px` }}>
            <p className="font-sans text-[0.65rem] uppercase tracking-wider text-pe-charcoal/40 mb-1 mt-2 px-2">
              Nueva subcategoría en {node.nameEs}
            </p>
            <FormRow onSubmit={() => handleCreate(node.id)} />
          </div>
        )}
      </div>
    );
  }

  if (loading) {
    return <div className="flex justify-center py-16"><Loader2 size={24} className="animate-spin text-pe-rose/50" /></div>;
  }

  return (
    <div>
      {/* Add root category */}
      <div className="flex items-center justify-between mb-4">
        <p className="font-sans text-[0.72rem] text-pe-charcoal/40">{tree.length} categorías raíz</p>
        <button
          onClick={() => { setCreating('__root__'); setForm({ ...EMPTY_FORM }); setEditing(null); setError(''); }}
          className="flex items-center gap-2 bg-pe-rose text-pe-offwhite font-sans text-[0.72rem] tracking-[0.14em] uppercase px-4 py-2 hover:bg-pe-rose-deep transition-colors duration-200"
        >
          <Plus size={13} />
          Nueva categoría raíz
        </button>
      </div>

      {creating === '__root__' && (
        <div className="mb-4">
          <FormRow onSubmit={() => handleCreate(null)} />
        </div>
      )}

      <div className="bg-pe-white border border-pe-black/6 shadow-sm py-1">
        {tree.length === 0 ? (
          <p className="font-sans text-[0.82rem] text-pe-charcoal/35 text-center py-12">
            No hay categorías. Crea la primera.
          </p>
        ) : (
          tree.map(node => <CategoryRow key={node.id} node={node} />)
        )}
      </div>
    </div>
  );
}
