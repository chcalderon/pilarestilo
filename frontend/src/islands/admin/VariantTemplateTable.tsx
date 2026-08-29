import { useEffect, useState } from 'react';
import { Plus, Edit3, Trash2, Loader2, Check, X } from 'lucide-react';
import {
  getVariantTemplates, createVariantTemplate, updateVariantTemplate, deleteVariantTemplate,
  type VariantTemplateDto, type VariantFieldDto,
} from '../../lib/api';
import { useAuthStore, readAuthTokenCookie } from '../../lib/authStore';
import { useToast, Toaster } from './Toast';
import VariantFieldEditor from './VariantFieldEditor';

type EditForm = {
  name: string;
  primary: VariantFieldDto;
  secondary: VariantFieldDto;
};

const EMPTY_FIELD: VariantFieldDto = {
  label: '', inputType: 'FREE_TEXT', options: [], min: null, max: null, allowMultiple: true, allowCustom: true,
};

const EMPTY_FORM: EditForm = { name: '', primary: EMPTY_FIELD, secondary: EMPTY_FIELD };

function fromDto(dto: VariantTemplateDto): EditForm {
  return { name: dto.name, primary: dto.config.primary, secondary: dto.config.secondary };
}

const INPUT_CLASS = 'font-sans text-[0.78rem] border border-pe-black/12 bg-pe-white px-2 py-1.5 text-pe-charcoal focus:outline-hidden focus:border-pe-rose/50 transition-colors';

interface FormRowProps {
  readonly form: EditForm;
  readonly setForm: React.Dispatch<React.SetStateAction<EditForm>>;
  readonly saving: boolean;
  readonly onSubmit: () => void;
  readonly onCancel: () => void;
}

function FormRow({ form, setForm, saving, onSubmit, onCancel }: FormRowProps) {
  return (
    <div className="bg-pe-cream/50 border border-pe-black/8 p-3 mt-2 flex flex-col gap-3">
      <div className="flex flex-col gap-0.5">
        <label htmlFor="vt-name" className="font-sans text-[0.62rem] uppercase tracking-wider text-pe-muted">Nombre *</label>
        <input id="vt-name" className={INPUT_CLASS} value={form.name}
          onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))} placeholder="ej: Zapatos" />
      </div>
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
        <VariantFieldEditor fieldNumber={1} field={form.primary} onChange={(next) => setForm((f) => ({ ...f, primary: next }))} />
        <VariantFieldEditor fieldNumber={2} field={form.secondary} onChange={(next) => setForm((f) => ({ ...f, secondary: next }))} />
      </div>
      <div className="flex flex-wrap items-center gap-2">
        <button type="button" onClick={onSubmit} disabled={saving}
          className="flex items-center gap-1 bg-pe-rose-action text-pe-offwhite font-sans text-[0.68rem] uppercase tracking-wider px-3 py-1.5 hover:bg-pe-rose-deep transition-colors disabled:opacity-50">
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

