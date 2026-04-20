import { useState, useEffect, useRef } from 'react';
import { ImagePlus, Loader2, Save, Upload, X } from 'lucide-react';
import {
  createProduct,
  uploadProductImage,
  updateProduct,
  getCategories,
  type ProductDto,
  type CreateProductRequest,
  type CategoryDto,
} from '../../lib/api';

interface Props {
  product?: ProductDto | null;
  onSave: (saved: ProductDto) => void;
  onCancel: () => void;
  token?: string;
}

const EMPTY_FORM = {
  name: '',
  description: '',
  amount: '',
  currency: 'CLP',
  imageUrl: '',
  condition: 'NEW' as 'NEW' | 'USED',
  brand: '',
  stock: '1',
  active: true,
};

export default function ProductForm({ product, onSave, onCancel, token }: Props) {
  const [form, setForm] = useState({ ...EMPTY_FORM });
  const [selectedCatIds, setSelectedCatIds] = useState<string[]>([]);
  const [categories, setCategories] = useState<CategoryDto[]>([]);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);
  const [uploadingImage, setUploadingImage] = useState(false);
  const [apiError, setApiError] = useState('');
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  useEffect(() => {
    getCategories().then(setCategories).catch(() => {});
  }, []);

  useEffect(() => {
    if (product) {
      setForm({
        name: product.name,
        description: product.description,
        amount: String(product.price.amount),
        currency: product.price.currency,
        imageUrl: product.imageUrl,
        condition: product.condition,
        brand: product.brand,
        stock: String(product.stock),
        active: product.active,
      });
      setSelectedCatIds([]);
    } else {
      setForm({ ...EMPTY_FORM });
      setSelectedCatIds([]);
    }
    setErrors({});
    setApiError('');
  }, [product]);

  useEffect(() => {
    if (product?.categorySlugs && categories.length > 0) {
      const ids = categories.filter((c) => product.categorySlugs!.includes(c.slug)).map((c) => c.id);
      setSelectedCatIds(ids);
    }
  }, [categories, product]);

  function toggleCategory(id: string) {
    setSelectedCatIds((prev) => (prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]));
  }

  function validate(): boolean {
    const e: Record<string, string> = {};
    if (!form.name.trim()) e.name = 'Nombre requerido';
    if (!form.brand.trim()) e.brand = 'Marca requerida';
    if (!form.description.trim()) e.description = 'Descripcion requerida';
    if (!form.amount || isNaN(Number(form.amount)) || Number(form.amount) <= 0) e.amount = 'Precio valido requerido';
    if (!form.stock || isNaN(Number(form.stock)) || Number(form.stock) < 0) e.stock = 'Stock valido requerido';
    setErrors(e);
    return Object.keys(e).length === 0;
  }

  async function handleImageUpload(file: File) {
    if (!token) {
      setApiError('Tu sesion de administracion expiro. Vuelve a iniciar sesion.');
      return;
    }

    setUploadingImage(true);
    setApiError('');
    try {
      const uploaded = await uploadProductImage(file, token);
      setForm((prev) => ({ ...prev, imageUrl: uploaded.url }));
    } catch (err) {
      setApiError(err instanceof Error ? err.message : 'Error al subir imagen');
    } finally {
      setUploadingImage(false);
    }
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!validate()) return;

    if (!token) {
      setApiError('Tu sesion de administracion expiro. Vuelve a iniciar sesion.');
      return;
    }

    setSaving(true);
    setApiError('');

    try {
      const payload: CreateProductRequest = {
        name: form.name.trim(),
        description: form.description.trim(),
        price: { amount: Number(form.amount), currency: form.currency },
        imageUrl: form.imageUrl.trim() || '/api/media/products/product-001.jpg',
        condition: form.condition,
        brand: form.brand.trim(),
        stock: Number(form.stock),
        active: form.active,
        categoryIds: selectedCatIds,
      };

      let saved: ProductDto;
      if (product) {
        saved = await updateProduct(product.id, { ...payload, active: form.active }, token);
      } else {
        saved = await createProduct(payload, token);
      }

      onSave(saved);
    } catch (err) {
      setApiError(err instanceof Error ? err.message : 'Error al guardar');
    } finally {
      setSaving(false);
    }
  }

  const inputClass =
    'w-full font-sans text-sm border border-pe-black/20 px-3 py-2 bg-white focus:outline-none focus:border-pe-rose transition-colors';
  const labelClass = 'block font-sans text-xs tracking-wider uppercase text-pe-black/60 mb-1';
  const errorClass = 'font-sans text-xs text-red-500 mt-1';
  const previewUrl = form.imageUrl.trim() || '/api/media/products/product-001.jpg';

  const rootCats = categories.filter((c) => !c.parentId);
  const childCats = categories.filter((c) => c.parentId);

  return (
    <div className="fixed inset-0 bg-[#1A1A1A]/60 z-50 flex items-center justify-center p-3 sm:p-4" role="dialog" aria-modal="true">
      <div className="bg-[#F8F4EF] w-full max-w-lg max-h-[92vh] overflow-y-auto p-4 sm:p-6 shadow-2xl">
        <div className="flex items-center justify-between mb-6">
          <h2 className="font-['Cormorant_Garamond',serif] text-[#1A1A1A] text-xl font-light">
            {product ? 'Editar Producto' : 'Nuevo Producto'}
          </h2>
          <button
            onClick={onCancel}
            className="inline-flex items-center justify-center w-8 h-8 text-[#3A3A3A]/40 hover:text-[#B76E79] transition-colors"
            aria-label="Cerrar formulario"
          >
            <X size={16} />
          </button>
        </div>

        {apiError && <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-2 mb-4">{apiError}</div>}

        <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-4">
          <div>
            <label htmlFor="pf-name" className={labelClass}>
              Nombre
            </label>
            <input
              id="pf-name"
              type="text"
              className={inputClass}
              value={form.name}
              onChange={(e) => setForm({ ...form, name: e.target.value })}
              required
            />
            {errors.name && <p className={errorClass}>{errors.name}</p>}
          </div>

          <div>
            <label htmlFor="pf-brand" className={labelClass}>
              Marca
            </label>
            <input
              id="pf-brand"
              type="text"
              className={inputClass}
              value={form.brand}
              onChange={(e) => setForm({ ...form, brand: e.target.value })}
              required
            />
            {errors.brand && <p className={errorClass}>{errors.brand}</p>}
          </div>

          <div>
            <label htmlFor="pf-desc" className={labelClass}>
              Descripcion
            </label>
            <textarea
              id="pf-desc"
              className={inputClass + ' resize-none h-20'}
              value={form.description}
              onChange={(e) => setForm({ ...form, description: e.target.value })}
              required
            />
            {errors.description && <p className={errorClass}>{errors.description}</p>}
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label htmlFor="pf-price" className={labelClass}>
                Precio (CLP)
              </label>
              <input
                id="pf-price"
                type="number"
                min="0"
                step="1"
                className={inputClass}
                value={form.amount}
                onChange={(e) => setForm({ ...form, amount: e.target.value })}
                required
              />
              {errors.amount && <p className={errorClass}>{errors.amount}</p>}
            </div>
            <div>
              <label htmlFor="pf-condition" className={labelClass}>
                Condicion
              </label>
              <select
                id="pf-condition"
                className={inputClass}
                value={form.condition}
                onChange={(e) => setForm({ ...form, condition: e.target.value as 'NEW' | 'USED' })}
              >
                <option value="NEW">Nuevo</option>
                <option value="USED">Usado</option>
              </select>
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label htmlFor="pf-stock" className={labelClass}>
                Stock
              </label>
              <input
                id="pf-stock"
                type="number"
                min="0"
                step="1"
                className={inputClass}
                value={form.stock}
                onChange={(e) => setForm({ ...form, stock: e.target.value })}
                required
              />
              {errors.stock && <p className={errorClass}>{errors.stock}</p>}
            </div>
            <div className="flex flex-col justify-end pb-1">
              <label className="flex items-center gap-2 cursor-pointer">
                <input
                  type="checkbox"
                  className="w-4 h-4 accent-[#B76E79]"
                  checked={form.active}
                  onChange={(e) => setForm({ ...form, active: e.target.checked })}
                />
                <span className="font-sans text-sm text-[#1A1A1A]">Activo</span>
              </label>
            </div>
          </div>

          <div>
            <label className={labelClass}>Imagen del producto</label>
            <div className="border border-pe-black/12 bg-pe-white p-3">
              <div className="flex gap-3">
                <img
                  src={previewUrl}
                  alt="Vista previa producto"
                  className="w-20 h-24 object-cover bg-pe-cream border border-pe-black/10"
                  loading="lazy"
                />
                <div className="min-w-0 flex-1">
                  <p className="font-sans text-[0.65rem] uppercase tracking-[0.12em] text-pe-charcoal/45 mb-1">
                    Ruta activa
                  </p>
                  <p className="font-mono text-[0.68rem] text-pe-charcoal/70 truncate">{previewUrl}</p>
                  <div className="flex flex-wrap gap-2 mt-3">
                    <button
                      type="button"
                      onClick={() => fileInputRef.current?.click()}
                      disabled={uploadingImage || saving}
                      className="inline-flex items-center gap-1.5 bg-[#B76E79] text-white font-sans text-[0.66rem] tracking-[0.1em] uppercase px-3 py-2 hover:bg-[#8E4F58] transition-colors disabled:opacity-60"
                    >
                      {uploadingImage ? <Loader2 size={13} className="animate-spin" /> : <Upload size={13} />}
                      {uploadingImage ? 'Subiendo...' : 'Subir imagen'}
                    </button>
                    <button
                      type="button"
                      onClick={() => setForm((prev) => ({ ...prev, imageUrl: '/api/media/products/product-001.jpg' }))}
                      disabled={uploadingImage || saving}
                      className="inline-flex items-center gap-1.5 border border-pe-black/15 text-pe-charcoal font-sans text-[0.66rem] tracking-[0.1em] uppercase px-3 py-2 hover:border-pe-rose hover:text-pe-rose transition-colors disabled:opacity-60"
                    >
                      <ImagePlus size={13} />
                      Imagen por defecto
                    </button>
                  </div>
                  <input
                    ref={fileInputRef}
                    type="file"
                    accept="image/*"
                    className="hidden"
                    onChange={(e) => {
                      const file = e.target.files?.[0];
                      if (file) {
                        void handleImageUpload(file);
                      }
                      e.currentTarget.value = '';
                    }}
                  />
                </div>
              </div>

              <div className="mt-3">
                <label htmlFor="pf-image" className={labelClass}>
                  Ruta manual (opcional)
                </label>
                <input
                  id="pf-image"
                  type="text"
                  className={inputClass}
                  value={form.imageUrl}
                  onChange={(e) => setForm({ ...form, imageUrl: e.target.value })}
                  placeholder="/api/media/products/product-001.jpg"
                />
              </div>
            </div>
          </div>

          {categories.length > 0 && (
            <div>
              <p className={labelClass}>Categorias</p>
              <div className="border border-[#EDE3D8] p-3 max-h-48 overflow-y-auto space-y-3">
                {rootCats.map((root) => (
                  <div key={root.id}>
                    <label className="flex items-center gap-2 cursor-pointer mb-1">
                      <input
                        type="checkbox"
                        className="w-3.5 h-3.5 accent-[#B76E79]"
                        checked={selectedCatIds.includes(root.id)}
                        onChange={() => toggleCategory(root.id)}
                      />
                      <span className="font-sans text-sm font-medium text-[#1A1A1A]">{root.nameEs}</span>
                    </label>
                    <div className="ml-5 space-y-1">
                      {childCats
                        .filter((c) => c.parentId === root.id)
                        .map((child) => (
                          <label key={child.id} className="flex items-center gap-2 cursor-pointer">
                            <input
                              type="checkbox"
                              className="w-3.5 h-3.5 accent-[#B76E79]"
                              checked={selectedCatIds.includes(child.id)}
                              onChange={() => toggleCategory(child.id)}
                            />
                            <span className="font-sans text-xs text-[#3A3A3A]">{child.nameEs}</span>
                          </label>
                        ))}
                    </div>
                  </div>
                ))}
              </div>
              {selectedCatIds.length > 0 && (
                <p className="text-[10px] text-[#B76E79] mt-1">
                  {selectedCatIds.length} {selectedCatIds.length === 1 ? 'categoria seleccionada' : 'categorias seleccionadas'}
                </p>
              )}
            </div>
          )}

          <div className="flex flex-col sm:flex-row gap-3 mt-2">
            <button
              type="submit"
              disabled={saving}
              className="flex-1 inline-flex items-center justify-center gap-1.5 bg-[#B76E79] text-white font-sans text-xs tracking-widest uppercase py-2.5 hover:bg-[#8E4F58] transition-colors disabled:opacity-50"
            >
              {saving ? (
                <>
                  <Loader2 size={14} className="animate-spin" />
                  Guardando...
                </>
              ) : (
                <>
                  <Save size={14} />
                  {product ? 'Guardar Cambios' : 'Crear Producto'}
                </>
              )}
            </button>
            <button
              type="button"
              onClick={onCancel}
              className="flex-1 inline-flex items-center justify-center gap-1.5 border border-[#3A3A3A]/20 text-[#1A1A1A] font-sans text-xs tracking-widest uppercase py-2.5 hover:border-[#B76E79] hover:text-[#B76E79] transition-colors"
            >
              <X size={14} />
              Cancelar
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
