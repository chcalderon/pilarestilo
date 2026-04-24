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
  type ProductVariantDto,
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
  listAmount: '',
  currency: 'CLP',
  imageUrl: '',
  condition: 'NEW' as 'NEW' | 'USED',
  brand: '',
  stock: '1',
  active: true,
};

type VariantSize = ProductVariantDto['size'];
type VariantRow = {
  color: string;
  size: VariantSize;
  stock: string;
};

const VARIANT_SIZES: VariantSize[] = ['XS', 'S', 'M', 'L', 'XL', 'UNICO'];

function normalizeVariantRows(rows: VariantRow[]): ProductVariantDto[] {
  return rows.map((row) => ({
    color: row.color.trim(),
    size: row.size,
    stock: Number(row.stock),
  }));
}

export default function ProductForm({ product, onSave, onCancel, token }: Props) {
  const [form, setForm] = useState({ ...EMPTY_FORM });
  const [useVariants, setUseVariants] = useState(false);
  const [variantRows, setVariantRows] = useState<VariantRow[]>([]);
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
      const existingVariants: VariantRow[] = (product.variants ?? []).map((variant) => ({
        color: variant.color,
        size: variant.size,
        stock: String(variant.stock),
      }));
      setForm({
        name: product.name,
        description: product.description,
        amount: String(product.price.amount),
        listAmount: product.listPrice?.amount != null ? String(product.listPrice.amount) : '',
        currency: product.price.currency,
        imageUrl: product.imageUrl,
        condition: product.condition,
        brand: product.brand,
        stock: String(product.stock),
        active: product.active,
      });
      setUseVariants(existingVariants.length > 0);
      setVariantRows(existingVariants);
      setSelectedCatIds([]);
    } else {
      setForm({ ...EMPTY_FORM });
      setUseVariants(false);
      setVariantRows([]);
      setSelectedCatIds([]);
    }
    setErrors({});
    setApiError('');
  }, [product]);

  useEffect(() => {
    if (!useVariants) return;
    if (variantRows.length > 0) return;
    const baseStock = Number(form.stock);
    setVariantRows([
      {
        color: 'Base',
        size: 'UNICO',
        stock: String(Number.isFinite(baseStock) ? Math.max(baseStock, 0) : 0),
      },
    ]);
  }, [useVariants, variantRows.length, form.stock]);

  useEffect(() => {
    if (product?.categorySlugs && categories.length > 0) {
      const ids = categories.filter((c) => product.categorySlugs!.includes(c.slug)).map((c) => c.id);
      setSelectedCatIds(ids);
    }
  }, [categories, product]);

  function toggleCategory(id: string) {
    setSelectedCatIds((prev) => (prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]));
  }

  function addVariantRow() {
    setVariantRows((prev) => [...prev, { color: '', size: 'UNICO', stock: '0' }]);
  }

  function updateVariantRow(index: number, patch: Partial<VariantRow>) {
    setVariantRows((prev) =>
      prev.map((row, rowIndex) => (rowIndex === index ? { ...row, ...patch } : row))
    );
  }

  function removeVariantRow(index: number) {
    setVariantRows((prev) => prev.filter((_, rowIndex) => rowIndex !== index));
  }

  const variantTotalStock = variantRows.reduce((sum, row) => {
    const value = Number(row.stock);
    if (!Number.isFinite(value) || value < 0) return sum;
    return sum + value;
  }, 0);

  function validate(): boolean {
    const e: Record<string, string> = {};
    if (!form.name.trim()) e.name = 'Nombre requerido';
    if (!form.brand.trim()) e.brand = 'Marca requerida';
    if (!form.description.trim()) e.description = 'Descripcion requerida';
    if (!form.amount || isNaN(Number(form.amount)) || Number(form.amount) <= 0) e.amount = 'Precio valido requerido';
    if (form.listAmount.trim()) {
      if (isNaN(Number(form.listAmount)) || Number(form.listAmount) <= 0) {
        e.listAmount = 'Precio lista valido requerido';
      } else if (!isNaN(Number(form.amount)) && Number(form.listAmount) <= Number(form.amount)) {
        e.listAmount = 'El precio lista debe ser mayor al precio oferta';
      }
    }
    if (useVariants) {
      if (variantRows.length === 0) {
        e.variants = 'Debes agregar al menos una variante';
      } else {
        const seen = new Set<string>();
        for (let idx = 0; idx < variantRows.length; idx += 1) {
          const row = variantRows[idx];
          if (!row.color.trim()) {
            e.variants = `Color requerido en variante ${idx + 1}`;
            break;
          }
          if (!row.stock || isNaN(Number(row.stock)) || Number(row.stock) < 0) {
            e.variants = `Stock valido requerido en variante ${idx + 1}`;
            break;
          }
          const key = `${row.color.trim().toLowerCase()}::${row.size}`;
          if (seen.has(key)) {
            e.variants = 'No se permiten variantes duplicadas (color + talla)';
            break;
          }
          seen.add(key);
        }
      }
    } else if (!form.stock || isNaN(Number(form.stock)) || Number(form.stock) < 0) {
      e.stock = 'Stock valido requerido';
    }
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
      const normalizedVariants = useVariants ? normalizeVariantRows(variantRows) : undefined;
      const payload: CreateProductRequest = {
        name: form.name.trim(),
        description: form.description.trim(),
        price: { amount: Number(form.amount), currency: form.currency },
        ...(form.listAmount.trim()
          ? { listPrice: { amount: Number(form.listAmount), currency: form.currency } }
          : {}),
        imageUrl: form.imageUrl.trim() || '/api/media/products/product-001.jpg',
        condition: form.condition,
        brand: form.brand.trim(),
        stock: useVariants ? variantTotalStock : Number(form.stock),
        active: form.active,
        categoryIds: selectedCatIds,
        variants: normalizedVariants,
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
    'w-full font-sans text-[0.82rem] border border-pe-black/30 px-2.5 py-1.5 bg-[#fffdfa] text-[#1A1A1A] focus:outline-none focus:border-pe-rose focus:ring-1 focus:ring-pe-rose/25 transition-colors';
  const labelClass = 'block font-sans text-[0.68rem] tracking-[0.14em] uppercase text-[#1A1A1A]/70 mb-1';
  const errorClass = 'font-sans text-xs text-red-500 mt-1';
  const previewUrl = form.imageUrl.trim() || '/api/media/products/product-001.jpg';

  const rootCats = categories.filter((c) => !c.parentId);
  const childCats = categories.filter((c) => c.parentId);

  return (
    <div className="fixed inset-0 bg-[#1A1A1A]/68 z-50 flex items-center justify-center p-2 sm:p-4" role="dialog" aria-modal="true">
      <div className="bg-[#F8F4EF] w-full max-w-xl max-h-[92vh] overflow-y-auto p-3 sm:p-5 shadow-2xl border border-pe-black/20">
        <div className="flex items-center justify-between mb-4">
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

        <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-3">
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
              className={inputClass + ' resize-none h-16'}
              value={form.description}
              onChange={(e) => setForm({ ...form, description: e.target.value })}
              required
            />
            {errors.description && <p className={errorClass}>{errors.description}</p>}
          </div>

          <div className="grid grid-cols-2 sm:grid-cols-2 lg:grid-cols-3 gap-2.5">
            <div className="col-span-1">
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
            <div className="col-span-1">
              <label htmlFor="pf-list-price" className={labelClass}>
                Precio lista (tachado)
              </label>
              <input
                id="pf-list-price"
                type="number"
                min="0"
                step="1"
                className={inputClass}
                value={form.listAmount}
                onChange={(e) => setForm({ ...form, listAmount: e.target.value })}
                placeholder="Opcional"
              />
              {errors.listAmount && <p className={errorClass}>{errors.listAmount}</p>}
            </div>
            <div className="col-span-2 sm:col-span-2 lg:col-span-1">
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

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
            <div>
              <label htmlFor="pf-stock" className={labelClass}>
                Stock {useVariants ? '(total variantes)' : ''}
              </label>
              <input
                id="pf-stock"
                type="number"
                min="0"
                step="1"
                className={inputClass}
                value={useVariants ? String(variantTotalStock) : form.stock}
                onChange={(e) => setForm({ ...form, stock: e.target.value })}
                required
                disabled={useVariants}
              />
              {errors.stock && <p className={errorClass}>{errors.stock}</p>}
            </div>
            <div className="flex flex-col justify-end pb-1 gap-2">
              <label className="flex items-center gap-2 cursor-pointer">
                <input
                  type="checkbox"
                  className="w-4 h-4 accent-[#B76E79]"
                  checked={useVariants}
                  onChange={(e) => setUseVariants(e.target.checked)}
                />
                <span className="font-sans text-sm text-[#1A1A1A]">Gestionar por variantes</span>
              </label>
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

          {useVariants && (
            <div className="border border-pe-black/12 bg-pe-white p-3 space-y-3">
              <div className="flex items-center justify-between">
                <p className={labelClass + ' mb-0'}>Variantes (color + talla + stock)</p>
                <button
                  type="button"
                  onClick={addVariantRow}
                  className="inline-flex items-center gap-1.5 border border-[#B76E79]/40 text-[#8E4F58] font-sans text-[0.66rem] tracking-[0.1em] uppercase px-2.5 py-1.5 hover:bg-[#B76E79]/10 transition-colors"
                >
                  + Agregar
                </button>
              </div>

              <div className="space-y-2">
                {variantRows.map((row, index) => (
                  <div key={index} className="grid grid-cols-1 sm:grid-cols-12 gap-2 items-center">
                    <input
                      type="text"
                      className={`sm:col-span-5 ${inputClass}`}
                      placeholder="Color"
                      value={row.color}
                      onChange={(e) => updateVariantRow(index, { color: e.target.value })}
                    />
                    <select
                      className={`sm:col-span-3 ${inputClass}`}
                      value={row.size}
                      onChange={(e) => updateVariantRow(index, { size: e.target.value as VariantSize })}
                    >
                      {VARIANT_SIZES.map((size) => (
                        <option key={size} value={size}>
                          {size}
                        </option>
                      ))}
                    </select>
                    <input
                      type="number"
                      min="0"
                      step="1"
                      className={`sm:col-span-2 ${inputClass}`}
                      value={row.stock}
                      onChange={(e) => updateVariantRow(index, { stock: e.target.value })}
                    />
                    <button
                      type="button"
                      onClick={() => removeVariantRow(index)}
                      className="sm:col-span-2 border border-red-300 text-red-500 text-[0.66rem] uppercase tracking-[0.1em] py-2 hover:bg-red-50 transition-colors"
                    >
                      Quitar
                    </button>
                  </div>
                ))}
              </div>

              <p className="font-sans text-[0.68rem] text-pe-charcoal/60">
                Stock total calculado automaticamente: <strong>{variantTotalStock}</strong>
              </p>
              {errors.variants && <p className={errorClass}>{errors.variants}</p>}
            </div>
          )}

          <div>
            <label className={labelClass}>Imagen del producto</label>
            <div className="border border-pe-black/12 bg-pe-white p-3">
              <div className="flex flex-col gap-3 sm:flex-row">
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

          <div className="flex flex-col sm:flex-row gap-2.5 mt-2">
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
