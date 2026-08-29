import { useEffect, useState } from 'react';
import { Eye, EyeOff, Loader2, KeyRound, CheckCircle2, AlertCircle } from 'lucide-react';
import { resetPassword, ApiError } from '../../lib/api';

interface Props {
  readonly locale: 'es' | 'en';
}

const MIN_LENGTH = 8;
const GENERIC_LINK_ERROR_ES = 'El enlace no es válido o ya expiró';
const GENERIC_LINK_ERROR_EN = 'The link is invalid or has expired';

function togglePasswordLabelFor(showPass: boolean, es: boolean): string {
  if (showPass) return es ? 'Ocultar contraseña' : 'Hide password';
  return es ? 'Mostrar contraseña' : 'Show password';
}

function submitLabelFor(loading: boolean, es: boolean): string {
  if (loading) return es ? 'Guardando…' : 'Saving…';
  return es ? 'Guardar contraseña' : 'Save password';
}

export default function ResetPasswordForm({ locale }: Props) {
  const es = locale === 'es';
  const [token, setToken] = useState<string | null>(null);
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [showPass, setShowPass] = useState(false);
  const [loading, setLoading] = useState(false);
  const [done, setDone] = useState(false);
  const [error, setError] = useState('');
  const [linkDead, setLinkDead] = useState(false);

  useEffect(() => {
    const fromUrl = new URLSearchParams(window.location.search).get('token');
    if (fromUrl && fromUrl.trim().length > 0) {
      setToken(fromUrl);
    } else {
      setLinkDead(true);
    }
  }, []);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError('');
    if (password.length < MIN_LENGTH) {
      setError(es ? `La contraseña debe tener al menos ${MIN_LENGTH} caracteres.`
                  : `The password must be at least ${MIN_LENGTH} characters.`);
      return;
    }
    if (password !== confirm) {
      setError(es ? 'Las contraseñas no coinciden.' : 'The passwords do not match.');
      return;
    }
    if (!token) {
      setLinkDead(true);
      return;
    }
    setLoading(true);
    try {
      await resetPassword(token, password);
      setDone(true);
    } catch (err) {
      if (err instanceof ApiError && err.status === 400) {
        setLinkDead(true);
      } else if (err instanceof ApiError && err.status === 429) {
        setError(es ? 'Demasiados intentos. Espera un momento e inténtalo otra vez.'
                    : 'Too many attempts. Wait a moment and try again.');
      } else {
        setError(es ? 'No pudimos actualizar la contraseña. Inténtalo de nuevo.'
                    : 'We could not update the password. Please try again.');
      }
    } finally {
      setLoading(false);
    }
  }

  if (done) {
    return (
      <div className="flex flex-col items-center gap-5 py-8 text-center">
        <CheckCircle2 size={44} className="text-pe-rose-ink" />
        <div>
          <p className="font-sans text-[0.95rem] text-pe-charcoal font-medium">
            {es ? 'Contraseña actualizada' : 'Password updated'}
          </p>
          <p className="font-sans text-[0.78rem] text-pe-muted mt-1.5 leading-relaxed">
            {es
              ? 'Se cerraron todas las sesiones anteriores. Inicia sesión con tu nueva contraseña.'
              : 'Every earlier session was signed out. Sign in with your new password.'}
          </p>
        </div>
        <a
          href={`/${locale}/auth/login`}
          className="flex items-center justify-center gap-2 bg-pe-rose-action text-pe-offwhite font-sans text-[0.78rem] tracking-[0.18em] uppercase px-6 py-3 hover:bg-pe-rose-deep transition-colors duration-200"
        >
          {es ? 'Iniciar sesión' : 'Sign in'}
        </a>
      </div>
    );
  }

  if (linkDead) {
    return (
      <div className="flex flex-col items-center gap-5 py-8 text-center">
        <AlertCircle size={44} className="text-pe-rose-ink" />
        <div>
          <p className="font-sans text-[0.95rem] text-pe-charcoal font-medium">
            {es ? GENERIC_LINK_ERROR_ES : GENERIC_LINK_ERROR_EN}
          </p>
          <p className="font-sans text-[0.78rem] text-pe-muted mt-1.5 leading-relaxed">
            {es
              ? 'Solicita un enlace nuevo para restablecer tu contraseña.'
              : 'Request a fresh link to reset your password.'}
          </p>
        </div>
        <a
          href={`/${locale}/auth/forgot-password`}
          className="font-sans text-[0.78rem] text-pe-rose-ink hover:underline underline-offset-2"
        >
          {es ? 'Solicitar un enlace nuevo' : 'Request a new link'}
        </a>
      </div>
    );
  }

  const togglePasswordLabel = togglePasswordLabelFor(showPass, es);

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-5" noValidate>
      <div className="flex flex-col gap-1.5">
        <label htmlFor="reset-password" className="font-sans text-[0.72rem] tracking-[0.18em] uppercase text-pe-muted">
          {es ? 'Nueva contraseña' : 'New password'}
        </label>
        <div className="relative">
          <input
            id="reset-password"
            type={showPass ? 'text' : 'password'}
            required
            autoComplete="new-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className="w-full bg-pe-white border border-pe-black/12 font-sans text-sm text-pe-charcoal px-3 py-2.5 pr-10 focus:outline-hidden focus:border-pe-rose/60 transition-colors duration-200"
            placeholder={es ? 'Al menos 8 caracteres' : 'At least 8 characters'}
          />
          <button
            type="button"
            onClick={() => setShowPass((v) => !v)}
            className="absolute right-3 top-1/2 -translate-y-1/2 text-pe-muted hover:text-pe-rose-ink transition-colors"
            aria-label={togglePasswordLabel}
          >
            {showPass ? <EyeOff size={15} /> : <Eye size={15} />}
          </button>
        </div>
      </div>

      <div className="flex flex-col gap-1.5">
        <label htmlFor="reset-confirm" className="font-sans text-[0.72rem] tracking-[0.18em] uppercase text-pe-muted">
          {es ? 'Repite la contraseña' : 'Confirm password'}
        </label>
        <input
          id="reset-confirm"
          type={showPass ? 'text' : 'password'}
          required
          autoComplete="new-password"
          value={confirm}
          onChange={(e) => setConfirm(e.target.value)}
          className="bg-pe-white border border-pe-black/12 font-sans text-sm text-pe-charcoal px-3 py-2.5 focus:outline-hidden focus:border-pe-rose/60 transition-colors duration-200"
          placeholder={es ? 'Repite la contraseña' : 'Repeat the password'}
        />
      </div>

      {error && (
        <div className="flex items-center gap-2 font-sans text-[0.78rem] text-red-300 bg-red-500/20 border border-red-500/50 px-3 py-2.5">
          <AlertCircle size={14} className="shrink-0 text-red-400" />
          {error}
        </div>
      )}

      <button
        type="submit"
        disabled={loading}
        className="flex items-center justify-center gap-2 bg-pe-rose-action text-pe-offwhite font-sans text-[0.78rem] tracking-[0.18em] uppercase px-6 py-3 hover:bg-pe-rose-deep transition-colors duration-200 disabled:opacity-60 disabled:cursor-not-allowed"
      >
        {loading ? <Loader2 size={15} className="animate-spin" /> : <KeyRound size={15} />}
        {submitLabelFor(loading, es)}
      </button>
    </form>
  );
}
