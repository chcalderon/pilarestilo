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

/** 400 means the link is spent — show the dead-link screen; anything else is an inline message. */
function submitErrorOutcome(err: unknown, es: boolean): { linkDead: boolean; message: string } {
  if (err instanceof ApiError && err.status === 400) {
    return { linkDead: true, message: '' };
  }
  if (err instanceof ApiError && err.status === 429) {
    return {
      linkDead: false,
      message: es
        ? 'Demasiados intentos. Espera un momento e inténtalo otra vez.'
        : 'Too many attempts. Wait a moment and try again.',
    };
  }
  return {
    linkDead: false,
    message: es
      ? 'No pudimos actualizar la contraseña. Inténtalo de nuevo.'
      : 'We could not update the password. Please try again.',
  };
}

function OutcomeCard({
  icon,
  title,
  body,
  href,
  cta,
  ctaAsButton,
}: Readonly<{
  icon: React.ReactNode;
  title: string;
  body: string;
  href: string;
  cta: string;
  ctaAsButton: boolean;
}>) {
  const ctaClass = ctaAsButton
    ? 'flex items-center justify-center gap-2 bg-pe-rose-action text-pe-offwhite font-sans text-[0.78rem] tracking-[0.18em] uppercase px-6 py-3 hover:bg-pe-rose-deep transition-colors duration-200'
    : 'font-sans text-[0.78rem] text-pe-rose-ink hover:underline underline-offset-2';
  return (
    <div className="flex flex-col items-center gap-5 py-8 text-center">
      {icon}
      <div>
        <p className="font-sans text-[0.95rem] text-pe-charcoal font-medium">{title}</p>
        <p className="font-sans text-[0.78rem] text-pe-muted mt-1.5 leading-relaxed">{body}</p>
      </div>
      <a href={href} className={ctaClass}>
        {cta}
      </a>
    </div>
  );
}

const COPY = {
  es: {
    newLabel: 'Nueva contraseña',
    newPlaceholder: 'Al menos 8 caracteres',
    confirmLabel: 'Repite la contraseña',
    confirmPlaceholder: 'Repite la contraseña',
    doneTitle: 'Contraseña actualizada',
    doneBody: 'Se cerraron todas las sesiones anteriores. Inicia sesión con tu nueva contraseña.',
    doneCta: 'Iniciar sesión',
    deadTitle: GENERIC_LINK_ERROR_ES,
    deadBody: 'Solicita un enlace nuevo para restablecer tu contraseña.',
    deadCta: 'Solicitar un enlace nuevo',
  },
  en: {
    newLabel: 'New password',
    newPlaceholder: 'At least 8 characters',
    confirmLabel: 'Confirm password',
    confirmPlaceholder: 'Repeat the password',
    doneTitle: 'Password updated',
    doneBody: 'Every earlier session was signed out. Sign in with your new password.',
    doneCta: 'Sign in',
    deadTitle: GENERIC_LINK_ERROR_EN,
    deadBody: 'Request a fresh link to reset your password.',
    deadCta: 'Request a new link',
  },
} as const;

export default function ResetPasswordForm({ locale }: Props) {
  const es = locale === 'es';
  const t = COPY[locale];
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
    const validationError = passwordValidationError(password, confirm, es);
    if (validationError) {
      setError(validationError);
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
      const outcome = submitErrorOutcome(err, es);
      if (outcome.linkDead) {
        setLinkDead(true);
      } else {
        setError(outcome.message);
      }
    } finally {
      setLoading(false);
    }
  }

  if (done) {
    return (
      <OutcomeCard
        icon={<CheckCircle2 size={44} className="text-pe-rose-ink" />}
        title={t.doneTitle}
        body={t.doneBody}
        href={`/${locale}/auth/login`}
        cta={t.doneCta}
        ctaAsButton
      />
    );
  }

  if (linkDead) {
    return (
      <OutcomeCard
        icon={<AlertCircle size={44} className="text-pe-rose-ink" />}
        title={t.deadTitle}
        body={t.deadBody}
        href={`/${locale}/auth/forgot-password`}
        cta={t.deadCta}
        ctaAsButton={false}
      />
    );
  }

  const togglePasswordLabel = togglePasswordLabelFor(showPass, es);

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-5" noValidate>
      <div className="flex flex-col gap-1.5">
        <label htmlFor="reset-password" className="font-sans text-[0.72rem] tracking-[0.18em] uppercase text-pe-muted">
          {t.newLabel}
        </label>
        <div className="relative">
          <input
            id="reset-password"
            type={showPass ? 'text' : 'password'}
            required
            autoComplete="new-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className="w-full bg-pe-white border border-pe-black/12 font-sans text-sm text-pe-charcoal px-3 py-2.5 pr-10 focus:outline-hidden focus:border-pe-rose/60 focus-visible:ring-1 focus-visible:ring-pe-rose/40 transition-colors duration-200"
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
        <label htmlFor="reset-confirm" className="font-sans text-[0.72rem] tracking-[0.18em] uppercase text-pe-muted">
          {t.confirmLabel}
        </label>
        <input
          id="reset-confirm"
          type={showPass ? 'text' : 'password'}
          required
          autoComplete="new-password"
          value={confirm}
          onChange={(e) => setConfirm(e.target.value)}
          className="bg-pe-white border border-pe-black/12 font-sans text-sm text-pe-charcoal px-3 py-2.5 focus:outline-hidden focus:border-pe-rose/60 focus-visible:ring-1 focus-visible:ring-pe-rose/40 transition-colors duration-200"
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
