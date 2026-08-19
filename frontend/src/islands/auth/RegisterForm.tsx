import { useState, useEffect, useRef } from 'react';
import { Eye, EyeOff, UserPlus, Loader2, CheckCircle2 } from 'lucide-react';
import { registerUser, googleLogin, acceptMarketingConsent } from '../../lib/api';
import { useAuthStore } from '../../lib/authStore';

interface Props {
  locale: 'es' | 'en';
  redirect?: string;
}

export default function RegisterForm({ locale, redirect }: Props) {
  const [email, setEmail]       = useState('');
  const [fullName, setFullName] = useState('');
  const [password, setPassword] = useState('');
  const [showPass, setShowPass] = useState(false);
  const [error, setError]       = useState('');
  const [loading, setLoading]   = useState(false);
  const [marketing, setMarketing] = useState(false);
  const [merged, setMerged]     = useState(false);
  const { setAuth }              = useAuthStore();
  const googleBtnRef             = useRef<HTMLDivElement>(null);

  const es = locale === 'es';

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
          setError('');
          try {
            const data = await googleLogin(response.credential);
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
            const dest = redirect ?? `/${locale}/account`;
            if (data.accountMerged) {
              setMerged(true);
              setTimeout(() => { window.location.href = dest; }, 2500);
            } else {
              window.location.href = dest;
            }
          } catch {
            setError(es ? 'No se pudo registrar con Google.' : 'Could not register with Google.');
          } finally {
            setLoading(false);
          }
        },
      });
      if (googleBtnRef.current) {
        g.accounts.id.renderButton(googleBtnRef.current, {
          type: 'standard',
          theme: 'outline',
          size: 'large',
          text: 'signup_with',
          width: googleBtnRef.current.offsetWidth || 320,
          logo_alignment: 'left',
        });
      }
    }

    if ((window as any).google?.accounts?.id) {
      initGoogle();
    } else {
      const script = document.querySelector('script[src*="accounts.google.com/gsi/client"]');
      script?.addEventListener('load', initGoogle);
      return () => script?.removeEventListener('load', initGoogle);
    }
  }, []);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (password.length < 8) {
      setError(es ? 'La contraseña debe tener al menos 8 caracteres.' : 'Password must be at least 8 characters.');
      return;
    }
    setLoading(true);
    setError('');
    try {
      const data = await registerUser(email, password, fullName);

      /*
       * Recorded after the account exists, and only if she ticked it. Failing here must not fail
       * the registration: she has an account either way, and a missing marketing consent is the
       * safe side of the two.
       */
      if (marketing) {
        try {
          await acceptMarketingConsent(data.accessToken);
        } catch {
          // Nothing to tell her: she is registered, and she can turn it on later from her account.
        }
      }
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
      window.location.href = redirect ?? `/${locale}/account`;
    } catch {
      setError(
        es
          ? 'No pudimos crear la cuenta. Intenta con otro email.'
          : 'Could not create account. Try a different email.'
      );
    } finally {
      setLoading(false);
    }
  }

  if (merged) {
    return (
      <div className="flex flex-col items-center gap-5 py-10 text-center">
        <CheckCircle2 size={44} className="text-pe-rose" />
        <div>
          <p className="font-sans text-[0.95rem] text-pe-charcoal font-medium">
            {es ? '¡Cuentas unificadas!' : 'Accounts linked!'}
          </p>
          <p className="font-sans text-[0.78rem] text-pe-muted mt-1.5">
            {es
              ? 'Tu cuenta existente ha sido vinculada con Google. Redirigiendo…'
              : 'Your existing account has been linked with Google. Redirecting…'}
          </p>
        </div>
      </div>
    );
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-5" noValidate>
      {/* Full name */}
      <div className="flex flex-col gap-1.5">
        <label className="font-sans text-[0.72rem] tracking-[0.18em] uppercase text-pe-muted">
          {es ? 'Nombre completo' : 'Full name'}
        </label>
        <input
          type="text"
          required
          autoComplete="name"
          value={fullName}
          onChange={e => setFullName(e.target.value)}
          className="bg-pe-white border border-pe-black/12 font-sans text-sm text-pe-charcoal px-3 py-2.5 focus:outline-hidden focus:border-pe-rose/60 transition-colors duration-200 placeholder:text-pe-muted"
          placeholder={es ? 'María García' : 'Jane Smith'}
        />
      </div>

      {/* Email */}
      <div className="flex flex-col gap-1.5">
        <label className="font-sans text-[0.72rem] tracking-[0.18em] uppercase text-pe-muted">
          {es ? 'Correo electrónico' : 'Email'}
        </label>
        <input
          type="email"
          required
          autoComplete="email"
          value={email}
          onChange={e => setEmail(e.target.value)}
          className="bg-pe-white border border-pe-black/12 font-sans text-sm text-pe-charcoal px-3 py-2.5 focus:outline-hidden focus:border-pe-rose/60 transition-colors duration-200 placeholder:text-pe-muted"
          placeholder={es ? 'tu@email.com' : 'you@email.com'}
        />
      </div>

      {/* Password */}
      <div className="flex flex-col gap-1.5">
        <label className="font-sans text-[0.72rem] tracking-[0.18em] uppercase text-pe-muted">
          {es ? 'Contraseña' : 'Password'}
          <span className="ml-1.5 text-pe-muted normal-case tracking-normal">
            ({es ? 'mín. 8 caracteres' : 'min. 8 characters'})
          </span>
        </label>
        <div className="relative">
          <input
            type={showPass ? 'text' : 'password'}
            required
            autoComplete="new-password"
            value={password}
            onChange={e => setPassword(e.target.value)}
            className="w-full bg-pe-white border border-pe-black/12 font-sans text-sm text-pe-charcoal px-3 py-2.5 pr-10 focus:outline-hidden focus:border-pe-rose/60 transition-colors duration-200"
            placeholder="••••••••"
          />
          <button
            type="button"
            onClick={() => setShowPass(v => !v)}
            className="absolute right-3 top-1/2 -translate-y-1/2 text-pe-muted hover:text-pe-rose transition-colors"
            aria-label={showPass ? (es ? 'Ocultar' : 'Hide') : (es ? 'Mostrar' : 'Show')}
          >
            {showPass ? <EyeOff size={15} /> : <Eye size={15} />}
          </button>
        </div>
      </div>

      {/* Error */}
      {error && (
        <p className="font-sans text-[0.78rem] text-pe-rose-ink bg-pe-rose-soft/40 px-3 py-2 border-l-2 border-pe-rose">
          {error}
        </p>
      )}

      {/* Consent: the terms come with the account, marketing does not */}
      <div className="flex flex-col gap-3">
        <label className="flex items-start gap-2.5 cursor-pointer">
          <input
            type="checkbox"
            checked={marketing}
            onChange={(e) => setMarketing(e.target.checked)}
            className="mt-0.5 w-4 h-4 shrink-0 accent-pe-rose"
          />
          <span className="font-sans text-[0.76rem] leading-relaxed text-pe-muted">
            {es
              ? 'Quiero recibir novedades y prendas nuevas por correo. Puedes desactivarlo cuando quieras.'
              : 'I want news and new arrivals by email. You can turn this off whenever you like.'}
          </span>
        </label>
        <p className="font-sans text-[0.72rem] leading-relaxed text-pe-muted">
          {es ? 'Al crear la cuenta aceptas los ' : 'Creating an account means accepting our '}
          <a
            href={`/${locale}/shipping-returns`}
            className="text-pe-rose-ink hover:underline underline-offset-2"
          >
            {es ? 'términos de compra' : 'terms of sale'}
          </a>
          {es ? ' y la ' : ' and our '}
          <a
            href={`/${locale}/privacy`}
            className="text-pe-rose-ink hover:underline underline-offset-2"
          >
            {es ? 'política de privacidad' : 'privacy policy'}
          </a>
          {es
            ? '. Los correos sobre tus pedidos llegan siempre: son parte del servicio.'
            : '. Emails about your orders always arrive: they are part of the service.'}
        </p>
      </div>

      {/* Submit */}
      <button
        type="submit"
        disabled={loading}
        className="flex items-center justify-center gap-2 bg-pe-rose-action text-pe-offwhite font-sans text-[0.78rem] tracking-[0.18em] uppercase px-6 py-3 hover:bg-pe-rose-action-action-deep transition-colors duration-200 disabled:opacity-60 disabled:cursor-not-allowed"
      >
        {loading ? <Loader2 size={15} className="animate-spin" /> : <UserPlus size={15} />}
        {loading ? (es ? 'Creando cuenta…' : 'Creating account…') : (es ? 'Crear cuenta' : 'Create account')}
      </button>

      {/* Google Sign-In */}
      <div className="flex items-center gap-3 my-1">
        <div className="flex-1 h-px bg-pe-black/10"></div>
        <span className="font-sans text-[0.7rem] tracking-[0.12em] uppercase text-pe-muted">
          {es ? 'o continuar con' : 'or continue with'}
        </span>
        <div className="flex-1 h-px bg-pe-black/10"></div>
      </div>
      <div ref={googleBtnRef} className="flex justify-center"></div>

      {/* Login link */}
      <p className="font-sans text-[0.78rem] text-pe-muted text-center">
        {es ? '¿Ya tienes cuenta?' : 'Already have an account?'}{' '}
        <a
          href={`/${locale}/auth/login`}
          className="text-pe-rose-ink hover:underline underline-offset-2"
        >
          {es ? 'Inicia sesión' : 'Sign in'}
        </a>
      </p>
    </form>
  );
}
