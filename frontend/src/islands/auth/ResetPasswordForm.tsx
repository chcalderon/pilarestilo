import { useState } from 'react';
import { Eye, EyeOff, Loader2, KeyRound, CheckCircle2, AlertCircle } from 'lucide-react';
import { resetPassword, ApiError } from '../../lib/api';

interface Props {
  readonly locale: 'es' | 'en';
}

const MIN_LENGTH = 8;

function togglePasswordLabelFor(showPass: boolean, es: boolean): string {
  if (showPass) return es ? 'Ocultar contraseña' : 'Hide password';
  return es ? 'Mostrar contraseña' : 'Show password';
}

function submitLabelFor(loading: boolean, es: boolean): string {
  if (loading) return es ? 'Guardando…' : 'Saving…';
  return es ? 'Guardar contraseña' : 'Save password';
}

function codeError(code: string, es: boolean): string | null {
  if (!/^\d{6}$/.test(code.trim())) {
    return es ? 'El código tiene 6 dígitos.' : 'The code is 6 digits.';
  }
  return null;
}

function passwordValidationError(password: string, confirm: string, es: boolean): string | null {
  if (password.length < MIN_LENGTH) {
    return es
      ? `La contraseña debe tener al menos ${MIN_LENGTH} caracteres.`
      : `The password must be at least ${MIN_LENGTH} characters.`;
  }
  if (password !== confirm) {
    return es ? 'Las contraseñas no coinciden.' : 'The passwords do not match.';
  }
  return null;
}

function submitErrorMessage(err: unknown, es: boolean): string {
  if (err instanceof ApiError && err.status === 429) {
    return es
      ? 'Demasiados intentos. Espera un momento e inténtalo otra vez.'
      : 'Too many attempts. Wait a moment and try again.';
  }
  if (err instanceof ApiError && (err.status === 400 || err.status === 422)) {
    return es
      ? 'El código no es válido o ya expiró. Pídelo de nuevo desde "¿Olvidaste tu contraseña?".'
      : 'The code is invalid or expired. Request a new one from "Forgot your password?".';
  }
  return es
    ? 'No pudimos actualizar la contraseña. Inténtalo de nuevo.'
    : 'We could not update the password. Please try again.';
}

function SuccessCard({ locale, es }: Readonly<{ locale: 'es' | 'en'; es: boolean }>) {
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

const COPY = {
  es: {
    emailLabel: 'Correo electrónico',
    emailPlaceholder: 'tu@correo.cl',
    codeLabel: 'Código de 6 dígitos',
    codePlaceholder: '000000',
    newLabel: 'Nueva contraseña',
    newPlaceholder: 'Al menos 8 caracteres',
    confirmLabel: 'Repite la contraseña',
    confirmPlaceholder: 'Repite la contraseña',
    intro: 'Escribe el código que te enviamos por correo y elige una nueva contraseña.',
  },
  en: {
    emailLabel: 'Email',
    emailPlaceholder: 'you@email.com',
    codeLabel: '6-digit code',
    codePlaceholder: '000000',
    newLabel: 'New password',
    newPlaceholder: 'At least 8 characters',
    confirmLabel: 'Confirm password',
    confirmPlaceholder: 'Repeat the password',
    intro: 'Enter the code we emailed you and choose a new password.',
  },
} as const;

export default function ResetPasswordForm({ locale }: Props) {
  const es = locale === 'es';
  const t = COPY[locale];
  const [email, setEmail] = useState('');
  const [code, setCode] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [showPass, setShowPass] = useState(false);
  const [loading, setLoading] = useState(false);
  const [done, setDone] = useState(false);
  const [error, setError] = useState('');

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError('');
    const ce = codeError(code, es);
    if (ce) {
      setError(ce);
      return;
    }
    const ve = passwordValidationError(password, confirm, es);
    if (ve) {
      setError(ve);
      return;
    }
    setLoading(true);
    try {
      await resetPassword(email.trim(), code.trim(), password);
      setDone(true);
    } catch (err) {
      setError(submitErrorMessage(err, es));
    } finally {
      setLoading(false);
    }
  }

  if (done) {
    return <SuccessCard locale={locale} es={es} />;
  }

  const togglePasswordLabel = togglePasswordLabelFor(showPass, es);
  const fieldClass =
    'bg-pe-white border border-pe-black/12 font-sans text-sm text-pe-charcoal px-3 py-2.5 focus:outline-hidden focus:border-pe-rose/60 focus-visible:ring-1 focus-visible:ring-pe-rose/40 transition-colors duration-200';
  const labelClass = 'font-sans text-[0.72rem] tracking-[0.18em] uppercase text-pe-muted';

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-5" noValidate>
      <p className="font-sans text-[0.82rem] text-pe-muted leading-relaxed">{t.intro}</p>

      <div className="flex flex-col gap-1.5">
        <label htmlFor="reset-email" className={labelClass}>{t.emailLabel}</label>
        <input
          id="reset-email"
          type="email"
          required
          autoComplete="email"
          value={email}
          onChange={(ev) => setEmail(ev.target.value)}
          className={fieldClass}
          placeholder={t.emailPlaceholder}
        />
      </div>

      <div className="flex flex-col gap-1.5">
        <label htmlFor="reset-code" className={labelClass}>{t.codeLabel}</label>
        <input
          id="reset-code"
          type="text"
          inputMode="numeric"
          pattern="\d*"
          maxLength={6}
          required
          autoComplete="one-time-code"
          value={code}
          onChange={(ev) => setCode(ev.target.value.replace(/\D/g, ''))}
          className={`${fieldClass} tracking-[0.4em] text-center`}
          placeholder={t.codePlaceholder}
        />
      </div>

      <div className="flex flex-col gap-1.5">
        <label htmlFor="reset-password" className={labelClass}>{t.newLabel}</label>
        <div className="relative">
          <input
            id="reset-password"
            type={showPass ? 'text' : 'password'}
            required
            autoComplete="new-password"
            value={password}
            onChange={(ev) => setPassword(ev.target.value)}
            className={`w-full pr-10 ${fieldClass}`}
            placeholder={t.newPlaceholder}
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
        <label htmlFor="reset-confirm" className={labelClass}>{t.confirmLabel}</label>
        <input
          id="reset-confirm"
          type={showPass ? 'text' : 'password'}
          required
          autoComplete="new-password"
          value={confirm}
          onChange={(ev) => setConfirm(ev.target.value)}
          className={fieldClass}
          placeholder={t.confirmPlaceholder}
        />
      </div>

      {error && (
        <div className="flex items-center gap-2 font-sans text-[0.78rem] text-pe-danger-ink bg-pe-danger-surface border border-pe-danger/50 px-3 py-2.5">
          <AlertCircle size={14} className="shrink-0 text-pe-danger-ink" />
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
