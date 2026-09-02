import { useState, useRef, useEffect } from 'react';
import { Upload, Loader2, X, ImageIcon, Camera } from 'lucide-react';
import { uploadMediaFile } from '../../lib/api';

interface Props {
  readonly value?: string;
  readonly onUpload: (url: string) => void;
  readonly onUploadedFile?: (file: File | null) => void;
  readonly folder: string;
  readonly token: string;
  readonly label?: string;
  readonly customUpload?: (file: File) => Promise<string>;
  readonly allowClear?: boolean;
  readonly preserveOriginalFile?: boolean;
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

export default function ImageDropzone({
  value,
  onUpload,
  onUploadedFile,
  folder,
  token,
  label,
  customUpload,
  allowClear = true,
  preserveOriginalFile = false,
}: Props) {
  const [state, setState] = useState<State>('idle');
  const [error, setError] = useState('');
  const [preview, setPreview] = useState(value);
  const [cameraOpen, setCameraOpen] = useState(false);
  const [cameraStarting, setCameraStarting] = useState(false);
  const [cameraError, setCameraError] = useState('');
  const fileInputRef = useRef<HTMLInputElement>(null);
  const cameraInputRef = useRef<HTMLInputElement>(null);
  const cameraVideoRef = useRef<HTMLVideoElement>(null);
  const cameraStreamRef = useRef<MediaStream | null>(null);

  useEffect(() => {
    setPreview(value);
  }, [value]);

  const stopCameraStream = () => {
    if (!cameraStreamRef.current) return;
    for (const track of cameraStreamRef.current.getTracks()) {
      track.stop();
    }
    cameraStreamRef.current = null;
  };

  useEffect(() => {
    return () => {
      stopCameraStream();
    };
  }, []);

  const upload = async (file: File) => {
    setState('uploading');
    setError('');
    try {
      const ready = preserveOriginalFile ? file : await compressImage(file);
      const url = customUpload
        ? await customUpload(ready)
        : await uploadMediaFile(ready, folder, token);
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
    e.currentTarget.value = '';
    if (!file) return;
    // The file input carries no `accept` so Android opens the full file browser (all folders)
    // instead of routing straight to Google Photos, so a non-image can now come back.
    if (file.type && !file.type.startsWith('image/')) {
      setError('Elige un archivo de imagen (JPG, PNG o WebP).');
      setState('error');
      return;
    }
    void upload(file);
  };

  const closeCamera = () => {
    setCameraOpen(false);
    setCameraError('');
    stopCameraStream();
  };

  const openCamera = async () => {
    if (uploading) return;
    setCameraError('');
    setCameraStarting(true);
    if (!navigator.mediaDevices?.getUserMedia) {
      setCameraStarting(false);
      cameraInputRef.current?.click();
      return;
    }
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: { ideal: 'environment' } },
        audio: false,
      });
      cameraStreamRef.current = stream;
      setCameraOpen(true);
      setCameraStarting(false);
    } catch {
      setCameraStarting(false);
      setCameraError('No se pudo abrir la camara. Puedes seleccionar una imagen manualmente.');
      cameraInputRef.current?.click();
    }
  };

  useEffect(() => {
    if (!cameraOpen || !cameraVideoRef.current || !cameraStreamRef.current) return;
    const video = cameraVideoRef.current;
    video.srcObject = cameraStreamRef.current;
    void video.play().catch(() => {});
  }, [cameraOpen]);

  const captureCameraPhoto = async () => {
    const video = cameraVideoRef.current;
    if (!video || !cameraStreamRef.current) return;
    const width = video.videoWidth || 1280;
    const height = video.videoHeight || 960;
    const canvas = document.createElement('canvas');
    canvas.width = width;
    canvas.height = height;
    const ctx = canvas.getContext('2d');
    if (!ctx) {
      setCameraError('No se pudo capturar la foto. Intenta nuevamente.');
      return;
    }
    ctx.drawImage(video, 0, 0, width, height);
    const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/jpeg', 0.92));
    if (!blob) {
      setCameraError('No se pudo capturar la foto. Intenta nuevamente.');
      return;
    }
    const filename = `camera-${Date.now()}.jpg`;
    const capturedFile = new File([blob], filename, { type: 'image/jpeg' });
    closeCamera();
    void upload(capturedFile);
  };

  const dragging = state === 'dragging';
  const uploading = state === 'uploading';

  let idleBorderClass = 'border-pe-black/20 bg-pe-cream/30 hover:border-pe-rose/40';
  if (dragging) {
    idleBorderClass = 'border-pe-rose bg-pe-rose/5 dark:bg-pe-rose/10';
  } else if (state === 'error') {
    idleBorderClass = 'border-pe-danger/40 bg-pe-danger-surface';
  }

  return (
    <div className="flex flex-col gap-1.5">
      {label && (
        <span className="font-sans text-[0.62rem] uppercase tracking-wider text-pe-muted">
          {label}
        </span>
      )}

      {/*
        Fixed-height container — always 192px tall so the form never shifts
        when an image is loaded or removed.
      */}
      <div
        className={[
          'group relative h-48 w-full overflow-hidden select-none',
          !preview
            ? ['border-2 border-dashed transition-colors', idleBorderClass].join(' ')
            : 'bg-pe-cream/40',
        ].join(' ')}
        onDragEnter={e => { e.preventDefault(); setState('dragging'); }}
        onDragOver={e => { e.preventDefault(); setState('dragging'); }}
        onDragLeave={() => setState('idle')}
        onDrop={onDrop}
      >
        {/*
          Click-to-pick is a real <label> for the hidden <input>, not a
          <div role="button"> — the remove button below is a nested control and
          cannot live inside a <button>. Drag-and-drop stays on the wrapper.
        */}
        <label
          className="absolute inset-0 cursor-pointer focus-within:outline-hidden focus-within:ring-2 focus-within:ring-inset focus-within:ring-pe-rose"
          aria-label={preview ? 'Cambiar imagen del producto' : 'Subir imagen del producto'}
        >
          <input
            ref={fileInputRef}
            type="file"
            className="sr-only"
            onChange={onChange}
            disabled={uploading}
          />

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
              <ImageIcon size={28} className="text-pe-muted" strokeWidth={1.25} />
              <span className="font-sans text-[0.68rem] text-pe-muted text-center px-6 leading-relaxed whitespace-pre-line">
                {dragging ? 'Suelta para subir' : 'Toca para elegir una imagen\no arrastra un archivo aqui'}
              </span>
            </div>
          )}

          {/* Hover overlay when image exists — gradient + hint only; remove button is a sibling below */}
          {preview && !uploading && !dragging && (
            <div className="absolute inset-0 flex flex-col justify-end p-2 opacity-0 group-hover:opacity-100 transition-opacity bg-gradient-to-t from-black/50 via-transparent to-transparent pointer-events-none">
              <div className="flex items-center gap-1.5 text-white/75">
                <Upload size={11} />
                <span className="font-sans text-[0.62rem]">Clic o arrastra para cambiar</span>
              </div>
            </div>
          )}

          {/* No-image upload hint overlay on hover */}
          {!preview && !uploading && !dragging && (
            <div className="absolute inset-x-0 bottom-0 flex items-center justify-center gap-1.5 py-1.5 opacity-0 group-hover:opacity-100 transition-opacity pointer-events-none">
              <Upload size={11} className="text-pe-muted" />
              <span className="font-sans text-[0.6rem] text-pe-muted">Clic para seleccionar</span>
            </div>
          )}
        </label>

        {/* Drag-over overlay when image already present */}
        {preview && dragging && (
          <div className="absolute inset-0 flex items-center justify-center border-2 border-dashed border-pe-rose bg-pe-rose/10 pointer-events-none">
            <span className="font-sans text-[0.72rem] text-pe-rose-ink font-medium">Suelta para reemplazar</span>
          </div>
        )}

        {/* Upload spinner */}
        {uploading && (
          <div className="absolute inset-0 flex items-center justify-center bg-black/35">
            <Loader2 size={22} className="text-white animate-spin" />
          </div>
        )}

        {/* Remove — sibling of the <label>, so it is not a <button> inside a <button> */}
        {preview && !uploading && !dragging && allowClear && (
          <button
            type="button"
            onClick={() => {
              setPreview(undefined);
              onUpload('');
              onUploadedFile?.(null);
            }}
            className="absolute right-2 top-2 z-10 bg-black/55 hover:bg-black/80 text-white p-1 opacity-0 group-hover:opacity-100 transition-[opacity,background-color]"
            title="Quitar imagen"
            aria-label="Quitar imagen"
          >
            <X size={13} />
          </button>
        )}
      </div>

      {/*
        Two explicit controls. The dropzone above is a real <label> too, but on a phone that
        affordance is invisible, so people only ever found "Tomar foto" and never the gallery.
      */}
      <div className="grid grid-cols-2 gap-1.5">
        <button
          type="button"
          onClick={() => fileInputRef.current?.click()}
          className="inline-flex items-center justify-center gap-1.5 border border-pe-charcoal/20 text-pe-muted font-sans text-[0.65rem] uppercase tracking-[0.1em] py-1.5 hover:border-pe-rose/50 hover:text-pe-rose-ink transition-colors disabled:opacity-50"
          disabled={uploading || cameraStarting}
        >
          <ImageIcon size={12} />
          Elegir imagen
        </button>

        <button
          type="button"
          onClick={() => void openCamera()}
          className="inline-flex items-center justify-center gap-1.5 border border-pe-charcoal/20 text-pe-muted font-sans text-[0.65rem] uppercase tracking-[0.1em] py-1.5 hover:border-pe-rose/50 hover:text-pe-rose-ink transition-colors disabled:opacity-50"
          disabled={uploading || cameraStarting}
        >
          {cameraStarting ? <Loader2 size={12} className="animate-spin" /> : <Camera size={12} />}
          {cameraStarting ? 'Abriendo camara...' : 'Tomar foto'}
        </button>
      </div>

      {state === 'error' && (
        <p className="font-sans text-[0.65rem] text-pe-danger-ink">{error}</p>
      )}
      {cameraError && (
        <p className="font-sans text-[0.65rem] text-pe-warning-ink">{cameraError}</p>
      )}
      <input
        ref={cameraInputRef}
        type="file"
        accept="image/*"
        capture="environment"
        className="hidden"
        onChange={onChange}
      />

      {cameraOpen && (
        <div className="fixed inset-0 z-[120] bg-black/70 flex items-center justify-center p-4">
          <div className="w-full max-w-[720px] bg-pe-white border border-pe-charcoal/20 p-3 space-y-3">
            <p className="font-sans text-[0.72rem] uppercase tracking-[0.1em] text-pe-muted">
              Camara
            </p>
            <div className="w-full bg-black">
              <video
                ref={cameraVideoRef}
                className="w-full max-h-[65vh] object-contain"
                playsInline
                muted
                autoPlay
              />
            </div>
            <div className="flex gap-2 justify-end">
              <button
                type="button"
                onClick={closeCamera}
                className="px-3 py-1.5 border border-pe-charcoal/20 text-pe-muted font-sans text-[0.64rem] uppercase tracking-[0.1em] hover:border-pe-rose/40 transition-colors"
              >
                Cancelar
              </button>
              <button
                type="button"
                onClick={() => void captureCameraPhoto()}
                className="px-3 py-1.5 border border-pe-rose/40 text-pe-rose-ink font-sans text-[0.64rem] uppercase tracking-[0.1em] hover:bg-pe-rose/10 transition-colors"
              >
                Capturar
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