export default function VariantTemplateTable() {
  const { token } = useAuthStore();
  const effectiveToken = token ?? readAuthTokenCookie();
  const [templates, setTemplates] = useState<VariantTemplateDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [editing, setEditing] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);
  const [form, setForm] = useState<EditForm>({ ...EMPTY_FORM });
  const [saving, setSaving] = useState(false);
  const { toasts, show, dismiss } = useToast();

  async function loadTemplates() {
    if (!effectiveToken) {
      setLoading(false);
      return;
    }
    setLoading(true);
    const data = await getVariantTemplates(effectiveToken);
    setTemplates(data);
    setLoading(false);
  }

  useEffect(() => { loadTemplates(); }, []);

  async function handleSaveEdit(id: string) {
    if (!effectiveToken || !form.name.trim()) {
      show('error', 'Nombre es requerido.'); return;
    }
    setSaving(true);
    try {
      await updateVariantTemplate(id, { name: form.name, primary: form.primary, secondary: form.secondary }, effectiveToken);
      setEditing(null);
      show('success', 'Tipo de variante actualizado.');
      await loadTemplates();
    } catch (err) {
      show('error', err instanceof Error ? err.message : 'Error al guardar.');
    } finally { setSaving(false); }
  }

  async function handleCreate() {
    if (!effectiveToken || !form.name.trim()) {
      show('error', 'Nombre es requerido.'); return;
    }
    setSaving(true);
    try {
      await createVariantTemplate({ name: form.name, primary: form.primary, secondary: form.secondary }, effectiveToken);
      setCreating(false);
      setForm({ ...EMPTY_FORM });
      show('success', 'Tipo de variante creado.');
      await loadTemplates();
    } catch (err) {
      show('error', err instanceof Error ? err.message : 'Error al crear tipo de variante.');
    } finally { setSaving(false); }
  }

  async function handleDelete(id: string, name: string) {
    if (!effectiveToken || !confirm(`¿Eliminar tipo de variante "${name}"?\n\nEsta acción no se puede deshacer.`)) return;
    try {
      await deleteVariantTemplate(id, effectiveToken);
      show('success', `Tipo de variante "${name}" eliminado.`);
      await loadTemplates();
    } catch (err) {
      show('error', err instanceof Error ? err.message : 'Error al eliminar el tipo de variante.');
    }
  }

  const handleCancel = () => { setEditing(null); setCreating(false); };

  if (loading) {
    return <div className="flex justify-center py-16"><Loader2 size={24} className="animate-spin text-pe-rose-ink" /></div>;
  }

  return (
    <div>
      <div className="mb-4 flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <p className="font-sans text-[0.72rem] text-pe-muted">{templates.length} tipos de variante</p>
        <button
          type="button"
          onClick={() => { setCreating(true); setForm({ ...EMPTY_FORM }); setEditing(null); }}
          className="inline-flex w-full sm:w-auto items-center justify-center gap-2 bg-pe-rose-action text-pe-offwhite font-sans text-[0.72rem] tracking-[0.14em] uppercase px-4 py-2 hover:bg-pe-rose-deep transition-colors duration-200"
        >
          <Plus size={13} />
          Nuevo tipo de variante
        </button>
      </div>

      {creating && (
        <div className="mb-4">
          <FormRow form={form} setForm={setForm} saving={saving} onSubmit={handleCreate} onCancel={handleCancel} />
        </div>
      )}

      <div className="bg-pe-white border border-pe-black/6 shadow-xs py-1">
        {templates.length === 0 ? (
          <p className="font-sans text-[0.82rem] text-pe-muted text-center py-12">
            No hay tipos de variante. Crea el primero.
          </p>
        ) : (
          templates.map((t) => (
            <div key={t.id}>
              <div className="group flex items-center gap-2 rounded-sm px-2 py-2 hover:bg-pe-cream/40 transition-colors">
                <span className="min-w-0 truncate font-sans text-[0.82rem] text-pe-charcoal">{t.name}</span>
                <span
                  className="font-sans text-[0.58rem] uppercase tracking-[0.12em] text-pe-muted bg-pe-cream px-1.5 py-0.5 ml-1"
                  title={`${t.config.primary.label} / ${t.config.secondary.label}`}
                >
                  {t.config.primary.label} / {t.config.secondary.label}
                </span>
                <div className="ml-auto flex items-center gap-1 opacity-100 md:opacity-0 md:group-hover:opacity-100 transition-opacity">
                  <button
                    type="button"
                    onClick={() => { setEditing(t.id); setForm(fromDto(t)); setCreating(false); }}
                    className="p-1 text-pe-muted hover:text-pe-rose-ink transition-colors"
                    title="Editar"
                  >
                    <Edit3 size={13} />
                  </button>
                  <button
                    type="button"
                    onClick={() => handleDelete(t.id, t.name)}
                    className="p-1 text-pe-muted hover:text-red-500 transition-colors"
                    title="Eliminar"
                  >
                    <Trash2 size={13} />
                  </button>
                </div>
              </div>
              {editing === t.id && (
                <div className="px-2">
                  <FormRow form={form} setForm={setForm} saving={saving}
                    onSubmit={() => handleSaveEdit(t.id)} onCancel={handleCancel} />
                </div>
              )}
            </div>
          ))
        )}
      </div>

      <Toaster toasts={toasts} dismiss={dismiss} />
    </div>
  );
}
