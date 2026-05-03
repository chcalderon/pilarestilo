import { useState, useRef, useEffect } from 'react';
import { Upload, Loader2, X } from 'lucide-react';
import { uploadMediaFile } from '../../lib/api';

interface Props {
  value?: string;
  onUpload: (url: string) => void;
  onUploadedFile?: (file: File | null) => void;
  folder: string;
  token: string;
  label?: string;
}

type State = 'idle' | 'dragging' | 'uploading' | 'error';

const MAX_DIMENSION = 1400;
const JPEG_QUALITY = 0.85;
const COMPRESS_THRESHOLD = 600 * 1024; // 600 KB

async function compressImage(file: File): Promise<File> {
  if (file.size <= COMPRESS_THRESHOLD && !file.type.startsWith('image/')) return file;
  return new Promise((resolve) => {
    const img = new Image();
    const url = URL.createObjectURL(file);
    img.onload = () => {
      URL.revokeObjectURL(url);
      let { width, height } = img;
      if (width <= MAX_DIMENSION && height <= MAX_DIMENSION && file.size <= COMPRESS_THRESHOLD) {
        resolve(file);
        return;
      }
      const ratio = Math.min(MAX_DIMENSION / width, MAX_DIMENSION / height, 1);
      width = Math.round(width * ratio);
      height = Math.round(height * ratio);
      const canvas = document.createElement('canvas');
      canvas.width = width;
      canvas.height = height;
      const ctx = canvas.getContext('2d')!;
      ctx.drawImage(img, 0, 0, width, height);
      canvas.toBlob(
        (blob) => {
          if (!blob) { resolve(file); return; }
          resolve(new File([blob], file.name.replace(/\.[^.]+$/, '.jpg'), { type: 'image/jpeg' }));
        },
        'image/jpeg',
        JPEG_QUALITY,
      );
    };
    img.onerror = () => { URL.revokeObjectURL(url); resolve(file); };
    img.src = url;
  });
}

export default function ImageDropzone({ value, onUpload, onUploadedFile, folder, token, label }: Props) {
  const [state, setState] = useState<State>('idle');
  const [error, setError] = useState('');
  const [preview, setPreview] = useState(value);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    setPreview(value);
  }, [value]);

  const upload = async (file: File) => {
    setState('uploading');
    setError('');
    try {
      const ready = await compressImage(file);
      const url = await uploadMediaFile(ready, folder, token);
      setPreview(url);
      onUpload(url);
      onUploadedFile?.(ready);
      setState('idle');
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Error al subir imagen');
      setState('error');
    }
  };

  const onDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setState('idle');
    const file = e.dataTransfer.files[0];
    if (file) void upload(file);
  };

  const onChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) void upload(file);
    e.currentTarget.value = '';
  };

  const dragging = state === 'dragging';
  const uploading = state === 'uploading';

  return (
    <div className="flex flex-col gap-1.5">
      {label && (
        <span className="font-sans text-[0.62rem] uppercase tracking-wider text-pe-charcoal/45">
          {label}
        </span>
      )}

      {preview && (
        <div className="relative w-full bg-pe-cream/60">
          <img
            src={preview}
            alt="Vista producto"
            className="w-full max-h-48 object-contain"
            loading="lazy"
          />
          {uploading && (
            <div className="absolute inset-0 flex items-center justify-center bg-black/30">
              <Loader2 size={22} className="text-white animate-spin" />
            </div>
          )}
          <button
            type="button"
            onClick={() => { setPreview(undefined); onUpload(''); onUploadedFile?.(null); }}
            className="absolute top-1.5 right-1.5 bg-black/50 hover:bg-black/70 text-white p-0.5 transition-colors"
            title="Quitar imagen"
          >
            <X size={12} />
          </button>
        </div>
      )}

      {/* Drop zone — always visible for uploading/changing */}
      <div
        onClick={() => !uploading && inputRef.current?.click()}
        onDragEnter={e => { e.preventDefault(); setState('dragging'); }}
        onDragOver={e => { e.preventDefault(); setState('dragging'); }}
        onDragLeave={() => setState('idle')}
        onDrop={onDrop}
        className={[
          'cursor-pointer border-2 border-dashed transition-colors select-none',
          'flex items-center justify-center gap-2 px-4 py-3',
          dragging ? 'border-pe-rose bg-pe-rose/5' : 'border-pe-black/15 hover:border-pe-rose/40',
          state === 'error' ? 'border-red-400' : '',
        ].join(' ')}
      >
        {uploading ? (
          <Loader2 size={16} className="animate-spin text-pe-rose" />
        ) : (
          <Upload size={14} className="text-pe-charcoal/35 shrink-0" />
        )}
        <span className="font-sans text-[0.68rem] text-pe-charcoal/45">
          {uploading
            ? 'Subiendo...'
            : dragging
              ? 'Suelta para subir'
              : preview
                ? 'Arrastra o haz clic para cambiar'
                : 'Arrastra o haz clic para subir'}
        </span>
      </div>

      {state === 'error' && (
        <p className="font-sans text-[0.65rem] text-red-500">{error}</p>
      )}
      <input
        ref={inputRef}
        type="file"
        accept="image/*"
        className="hidden"
        onChange={onChange}
      />
    </div>
  );
}
