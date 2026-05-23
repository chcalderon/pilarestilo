import { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { AnimatePresence, motion } from 'motion/react';

function useReducedMotion(): boolean {
  const [reduced, setReduced] = useState(false);
  useEffect(() => {
    const mq = window.matchMedia('(prefers-reduced-motion: reduce)');
    setReduced(mq.matches);
    const handler = (e: MediaQueryListEvent) => setReduced(e.matches);
    mq.addEventListener('change', handler);
    return () => mq.removeEventListener('change', handler);
  }, []);
  return reduced;
}

interface Props {
  open: boolean;
  productName: string;
  availableQty: number;
  requestedQty: number;
  onClose: () => void;
}

const EASING = [0.22, 0.61, 0.36, 1] as const;

export default function StockUnavailableModal({
  open,
  productName,
  availableQty,
  requestedQty,
  onClose,
}: Props) {
  const btnRef = useRef<HTMLButtonElement>(null);
  const [mounted, setMounted] = useState(false);
  const reducedMotion = useReducedMotion();
  const descId = 'stock-modal-desc';

  useEffect(() => { setMounted(true); }, []);

  useEffect(() => {
    if (open) {
      const frame = requestAnimationFrame(() => btnRef.current?.focus());
      return () => cancelAnimationFrame(frame);
    }
  }, [open]);

  useEffect(() => {
    if (!open) return;
    const handler = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    document.addEventListener('keydown', handler);
    return () => document.removeEventListener('keydown', handler);
  }, [open, onClose]);

  useEffect(() => {
    if (!open) return;
    document.body.style.overflow = 'hidden';
    return () => { document.body.style.overflow = ''; };
  }, [open]);

  if (!mounted) return null;

  return createPortal(
    <AnimatePresence>
      {open && (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.18 }}
          className="fixed inset-0 z-[9999] flex items-center justify-center p-4"
          onClick={onClose}
        >
          <div className="absolute inset-0 bg-pe-black/50 backdrop-blur-sm" aria-hidden="true" />

          <motion.div
            initial={reducedMotion ? { opacity: 0 } : { opacity: 0, scale: 0.96 }}
            animate={{ opacity: 1, scale: 1 }}
            exit={reducedMotion ? { opacity: 0 } : { opacity: 0, scale: 0.97 }}
            transition={{ duration: reducedMotion ? 0.12 : 0.24, ease: EASING }}
            role="alertdialog"
            aria-modal="true"
            aria-describedby={descId}
            aria-label="Sin stock suficiente"
            className="relative w-full max-w-md bg-pe-beige shadow-editorial px-6 py-8"
            onClick={(e) => e.stopPropagation()}
          >
            <h2 className="font-display text-pe-black text-2xl font-semibold tracking-wide mb-4">
              Sin stock suficiente
            </h2>

            <p id={descId} className="font-sans text-sm text-pe-charcoal/80 leading-relaxed mb-6">
              {availableQty === 0 ? (
                <>
                  <strong className="font-medium text-pe-black">{productName}</strong> no tiene stock disponible en este momento.
                </>
              ) : (
                <>
                  Quedan <strong className="font-medium text-pe-black">{availableQty} unidad{availableQty !== 1 ? 'es' : ''}</strong> de{' '}
                  <strong className="font-medium text-pe-black">{productName}</strong>. No es posible agregar {requestedQty} unidad{requestedQty !== 1 ? 'es' : ''}.
                </>
              )}
            </p>

            <button
              ref={btnRef}
              type="button"
              onClick={onClose}
              className="w-full font-sans text-xs tracking-[0.22em] uppercase px-4 py-3 bg-pe-rose text-pe-white hover:bg-pe-rose-deep transition-colors duration-200 focus:outline-none focus-visible:ring-2 focus-visible:ring-pe-gold focus-visible:ring-offset-2"
            >
              Entendido
            </button>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>,
    document.body
  );
}
