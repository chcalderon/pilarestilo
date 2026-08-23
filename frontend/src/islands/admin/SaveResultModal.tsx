import { useEffect } from 'react';
import { X } from 'lucide-react';
import Overlay from './Overlay';

interface Props {
  readonly ok: boolean;
  readonly title: string;
  readonly detail?: string | null;
  readonly onClose: () => void;
}

/**
 * Says what happened to the thing that was just saved.
 *
 * The product form closed on success and said nothing, so the only way to know whether the work
 * had landed was to find the row again and read it. Saving is the moment the operator is least
 * able to check: the form they were looking at is gone.
 *
 * A success dismisses itself after a few seconds, because the answer is one word and holding the
 * screen for it is a toll. A failure stays until it is closed, and leaves the form open behind it,
 * because nobody can act on a message that disappeared before they finished reading it.
 */
export default function SaveResultModal({ ok, title, detail, onClose }: Props) {
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', onKey);
    if (!ok) return () => document.removeEventListener('keydown', onKey);

    const timer = window.setTimeout(onClose, 2600);
    return () => {
      window.clearTimeout(timer);
      document.removeEventListener('keydown', onKey);
    };
  }, [ok, onClose]);

  return (
    <Overlay>
      <div className="fixed inset-0 z-[70] flex items-center justify-center p-4">
        <button
          type="button"
          aria-label="Cerrar"
          onClick={onClose}
          className="absolute inset-0 bg-black/50 backdrop-blur-[2px]"
        />
        <div
          role="alertdialog"
          aria-modal="true"
          aria-label={title}
          className="relative w-full max-w-sm bg-[var(--pe-surface-card)] border border-[var(--pe-border)] px-6 py-7 text-center shadow-2xl"
        >
          <button
            type="button"
            onClick={onClose}
            aria-label="Cerrar"
            className="absolute right-3 top-3 p-1 opacity-50 hover:opacity-100"
          >
            <X size={16} />
          </button>

          <img src="/logo-pe.svg" alt="" width="72" height="64" className="mx-auto mb-4 opacity-90" />

          <p
            className={`font-display text-xl font-light ${ok ? '' : 'text-red-500'}`}
          >
            {title}
          </p>
          {detail && <p className="mt-2 text-[0.8rem] leading-relaxed opacity-70">{detail}</p>}

          {!ok && (
            <button
              type="button"
              onClick={onClose}
              className="mt-5 inline-flex items-center gap-1.5 px-4 py-2 text-[0.7rem] font-sans tracking-widest uppercase rounded-xs bg-[var(--pe-ink)] text-[var(--pe-surface)] hover:opacity-80 transition-opacity"
            >
              Volver al formulario
            </button>
          )}
        </div>
      </div>
    </Overlay>
  );
}
