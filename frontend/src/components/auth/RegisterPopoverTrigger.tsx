import { useState, useRef, useCallback } from "react";
import { UserPlus } from "lucide-react";
import { RegisterPopoverPanel } from "./RegisterPopoverPanel";

interface Props {
  es?: boolean;
}

export function RegisterPopoverTrigger({ es = true }: Props) {
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
      <button
        ref={buttonRef}
        onClick={handleOpen}
        className="font-sans text-[0.68rem] uppercase tracking-[0.22em] text-[var(--pe-nav-muted)] hover:text-[var(--pe-nav-hover)] transition-colors duration-200 hidden sm:flex items-center gap-1.5"
      >
        <UserPlus size={13} />
        {es ? 'Registrarse' : 'Register'}
      </button>

      {open && (
        <RegisterPopoverPanel
          anchor={anchor}
          onClose={() => setOpen(false)}
        />
      )}
    </>
  );
}
