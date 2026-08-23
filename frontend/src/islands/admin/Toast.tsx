import { useState, useCallback } from 'react';
import { CheckCircle, XCircle, X } from 'lucide-react';

export type ToastType = 'success' | 'error';

export interface ToastItem {
  id: number;
  type: ToastType;
  message: string;
}

let _nextId = 1;

export function useToast() {
  const [toasts, setToasts] = useState<ToastItem[]>([]);

  const show = useCallback((type: ToastType, message: string) => {
    const id = _nextId++;
    setToasts(prev => [...prev, { id, type, message }]);
    setTimeout(() => setToasts(prev => prev.filter(t => t.id !== id)), 5000);
  }, []);

  const dismiss = useCallback((id: number) => {
    setToasts(prev => prev.filter(t => t.id !== id));
  }, []);

  return { toasts, show, dismiss };
}

interface Props {
  readonly toasts: ToastItem[];
  readonly dismiss: (id: number) => void;
}

export function Toaster({ toasts, dismiss }: Props) {
  if (toasts.length === 0) return null;
  return (
    <div className="fixed bottom-4 right-4 z-50 flex flex-col gap-2 max-w-sm w-full pointer-events-none">
      {toasts.map(t => (
        <div
          key={t.id}
          className={[
            'flex items-start gap-2 px-3 py-2.5 shadow-lg pointer-events-auto',
            t.type === 'error'
              ? 'bg-red-50 border border-red-200 text-red-800'
              : 'bg-green-50 border border-green-200 text-green-800',
          ].join(' ')}
        >
          {t.type === 'error'
            ? <XCircle size={15} className="shrink-0 mt-0.5 text-red-500" />
            : <CheckCircle size={15} className="shrink-0 mt-0.5 text-pe-positive" />}
          <span className="font-sans text-[0.72rem] flex-1 leading-snug">{t.message}</span>
          <button
            type="button"
            onClick={() => dismiss(t.id)}
            className="shrink-0 text-current opacity-40 hover:opacity-70 transition-opacity mt-0.5"
          >
            <X size={12} />
          </button>
        </div>
      ))}
    </div>
  );
}
