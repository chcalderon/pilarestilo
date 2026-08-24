import { useState, useRef } from 'react';
import { Eye, EyeOff, LogIn, Loader2, AlertCircle } from 'lucide-react';
import { loginUser, googleLogin } from '../../lib/api';
import { useGoogleSignIn } from '../../lib/useGoogleSignIn';
import { useAuthRedirect } from '../../lib/useAuthRedirect';
import { isAdminPanelRole } from '../../lib/roles';
import { AuthSuccessScreen } from '../../components/auth/AuthSuccessScreen';

interface Props {
  readonly locale: 'es' | 'en';
  readonly redirect?: string;
}

export default function LoginForm({ locale, redirect }: Props) {
  const [email, setEmail]       = useState('');
  const [password, setPassword] = useState('');
  const [showPass, setShowPass] = useState(false);
  const [error, setError]       = useState('');
  const [loading, setLoading]   = useState(false);
  const googleBtnRef            = useRef<HTMLDivElement>(null);

  const es = locale === 'es';

  const { success, finishAuth } = useAuthRedirect(
    (data) => (isAdminPanelRole(data.role) ? '/admin/dashboard' : (redirect ?? `/${locale}/`))
  );

  async function handleGoogleCredential(credential: string) {
    setLoading(true);
    setError('');
    try {
      const data = await googleLogin(credential);
      finishAuth(data);
    } catch {
      setError(es ? 'No se pudo iniciar sesión con Google.' : 'Could not sign in with Google.');
    } finally {
      setLoading(false);
    }
  }

  useGoogleSignIn({
    clientId: (import.meta as any).env?.PUBLIC_GOOGLE_CLIENT_ID as string | undefined,
    buttonRef: googleBtnRef,
    onCredential: handleGoogleCredential,
    buttonWidth: 320,
  });

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setLoading(true);
    setError('');
    try {
      const data = await loginUser(email, password);
      finishAuth(data);
    } catch {
      setError(es ? 'Email o contraseña incorrectos.' : 'Invalid email or password.');
    } finally {
      setLoading(false);
    }
  }

  if (success) {
    return <AuthSuccessScreen success={success} es={es} />;
  }

  let togglePasswordLabel: string;
  if (showPass) {
    togglePasswordLabel = es ? 'Ocultar contraseña' : 'Hide password';
  } else {
    togglePasswordLabel = es ? 'Mostrar contraseña' : 'Show password';
  }
  let submitLabel: string;
  if (loading) {
    submitLabel = es ? 'Ingresando…' : 'Signing in…';
  } else {
    submitLabel = es ? 'Iniciar sesión' : 'Sign in';
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-5" noValidate>
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
            className="absolute right-3 top-1/2 -translate-y-1/2 text-pe-muted hover:text-pe-rose-ink transition-colors"
            aria-label={togglePasswordLabel}
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
        className="flex items-center justify-center gap-2 bg-pe-rose-action text-pe-offwhite font-sans text-[0.78rem] tracking-[0.18em] uppercase px-6 py-3 hover:bg-pe-rose-action-action-deep transition-colors duration-200 disabled:opacity-60 disabled:cursor-not-allowed"
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
          className="text-pe-rose-ink hover:underline underline-offset-2"
        >
          {es ? 'Regístrate' : 'Register'}
        </a>
      </p>
    </form>
  );
}
