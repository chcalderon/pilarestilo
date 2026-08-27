import { useCallback, useRef } from "react";
import { createPortal } from "react-dom";
import { RegisterPopoverForm } from "./RegisterPopoverForm";
import { X } from "lucide-react";

interface AnchorRect {
  bottom: number;
  right: number;
}

interface Props {
  anchor: AnchorRect;
  initialTab: "register" | "login";
  locale: "es" | "en";
  onClose: () => void;
}

export function RegisterPopoverPanel({ anchor, initialTab, locale, onClose }: Props) {
  const dialogRef = useRef<HTMLDialogElement | null>(null);
  const isMobile = typeof window !== "undefined" && window.innerWidth < 640;

  /*
   * showModal() replaces four hand-rolled effects at once: outside-mousedown-to-close becomes the
   * standard "click landed on the dialog itself, not a descendant" backdrop check, Escape-to-close
   * is the native cancel event, and the initial-focus + Tab focus trap are both free -- everything
   * outside a modally-shown dialog goes inert, so Tab can't leave it and the browser focuses the
   * first focusable element on its own. A ref callback rather than a mount-effect, so it still
   * fires correctly if this is ever rendered somewhere the portal target isn't ready synchronously.
   */
  const setDialogRef = useCallback((node: HTMLDialogElement | null) => {
    dialogRef.current = node;
    if (node && !node.open) node.showModal();
  }, []);

  const desktopStyle: React.CSSProperties = {
    position: "fixed",
    top: anchor.bottom + 8,
    right: window.innerWidth - anchor.right,
    width: 320,
    margin: 0,
  };

  const mobileStyle: React.CSSProperties = {
    position: "fixed",
    bottom: 0,
    left: 0,
    right: 0,
    margin: 0,
  };

  const panel = (
    <dialog
      ref={setDialogRef}
      aria-label={locale === "es" ? "Crear cuenta o iniciar sesion" : "Create account or log in"}
      onCancel={(e) => {
        e.preventDefault();
        onClose();
      }}
      onClick={(e) => {
        if (e.target === dialogRef.current) onClose();
      }}
      style={isMobile ? mobileStyle : desktopStyle}
      className={
        (isMobile
          ? "bg-[var(--pe-surface)] border-t border-[var(--pe-border)] rounded-t-2xl p-6 pt-10 shadow-xl"
          : "bg-[var(--pe-surface)] border border-[var(--pe-border)] p-5 shadow-[0_8px_24px_rgba(0,0,0,0.12)]"
        ) + " max-w-none max-h-none backdrop:bg-transparent"
      }
    >
      {isMobile && (
        <button
          type="button"
          onClick={onClose}
          className="absolute top-3 right-3 text-[var(--pe-muted)] hover:text-[var(--pe-foreground)] transition-colors"
          aria-label={locale === "es" ? "Cerrar" : "Close"}
        >
          <X size={16} />
        </button>
      )}
      <RegisterPopoverForm initialTab={initialTab} locale={locale} />
    </dialog>
  );

  return createPortal(panel, document.body);
}
