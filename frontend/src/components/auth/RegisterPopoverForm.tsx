import { useState, useEffect, useRef } from "react";
import { CheckCircle2 } from "lucide-react";
import { registerUser, loginUser, googleLogin, acceptMarketingConsent, type AuthTokenResponse } from "@/lib/api";
import { useAuthStore } from "@/lib/authStore";

type Tab = "register" | "login";

interface Props {
  initialTab?: Tab;
  locale: "es" | "en";
}

/** How long the welcome message stays up before the reload it promises. Long enough to read
 * a four-word line, short enough that waiting for it doesn't feel like the app is stuck. */
const WELCOME_DWELL_MS = 1600;

export function RegisterPopoverForm({ initialTab = "register", locale }: Props) {
  const es = locale === "es";
  const [tab, setTab] = useState<Tab>(initialTab);
  const [fullName, setFullName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [marketing, setMarketing] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [welcomeName, setWelcomeName] = useState<string | null>(null);
  const googleBtnRef = useRef<HTMLDivElement>(null);

  const setAuth = useAuthStore(s => s.setAuth);

  /**
   * Shared by the password form and the Google callback: both end the same way, and used to
   * diverge only in whether the visitor ever saw proof it worked. The header changing state is
   * not proof by itself — that is what sent the owner looking for this in the first place.
   *
   * <p>Writing to the auth store is deliberately deferred to the end of the dwell, not done up
   * front: AccountMenu reads the same store, and the instant `user` stops being null it stops
   * rendering the branch that holds this very popover — unmounting the welcome message before
   * anyone could read it. Waiting keeps the store, and AccountMenu's render, untouched for the
   * whole dwell.
   */
  function finishAuth(data: AuthTokenResponse) {
    setWelcomeName(data.fullName?.trim().split(" ")[0] || data.email.split("@")[0]);
    window.setTimeout(() => {
      setAuth(data.accessToken, {
        id: data.userId,
        email: data.email,
        role: data.role,
        fullName: data.fullName,
        avatarUrl: data.avatarUrl,
        permissions: data.permissions ?? [],
        permissionCodes: data.permissionCodes ?? [],
        vigencyStart: data.vigencyStart,
        vigencyEnd: data.vigencyEnd,
      });
      // A full reload, not just closing the popover: the page underneath was rendered
      // server-side as anonymous and stays that way until the next request. Reloading also
      // re-enters middleware.ts, which is what sends an already-authenticated visit to
      // /auth/login somewhere else now.
      window.location.reload();
    }, WELCOME_DWELL_MS);
  }

  useEffect(() => {
    setTab(initialTab);
    setError(null);
  }, [initialTab]);

  useEffect(() => {
    const clientId = (import.meta as any).env?.PUBLIC_GOOGLE_CLIENT_ID as string | undefined;
    if (!clientId) return;

    function initGoogle() {
      const g = (window as any).google;
      if (!g?.accounts?.id) return;
      g.accounts.id.initialize({
        client_id: clientId,
        callback: async (response: { credential: string }) => {
          setLoading(true);
          setError(null);
          try {
            const data = await googleLogin(response.credential);
            finishAuth(data);
          } catch {
            setError(es ? "No se pudo iniciar sesion con Google." : "Google sign-in failed.");
          } finally {
            setLoading(false);
          }
        },
      });
      if (googleBtnRef.current) {
        g.accounts.id.renderButton(googleBtnRef.current, {
          type: "standard",
          theme: "outline",
          size: "large",
          text: "continue_with",
          width: googleBtnRef.current.offsetWidth || 280,
          logo_alignment: "left",
        });
      }
    }

    if ((window as any).google?.accounts?.id) {
      initGoogle();
    } else {
      const script = document.querySelector('script[src*="accounts.google.com/gsi/client"]');
      script?.addEventListener("load", initGoogle);
      return () => script?.removeEventListener("load", initGoogle);
    }
  }, [es, setAuth]);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const data = tab === "register"
        ? await registerUser(email, password, fullName)
        : await loginUser(email, password);

      /*
       * Recorded after the account exists and only if she ticked it. A failure here must not fail
       * the registration: she has an account either way, and no marketing consent is the safe side.
       */
      if (tab === "register" && marketing) {
        try {
          await acceptMarketingConsent(data.accessToken);
        } catch {
          // Nothing to tell her: she can turn it on later from her account.
        }
      }
      finishAuth(data);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : (es ? "Error al procesar" : "Failed to process request");
      setError(msg);
    } finally {
      setLoading(false);
    }
  }

  if (welcomeName) {
    return (
      <div className="flex flex-col items-center gap-3 py-8 text-center">
        <CheckCircle2 size={38} className="text-[#B76E79]" strokeWidth={1.75} />
        <p className="font-display text-lg text-[var(--pe-foreground)]">
          {es ? `Bienvenido/a, ${welcomeName}` : `Welcome, ${welcomeName}`}
        </p>
        <p className="text-[0.72rem] text-[var(--pe-muted)]">
          {es ? 'Has ingresado correctamente.' : 'You are signed in.'}
        </p>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex border-b border-[var(--pe-border)]">
        <button
          type="button"
          onClick={() => { setTab("register"); setError(null); }}
          className={`flex-1 py-2 text-xs tracking-widest uppercase transition-colors ${tab === "register" ? "border-b-2 border-[var(--pe-foreground)] text-[var(--pe-foreground)]" : "text-[var(--pe-muted)]"}`}
        >
          {es ? "Registrarse" : "Register"}
        </button>
        <button
          type="button"
          onClick={() => { setTab("login"); setError(null); }}
          className={`flex-1 py-2 text-xs tracking-widest uppercase transition-colors ${tab === "login" ? "border-b-2 border-[var(--pe-foreground)] text-[var(--pe-foreground)]" : "text-[var(--pe-muted)]"}`}
        >
          {es ? "Iniciar sesion" : "Log in"}
        </button>
      </div>

      <form onSubmit={handleSubmit} className="flex flex-col gap-3" noValidate>
        {tab === "register" && (
          <div className="flex flex-col gap-1">
            <label className="text-[10px] tracking-widest uppercase text-[var(--pe-muted)]">
              {es ? "Nombre" : "Name"}
            </label>
            <input
              type="text"
              required
              autoComplete="name"
              value={fullName}
              onChange={e => setFullName(e.target.value)}
              disabled={loading}
              className="border border-[var(--pe-border)] px-3 py-2 text-sm bg-[var(--pe-surface)] focus:outline-hidden focus:border-[var(--pe-foreground)] disabled:opacity-50"
            />
          </div>
        )}

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
            className="border border-[var(--pe-border)] px-3 py-2 text-sm bg-[var(--pe-surface)] focus:outline-hidden focus:border-[var(--pe-foreground)] disabled:opacity-50"
          />
        </div>

        <div className="flex flex-col gap-1">
          <label className="text-[10px] tracking-widest uppercase text-[var(--pe-muted)]">
            {es ? "Contrasena" : "Password"}
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
              className="w-full border border-[var(--pe-border)] px-3 py-2 pr-9 text-sm bg-[var(--pe-surface)] focus:outline-hidden focus:border-[var(--pe-foreground)] disabled:opacity-50"
            />
            <button
              type="button"
              onClick={() => setShowPassword(v => !v)}
              className="absolute right-2 top-1/2 -translate-y-1/2 text-[var(--pe-muted)] hover:text-[var(--pe-foreground)]"
              aria-label={showPassword ? (es ? "Ocultar contrasena" : "Hide password") : (es ? "Mostrar contrasena" : "Show password")}
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

        {tab === "register" && (
          <label className="flex items-start gap-2 cursor-pointer">
            <input
              type="checkbox"
              checked={marketing}
              onChange={e => setMarketing(e.target.checked)}
              disabled={loading}
              className="mt-0.5 w-3.5 h-3.5 shrink-0 accent-[#B76E79]"
            />
            <span className="text-[0.68rem] leading-relaxed text-[var(--pe-muted)]">
              {es
                ? "Quiero recibir novedades por correo. Puedes desactivarlo cuando quieras."
                : "I want news by email. You can turn this off whenever you like."}
            </span>
          </label>
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
              {es ? "Procesando" : "Processing"}
            </span>
          ) : tab === "register" ? (es ? "Crear cuenta" : "Create account") : (es ? "Iniciar sesion" : "Log in")}
        </button>
      </form>

      <div className="flex items-center gap-2 text-[var(--pe-muted)]">
        <div className="flex-1 h-px bg-[var(--pe-border)]" />
        <span className="text-[10px] tracking-widest uppercase">o</span>
        <div className="flex-1 h-px bg-[var(--pe-border)]" />
      </div>

      <div ref={googleBtnRef} className="flex justify-center" />

      <p className="text-[0.66rem] leading-relaxed text-[var(--pe-muted)]">
        {es ? "Al crear la cuenta aceptas los " : "Creating an account means accepting our "}
        <a href={`/${locale}/shipping-returns`} className="underline hover:text-[var(--pe-foreground)]">
          {es ? "términos de compra" : "terms of sale"}
        </a>
        {es ? " y la " : " and our "}
        <a href={`/${locale}/privacy`} className="underline hover:text-[var(--pe-foreground)]">
          {es ? "política de privacidad" : "privacy policy"}
        </a>.
      </p>

      <div className="pt-1 text-center text-[0.68rem] text-[var(--pe-muted)]">
        {tab === "login" ? (
          <>
            <span>{es ? "¿No tienes cuenta?" : "Don't have an account?"}</span>{" "}
            <a href={`/${locale}/auth/register`} className="underline hover:text-[var(--pe-foreground)]">
              {es ? "Registrarse" : "Register"}
            </a>
          </>
        ) : (
          <>
            <span>{es ? "¿Ya tienes cuenta?" : "Already have an account?"}</span>{" "}
            <a href={`/${locale}/auth/login`} className="underline hover:text-[var(--pe-foreground)]">
              {es ? "Iniciar sesion" : "Log in"}
            </a>
          </>
        )}
      </div>
    </div>
  );
}
