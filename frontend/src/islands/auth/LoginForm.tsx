import { useRef, useState } from 'react';
import { Eye, EyeOff, LogIn, Loader2, AlertCircle } from 'lucide-react';
import { loginUser, googleLogin } from '../../lib/api';
import { useGoogleSignIn } from '../../lib/useGoogleSignIn';
import { useAuthRedirect } from '../../lib/useAuthRedirect';
import { useAuthAction } from '../../lib/useAuthAction';
import { isAdminPanelRole } from '../../lib/roles';
import { AuthSuccessScreen } from '../../components/auth/AuthSuccessScreen';

interface Props {
  readonly locale: 'es' | 'en';
  readonly redirect?: string;
}

function togglePasswordLabelFor(showPass: boolean, es: boolean): string {
  if (showPass) return es ? 'Ocultar contraseña' : 'Hide password';
  return es ? 'Mostrar contraseña' : 'Show password';
}

function submitLabelFor(loading: boolean, es: boolean): string {
  if (loading) return es ? 'Ingresando…' : 'Signing in…';
  return es ? 'Iniciar sesión' : 'Sign in';
}

export default function LoginForm({ locale, redirect }: Props) {
  const [email, setEmail]       = useState('');
  const [password, setPassword] = useState('');
  const [showPass, setShowPass] = useState(false);
  const googleBtnRef            = useRef<HTMLDivElement>(null);

  const es = locale === 'es';

  const { success, finishAuth } = useAuthRedirect(
    (data) => (isAdminPanelRole(data.role) ? '/admin/dashboard' : (redirect ?? `/${locale}/`))
  );
  const { loading, error, run } = useAuthAction(finishAuth);

  useGoogleSignIn({
    clientId: (import.meta as any).env?.PUBLIC_GOOGLE_CLIENT_ID as string | undefined,
    buttonRef: googleBtnRef,
    onCredential: (credential) => run(
      () => googleLogin(credential),
      es ? 'No se pudo iniciar sesión con Google.' : 'Could not sign in with Google.'
    ),
    buttonWidth: 320,
  });

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    void run(() => loginUser(email, password), es ? 'Email o contraseña incorrectos.' : 'Invalid email or password.');
  }

  if (success) {
    return <AuthSuccessScreen success={success} es={es} />;
  }

  const togglePasswordLabel = togglePasswordLabelFor(showPass, es);
  const submitLabel = submitLabelFor(loading, es);

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-5" noValidate>
      {/* Email */}
      <div className="flex flex-col gap-1.5">
        <label htmlFor="login-email" className="font-sans text-[0.72rem] tracking-[0.18em] uppercase text-pe-muted">
          {es ? 'Correo electrónico' : 'Email'}
        </label>
        <input
          id="login-email"
          type="email"
          required
          autoComplete="email"
          value={email}
          onChange={e => setEmail(e.target.value)}
          className="bg-pe-white border border-pe-black/12 font-sans text-sm text-pe-charcoal px-3 py-2.5 focus:outline-hidden focus:border-pe-rose/60 focus-visible:ring-1 focus-visible:ring-pe-rose/40 transition-colors duration-200 placeholder:text-pe-muted"
          placeholder={es ? 'tu@email.com' : 'you@email.com'}
        />
      </div>

      {/* Password */}
      <div className="flex flex-col gap-1.5">
        <label htmlFor="login-password" className="font-sans text-[0.72rem] tracking-[0.18em] uppercase text-pe-muted">
          {es ? 'Contraseña' : 'Password'}
        </label>
        <div className="relative">
          <input
            id="login-password"
            type={showPass ? 'text' : 'password'}
            required
            autoComplete="current-password"
            value={password}
            onChange={e => setPassword(e.target.value)}
            className="w-full bg-pe-white border border-pe-black/12 font-sans text-sm text-pe-charcoal px-3 py-2.5 pr-10 focus:outline-hidden focus:border-pe-rose/60 focus-visible:ring-1 focus-visible:ring-pe-rose/40 transition-colors duration-200"
            placeholder={es ? 'Tu contraseña' : 'Your password'}
          />
          <button
            type="button"
            onClick={() => setShowPass(v => !v)}
            className="absolute right-3 top-1/2 -translate-y-1/2 text-pe-muted hover:text-pe-rose-ink transition-colors"
            aria-label={togglePasswordLabel}
          >
            {showPass ? <EyeOff size={15} /> : <Eye size={15} />}
          </button>
        </div>
        <a
          href={`/${locale}/auth/forgot-password`}
          className="self-end font-sans text-[0.74rem] text-pe-rose-ink hover:underline underline-offset-2"
        >
          {es ? '¿Olvidaste tu contraseña?' : 'Forgot your password?'}
        </a>
      </div>

      {/* Error */}
      {error && (
        <div className="flex items-center gap-2 font-sans text-[0.78rem] text-pe-danger-ink bg-pe-danger-surface border border-pe-danger/50 px-3 py-2.5">
          <AlertCircle size={14} className="shrink-0 text-pe-danger-ink" />
          {error}
        </div>
      )}

      {/* Submit */}
      <button
        type="submit"
        disabled={loading}
        className="flex items-center justify-center gap-2 bg-pe-rose-action text-pe-offwhite font-sans text-[0.78rem] tracking-[0.18em] uppercase px-6 py-3 hover:bg-pe-rose-deep transition-colors duration-200 disabled:opacity-60 disabled:cursor-not-allowed"
      >
        {loading ? <Loader2 size={15} className="animate-spin" /> : <LogIn size={15} />}
        {submitLabel}
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

      {/* Register link */}
      <p className="font-sans text-[0.78rem] text-pe-muted text-center">
        {es ? '¿No tienes cuenta?' : "Don't have an account?"}{' '}
        <a
          href={`/${locale}/auth/register`}
          className="text-pe-rose-ink underline underline-offset-2 decoration-pe-rose-ink/40 hover:decoration-pe-rose-ink"
        >
          {es ? 'Regístrate' : 'Register'}
        </a>
      </p>
    </form>
  );
}
