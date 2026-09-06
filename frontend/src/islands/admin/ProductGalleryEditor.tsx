import { ArrowUp, ArrowDown, X, Star } from 'lucide-react';
import ImageDropzone from './ImageDropzone';

const MAX_GALLERY = 9;

interface Props {
  readonly value: string[];
  readonly onChange: (next: string[]) => void;
  readonly coverUrl: string;
  readonly onCoverChange: (url: string) => void;
  readonly token: string;
}

export default function ProductGalleryEditor({ value, onChange, coverUrl, onCoverChange, token }: Props) {
  function move(index: number, delta: number) {
    const next = [...value];
    const target = index + delta;
    if (target < 0 || target >= next.length) return;
    [next[index], next[target]] = [next[target], next[index]];
    onChange(next);
  }

  function remove(index: number) {
    onChange(value.filter((_, i) => i !== index));
  }

  function makeCover(index: number) {
    const promoted = value[index];
    const next = [...value];
    next[index] = coverUrl;
    onCoverChange(promoted);
    onChange(next);
  }

  return (
    <div className="space-y-2">
      <span className="text-xs text-pe-muted">Más fotos (galería)</span>
      {value.length > 0 && (
        <ul className="grid grid-cols-3 gap-2 sm:grid-cols-4">
          {value.map((url, i) => (
            <li key={`${url}-${i}`} className="relative rounded-xs border border-pe-border overflow-hidden">
              <img src={url} alt="" className="aspect-4/5 w-full object-cover" />
              <div className="absolute inset-x-0 bottom-0 flex justify-between bg-pe-surface/80 p-1">
                <button
                  type="button"
                  aria-label="Subir foto"
                  onClick={() => move(i, -1)}
                  disabled={i === 0}
                  className="disabled:opacity-30"
                >
                  <ArrowUp size={14} />
                </button>
                <button
                  type="button"
                  aria-label="Bajar foto"
                  onClick={() => move(i, 1)}
                  disabled={i === value.length - 1}
                  className="disabled:opacity-30"
                >
                  <ArrowDown size={14} />
                </button>
                <button type="button" aria-label="Hacer portada" onClick={() => makeCover(i)}>
                  <Star size={14} />
                </button>
                <button type="button" aria-label="Quitar foto" onClick={() => remove(i)}>
                  <X size={14} />
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}
      {value.length < MAX_GALLERY ? (
        <ImageDropzone
          key={value.length}
          folder="products"
          token={token}
          label="Agregar foto"
          ariaLabel="Agregar foto a la galería"
          allowClear={false}
          onUpload={(url) => onChange([...value, url])}
        />
      ) : (
        <p className="text-xs text-pe-muted">Máximo 10 fotos (portada + 9).</p>
      )}
    </div>
  );
}
