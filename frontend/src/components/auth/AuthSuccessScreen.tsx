import { CheckCircle2 } from "lucide-react";
import type { AuthSuccessState } from "@/lib/useAuthRedirect";

interface Props {
  readonly success: AuthSuccessState;
  readonly es: boolean;
}

/** Shown by both LoginForm and RegisterForm for the same reason: proof the sign-in worked,
 * before the redirect it promises. Merged accounts get their own headline. */
export function AuthSuccessScreen({ success, es }: Props) {
  let title: string;
  let subtitle: string;
  if (success.merged) {
    title = es ? "¡Cuentas unificadas!" : "Accounts linked!";
    subtitle = es
      ? "Tu cuenta existente ha sido vinculada con Google. Redirigiendo…"
      : "Your existing account has been linked with Google. Redirecting…";
  } else {
    title = es ? `Bienvenido/a, ${success.name}` : `Welcome, ${success.name}`;
    subtitle = es ? "Has ingresado correctamente." : "You are signed in.";
  }

  return (
    <div className="flex flex-col items-center gap-5 py-10 text-center">
      <CheckCircle2 size={44} className="text-pe-rose-ink" />
      <div>
        <p className="font-sans text-[0.95rem] text-pe-charcoal font-medium">{title}</p>
        <p className="font-sans text-[0.78rem] text-pe-muted mt-1.5">{subtitle}</p>
      </div>
    </div>
  );
}
