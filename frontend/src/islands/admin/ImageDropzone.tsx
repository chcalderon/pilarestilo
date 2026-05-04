import { useState, useRef, useEffect } from 'react';
import { Upload, Loader2, X, ImageIcon } from 'lucide-react';
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

      {/*
        Fixed-height container — always 192px tall so the form never shifts
        when an image is loaded or removed.
      */}
      <div
        className={[
          'relative h-48 w-full overflow-hidden cursor-pointer select-none',
          !preview
            ? [
                'border-2 border-dashed transition-colors',
                dragging
                  ? 'border-pe-rose bg-pe-rose/5 dark:bg-pe-rose/10'
                  : state === 'error'
                    ? 'border-red-400 bg-red-50/30 dark:bg-red-900/20'
                    : 'border-pe-black/20 bg-pe-cream/30 hover:border-pe-rose/40 dark:border-[#3F2A2F] dark:bg-[#1F1518] dark:hover:border-[#E4B8BF]/40',
              ].join(' ')
            : 'bg-pe-cream/40 dark:bg-[#0F0A0C]',
        ].join(' ')}
        onClick={() => !uploading && inputRef.current?.click()}
        onDragEnter={e => { e.preventDefault(); setState('dragging'); }}
        onDragOver={e => { e.preventDefault(); setState('dragging'); }}
        onDragLeave={() => setState('idle')}
        onDrop={onDrop}
        role="button"
        aria-label={preview ? 'Cambiar imagen del producto' : 'Subir imagen del producto'}
        tabIndex={0}
        onKeyDown={e => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); inputRef.current?.click(); } }}
      >
        {preview ? (
          /* Image fits container without cropping (preserve full product view) */
          <img
            src={preview}
            alt="Vista previa del producto"
            className="w-full h-full object-contain"
            decoding="async"
          />
        ) : (
          /* Placeholder — shown when no image yet */
          <div className="absolute inset-0 flex flex-col items-center justify-center gap-2 pointer-events-none">
            <ImageIcon size={28} className="text-pe-charcoal/20 dark:text-[#D6C8B5]/30" strokeWidth={1.25} />
            <span className="font-sans text-[0.68rem] text-pe-charcoal/35 dark:text-[#D6C8B5]/55 text-center px-6 leading-relaxed whitespace-pre-line">
              {dragging ? 'Suelta para subir' : 'Arrastra una imagen\no haz clic para seleccionar'}
            </span>
          </div>
        )}

        {/* Drag-over overlay when image already present */}
        {preview && dragging && (
          <div className="absolute inset-0 flex items-center justify-center border-2 border-dashed border-pe-rose bg-pe-rose/10 pointer-events-none">
            <span className="font-sans text-[0.72rem] text-pe-rose font-medium">Suelta para reemplazar</span>
          </div>
        )}

        {/* Upload spinner */}
        {uploading && (
          <div className="absolute inset-0 flex items-center justify-center bg-black/35">
            <Loader2 size={22} className="text-white animate-spin" />
          </div>
        )}

        {/* Controls overlay — visible on hover when image exists */}
        {preview && !uploading && !dragging && (
          <div className="absolute inset-0 flex flex-col justify-between p-2 opacity-0 hover:opacity-100 transition-opacity bg-gradient-to-t from-black/50 via-transparent to-transparent">
            <div className="flex justify-end">
              <button
                type="button"
                onClick={e => {
                  e.stopPropagation();
                  setPreview(undefined);
                  onUpload('');
                  onUploadedFile?.(null);
                }}
                className="bg-black/55 hover:bg-black/80 text-white p-1 transition-colors"
                title="Quitar imagen"
                aria-label="Quitar imagen"
              >
                <X size={13} />
              </button>
            </div>
            <div className="flex items-center gap-1.5 text-white/75">
              <Upload size={11} />
              <span className="font-sans text-[0.62rem]">Clic o arrastra para cambiar</span>
            </div>
          </div>
        )}

        {/* No-image upload hint overlay on hover */}
        {!preview && !uploading && !dragging && (
          <div className="absolute inset-x-0 bottom-0 flex items-center justify-center gap-1.5 py-1.5 opacity-0 hover:opacity-100 transition-opacity pointer-events-none">
            <Upload size={11} className="text-pe-charcoal/40 dark:text-[#D6C8B5]/55" />
            <span className="font-sans text-[0.6rem] text-pe-charcoal/40 dark:text-[#D6C8B5]/55">Clic para seleccionar</span>
          </div>
        )}
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
