import { useState, useEffect, useRef } from 'react';
import { Eye, EyeOff, LogIn, Loader2, AlertCircle, CheckCircle2 } from 'lucide-react';
import { loginUser, googleLogin } from '../../lib/api';
import { useAuthStore } from '../../lib/authStore';
import { isAdminPanelRole } from '../../lib/roles';

interface Props {
  locale: 'es' | 'en';
  redirect?: string;
}

export default function LoginForm({ locale, redirect }: Props) {
  const [email, setEmail]           = useState('');
  const [password, setPassword]     = useState('');
  const [showPass, setShowPass]     = useState(false);
  const [error, setError]           = useState('');
  const [loading, setLoading]       = useState(false);
  const [merged, setMerged]         = useState(false);
  const { setAuth }                  = useAuthStore();
  const googleBtnRef                 = useRef<HTMLDivElement>(null);

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
            const dest = isAdminPanelRole(data.role) ? '/admin/dashboard' : (redirect ?? `/${locale}/`);
            if (data.accountMerged) {
              setMerged(true);
              setTimeout(() => { window.location.href = dest; }, 2500);
            } else {
              window.location.href = dest;
            }
          } catch {
            setError(es ? 'No se pudo iniciar sesión con Google.' : 'Could not sign in with Google.');
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
          text: 'continue_with',
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
    setLoading(true);
    setError('');
    try {
      const data = await loginUser(email, password);
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
      window.location.href = isAdminPanelRole(data.role) ? '/admin/dashboard' : (redirect ?? `/${locale}/`);
    } catch {
      setError(es ? 'Email o contraseña incorrectos.' : 'Invalid email or password.');
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
          <p className="font-sans text-[0.78rem] text-pe-charcoal/55 mt-1.5">
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
      {/* Email */}
      <div className="flex flex-col gap-1.5">
        <label className="font-sans text-[0.72rem] tracking-[0.18em] uppercase text-pe-charcoal/60">
          {es ? 'Correo electrónico' : 'Email'}
        </label>
        <input
          type="email"
          required
          autoComplete="email"
          value={email}
          onChange={e => setEmail(e.target.value)}
          className="bg-pe-white border border-pe-black/12 font-sans text-sm text-pe-charcoal px-3 py-2.5 focus:outline-hidden focus:border-pe-rose/60 transition-colors duration-200 placeholder:text-pe-charcoal/30"
          placeholder={es ? 'tu@email.com' : 'you@email.com'}
        />
      </div>

      {/* Password */}
      <div className="flex flex-col gap-1.5">
        <label className="font-sans text-[0.72rem] tracking-[0.18em] uppercase text-pe-charcoal/60">
          {es ? 'Contraseña' : 'Password'}
        </label>
        <div className="relative">
          <input
            type={showPass ? 'text' : 'password'}
            required
            autoComplete="current-password"
            value={password}
            onChange={e => setPassword(e.target.value)}
            className="w-full bg-pe-white border border-pe-black/12 font-sans text-sm text-pe-charcoal px-3 py-2.5 pr-10 focus:outline-hidden focus:border-pe-rose/60 transition-colors duration-200"
            placeholder={es ? 'Tu contraseña' : 'Your password'}
          />
          <button
            type="button"
            onClick={() => setShowPass(v => !v)}
            className="absolute right-3 top-1/2 -translate-y-1/2 text-pe-charcoal/40 hover:text-pe-rose transition-colors"
            aria-label={showPass ? (es ? 'Ocultar contraseña' : 'Hide password') : (es ? 'Mostrar contraseña' : 'Show password')}
          >
            {showPass ? <EyeOff size={15} /> : <Eye size={15} />}
          </button>
        </div>
      </div>

      {/* Error */}
      {error && (
        <div className="flex items-center gap-2 font-sans text-[0.78rem] text-red-300 bg-red-500/20 border border-red-500/50 px-3 py-2.5">
          <AlertCircle size={14} className="shrink-0 text-red-400" />
          {error}
        </div>
      )}

      {/* Submit */}
      <button
        type="submit"
        disabled={loading}
        className="flex items-center justify-center gap-2 bg-pe-rose text-pe-offwhite font-sans text-[0.78rem] tracking-[0.18em] uppercase px-6 py-3 hover:bg-pe-rose-deep transition-colors duration-200 disabled:opacity-60 disabled:cursor-not-allowed"
      >
        {loading ? <Loader2 size={15} className="animate-spin" /> : <LogIn size={15} />}
        {loading ? (es ? 'Ingresando…' : 'Signing in…') : (es ? 'Iniciar sesión' : 'Sign in')}
      </button>

      {/* Google Sign-In */}
      <div className="flex items-center gap-3 my-1">
        <div className="flex-1 h-px bg-pe-black/10"></div>
        <span className="font-sans text-[0.7rem] tracking-[0.12em] uppercase text-pe-charcoal/40">
          {es ? 'o continuar con' : 'or continue with'}
        </span>
        <div className="flex-1 h-px bg-pe-black/10"></div>
      </div>
      <div ref={googleBtnRef} className="flex justify-center"></div>

      {/* Register link */}
      <p className="font-sans text-[0.78rem] text-pe-charcoal/50 text-center">
        {es ? '¿No tienes cuenta?' : "Don't have an account?"}{' '}
        <a
          href={`/${locale}/auth/register`}
          className="text-pe-rose-deep hover:underline underline-offset-2"
        >
          {es ? 'Regístrate' : 'Register'}
        </a>
      </p>
    </form>
  );
}
