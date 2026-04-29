import { useState, useRef, useCallback } from "react";
import { UserPlus } from "lucide-react";
import { RegisterPopoverPanel } from "./RegisterPopoverPanel";
import type { ReactNode } from "react";

interface Props {
  locale: "es" | "en";
  initialTab?: "register" | "login";
  variant?: "icon" | "text";
  label?: string;
  className?: string;
  icon?: ReactNode;
}

export function RegisterPopoverTrigger({
  locale,
  initialTab = "register",
  variant = "icon",
  label,
  className,
  icon,
}: Props) {
  const es = locale === "es";
  const [open, setOpen] = useState(false);
  const [anchor, setAnchor] = useState({ bottom: 0, right: 0 });
  const buttonRef = useRef<HTMLButtonElement>(null);

  const handleOpen = useCallback(() => {
    if (!buttonRef.current) return;
    const rect = buttonRef.current.getBoundingClientRect();
    setAnchor({ bottom: rect.bottom, right: rect.right });
    setOpen(true);
  }, []);

  return (
    <>
      {variant === "text" ? (
        <button
          ref={buttonRef}
          onClick={handleOpen}
          className={className}
          aria-label={label ?? (es ? "Iniciar sesion" : "Log in")}
          title={label ?? (es ? "Iniciar sesion" : "Log in")}
        >
          {icon ? <span className="inline-flex items-center">{icon}</span> : null}
          {label ?? (es ? "Iniciar sesion" : "Log in")}
        </button>
      ) : (
        <button
          ref={buttonRef}
          onClick={handleOpen}
          className="text-[var(--pe-nav-muted)] hover:text-[var(--pe-nav-hover)] transition-colors duration-200 hidden sm:flex items-center"
          aria-label={es ? "Registrarse" : "Register"}
          title={es ? "Registrarse" : "Register"}
        >
          <UserPlus size={16} />
        </button>
      )}

      {open && (
        <RegisterPopoverPanel
          anchor={anchor}
          initialTab={initialTab}
          locale={locale}
          onClose={() => setOpen(false)}
        />
      )}
    </>
  );
}
