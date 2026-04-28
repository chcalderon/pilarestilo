import { useState } from "react";
import { registerUser, loginUser } from "@/lib/api";
import { useAuthStore } from "@/lib/authStore";

type Tab = "register" | "login";

interface Props {
  onSuccess: () => void;
}

export function RegisterPopoverForm({ onSuccess }: Props) {
  const [tab, setTab] = useState<Tab>("register");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const setAuth = useAuthStore(s => s.setAuth);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const data = tab === "register"
        ? await registerUser(email, password)
        : await loginUser(email, password);
      setAuth(data.accessToken, {
        id: data.userId,
        email: data.email,
        role: data.role,
        fullName: data.fullName,
        avatarUrl: data.avatarUrl,
        permissions: data.permissions ?? [],
        vigencyStart: data.vigencyStart,
        vigencyEnd: data.vigencyEnd,
      });
      onSuccess();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "Error al procesar";
      setError(msg);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex border-b border-[var(--pe-border)]">
        <button
          type="button"
          onClick={() => { setTab("register"); setError(null); }}
          className={`flex-1 py-2 text-xs tracking-widest uppercase transition-colors ${tab === "register" ? "border-b-2 border-[var(--pe-foreground)] text-[var(--pe-foreground)]" : "text-[var(--pe-muted)]"}`}
        >
          Registrarse
        </button>
        <button
          type="button"
          onClick={() => { setTab("login"); setError(null); }}
          className={`flex-1 py-2 text-xs tracking-widest uppercase transition-colors ${tab === "login" ? "border-b-2 border-[var(--pe-foreground)] text-[var(--pe-foreground)]" : "text-[var(--pe-muted)]"}`}
        >
          Iniciar sesión
        </button>
      </div>

      <form onSubmit={handleSubmit} className="flex flex-col gap-3" noValidate>
        <div className="flex flex-col gap-1">
          <label className="text-[10px] tracking-widest uppercase text-[var(--pe-muted)]">
            Email
          </label>
          <input
            type="email"
            required
            autoComplete="email"
            value={email}
            onChange={e => setEmail(e.target.value)}
            disabled={loading}
            className="border border-[var(--pe-border)] px-3 py-2 text-sm bg-[var(--pe-surface)] focus:outline-none focus:border-[var(--pe-foreground)] disabled:opacity-50"
          />
        </div>

        <div className="flex flex-col gap-1">
          <label className="text-[10px] tracking-widest uppercase text-[var(--pe-muted)]">
            Contraseña
          </label>
          <div className="relative">
            <input
              type={showPassword ? "text" : "password"}
              required
              minLength={8}
              autoComplete={tab === "register" ? "new-password" : "current-password"}
              value={password}
              onChange={e => setPassword(e.target.value)}
              disabled={loading}
              className="w-full border border-[var(--pe-border)] px-3 py-2 pr-9 text-sm bg-[var(--pe-surface)] focus:outline-none focus:border-[var(--pe-foreground)] disabled:opacity-50"
            />
            <button
              type="button"
              onClick={() => setShowPassword(v => !v)}
              className="absolute right-2 top-1/2 -translate-y-1/2 text-[var(--pe-muted)] hover:text-[var(--pe-foreground)]"
              aria-label={showPassword ? "Ocultar contraseña" : "Mostrar contraseña"}
            >
              {showPassword ? (
                <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94"/>
                  <path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19"/>
                  <line x1="1" y1="1" x2="23" y2="23"/>
                </svg>
              ) : (
                <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                  <circle cx="12" cy="12" r="3"/>
                </svg>
              )}
            </button>
          </div>
        </div>

        {error && (
          <p role="alert" aria-live="polite" className="text-xs text-red-500">
            {error}
          </p>
        )}

        <button
          type="submit"
          disabled={loading}
          className="bg-[#1A1A1A] text-[#F8F4EF] py-2 text-sm tracking-widest uppercase hover:bg-[#B76E79] disabled:opacity-50 transition-colors"
        >
          {loading ? (
            <span className="flex items-center justify-center gap-2">
              <svg className="animate-spin h-4 w-4" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"/>
                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8z"/>
              </svg>
              Procesando
            </span>
          ) : tab === "register" ? "Crear cuenta" : "Iniciar sesión"}
        </button>
      </form>
    </div>
  );
}
