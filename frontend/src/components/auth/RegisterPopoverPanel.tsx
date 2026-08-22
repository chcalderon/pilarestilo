import { useEffect, useRef } from "react";
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
  const panelRef = useRef<HTMLDivElement>(null);
  const isMobile = typeof window !== "undefined" && window.innerWidth < 640;

  // Close on outside mousedown
  useEffect(() => {
    function handleMouseDown(e: MouseEvent) {
      if (panelRef.current && !panelRef.current.contains(e.target as Node)) {
        onClose();
      }
    }
    document.addEventListener("mousedown", handleMouseDown);
    return () => document.removeEventListener("mousedown", handleMouseDown);
  }, [onClose]);

  // Close on Escape
  useEffect(() => {
    function handleKey(e: KeyboardEvent) {
      if (e.key === "Escape") onClose();
    }
    document.addEventListener("keydown", handleKey);
    return () => document.removeEventListener("keydown", handleKey);
  }, [onClose]);

  // Focus first input on mount
  useEffect(() => {
    const firstInput = panelRef.current?.querySelector<HTMLElement>("input, select, textarea");
    const firstFocusable = panelRef.current?.querySelector<HTMLElement>("button, [href], [tabindex]:not([tabindex=\"-1\"])");
    (firstInput ?? firstFocusable)?.focus();
  }, []);

  // Focus trap
  useEffect(() => {
    function trapFocus(e: KeyboardEvent) {
      if (e.key !== "Tab" || !panelRef.current) return;
      const focusable = Array.from(
        panelRef.current.querySelectorAll<HTMLElement>(
          'input, button, [href], select, textarea, [tabindex]:not([tabindex="-1"])'
        )
      ).filter(el => !el.hasAttribute("disabled"));
      if (focusable.length === 0) return;
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (e.shiftKey && document.activeElement === first) {
        e.preventDefault();
        last.focus();
      } else if (!e.shiftKey && document.activeElement === last) {
        e.preventDefault();
        first.focus();
      }
    }
    document.addEventListener("keydown", trapFocus);
    return () => document.removeEventListener("keydown", trapFocus);
  }, []);

  const desktopStyle: React.CSSProperties = {
    position: "fixed",
    top: anchor.bottom + 8,
    right: window.innerWidth - anchor.right,
    width: 320,
    zIndex: 9999,
  };

  const mobileStyle: React.CSSProperties = {
    position: "fixed",
    bottom: 0,
    left: 0,
    right: 0,
    zIndex: 9999,
  };

  const panel = (
    <div
      ref={panelRef}
      role="dialog"
      aria-modal="true"
      aria-label={locale === "es" ? "Crear cuenta o iniciar sesion" : "Create account or log in"}
      style={isMobile ? mobileStyle : desktopStyle}
      className={
        isMobile
          ? "relative bg-[var(--pe-surface)] border-t border-[var(--pe-border)] rounded-t-2xl p-6 pt-10 shadow-xl"
          : "relative bg-[var(--pe-surface)] border border-[var(--pe-border)] p-5 shadow-[0_8px_24px_rgba(0,0,0,0.12)]"
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
    </div>
  );

  return createPortal(panel, document.body);
}
