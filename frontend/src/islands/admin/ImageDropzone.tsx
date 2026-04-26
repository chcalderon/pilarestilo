import { useState, useRef } from 'react';
import { Upload, ImagePlus, Loader2 } from 'lucide-react';
import { uploadMediaFile } from '../../lib/api';

interface Props {
  value?: string;
  onUpload: (url: string) => void;
  folder: string;
  token: string;
  label?: string;
}

type State = 'idle' | 'dragging' | 'uploading' | 'error';

export default function ImageDropzone({ value, onUpload, folder, token, label }: Props) {
  const [state, setState] = useState<State>('idle');
  const [error, setError] = useState('');
  const [preview, setPreview] = useState(value);
  const inputRef = useRef<HTMLInputElement>(null);

  const upload = async (file: File) => {
    setState('uploading');
    setError('');
    try {
      const url = await uploadMediaFile(file, folder, token);
      setPreview(url);
      onUpload(url);
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
    <div className="flex flex-col gap-1">
      {label && (
        <span className="font-sans text-[0.62rem] uppercase tracking-wider text-pe-charcoal/45">
          {label}
        </span>
      )}
      <div
        onClick={() => !uploading && inputRef.current?.click()}
        onDragEnter={e => { e.preventDefault(); setState('dragging'); }}
        onDragOver={e => { e.preventDefault(); setState('dragging'); }}
        onDragLeave={() => setState('idle')}
        onDrop={onDrop}
        className={[
          'relative cursor-pointer border-2 border-dashed transition-colors select-none',
          dragging ? 'border-pe-rose bg-pe-rose/5' : 'border-pe-black/15 hover:border-pe-rose/40',
          state === 'error' ? 'border-red-400' : '',
        ].join(' ')}
        style={{ minHeight: '96px' }}
      >
        {preview ? (
          <div className="relative w-full" style={{ minHeight: '96px' }}>
            <img
              src={preview}
              alt="Vista previa"
              className="w-full h-24 object-cover"
              loading="lazy"
            />
            <div className="absolute inset-0 flex items-center justify-center bg-black/40 opacity-0 hover:opacity-100 transition-opacity">
              {uploading ? (
                <Loader2 size={20} className="text-white animate-spin" />
              ) : (
                <span className="font-sans text-[0.62rem] uppercase tracking-wider text-white flex items-center gap-1">
                  <Upload size={12} /> Cambiar
                </span>
              )}
            </div>
          </div>
        ) : (
          <div className="flex flex-col items-center justify-center gap-2 p-6 text-pe-charcoal/40">
            {uploading ? (
              <Loader2 size={22} className="animate-spin text-pe-rose" />
            ) : (
              <>
                <ImagePlus size={22} />
                <span className="font-sans text-[0.68rem] text-center">
                  {dragging ? 'Soltá para subir' : 'Arrastrá o hacé clic para subir'}
                </span>
              </>
            )}
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
