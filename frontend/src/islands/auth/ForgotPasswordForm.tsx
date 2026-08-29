import { useState } from 'react';
import { Loader2, MailCheck, Send, AlertCircle } from 'lucide-react';
import { requestPasswordReset, ApiError } from '../../lib/api';

interface Props {
  readonly locale: 'es' | 'en';
}

function submitLabelFor(loading: boolean, es: boolean): string {
  if (loading) return es ? 'Enviando…' : 'Sending…';
  return es ? 'Enviar enlace' : 'Send link';
}

export default function ForgotPasswordForm({ locale }: Props) {
  const es = locale === 'es';
  const [email, setEmail] = useState('');
  const [loading, setLoading] = useState(false);
  const [sent, setSent] = useState(false);
  const [error, setError] = useState('');

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setLoading(true);
    setError('');
    try {
      await requestPasswordReset(email.trim());
      setSent(true);
    } catch (err) {
      if (err instanceof ApiError && err.status === 429) {
        setError(es ? 'Demasiados intentos. Espera un momento e inténtalo otra vez.'
                    : 'Too many attempts. Wait a moment and try again.');
      } else if (err instanceof ApiError && err.status === 422) {
        setError(es ? 'Ingresa un correo electrónico válido.' : 'Enter a valid email address.');
      } else {
        setError(es ? 'No pudimos procesar la solicitud. Inténtalo de nuevo.'
                    : 'We could not process the request. Please try again.');
      }
    } finally {
      setLoading(false);
    }
  }

  if (sent) {
    return (
      <div className="flex flex-col items-center gap-5 py-8 text-center">
        <MailCheck size={44} className="text-pe-rose-ink" />
        <div>
          <p className="font-sans text-[0.95rem] text-pe-charcoal font-medium">
            {es ? 'Revisa tu correo' : 'Check your inbox'}
          </p>
          <p className="font-sans text-[0.78rem] text-pe-muted mt-1.5 leading-relaxed">
            {es
              ? 'Si el correo pertenece a una cuenta, te enviamos un enlace para restablecer tu contraseña. El enlace expira en 30 minutos.'
              : 'If the address belongs to an account, we sent a link to reset your password. It expires in 30 minutes.'}
          </p>
        </div>
        <a
          href={`/${locale}/auth/login`}
          className="font-sans text-[0.78rem] text-pe-rose-ink hover:underline underline-offset-2"
        >
          {es ? 'Volver a iniciar sesión' : 'Back to sign in'}
        </a>
      </div>
    );
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-5" noValidate>
      <p className="font-sans text-[0.82rem] text-pe-muted leading-relaxed">
        {es
          ? 'Ingresa el correo de tu cuenta y te enviaremos un enlace para elegir una nueva contraseña.'
          : 'Enter your account email and we will send you a link to choose a new password.'}
      </p>

      <div className="flex flex-col gap-1.5">
        <label htmlFor="forgot-email" className="font-sans text-[0.72rem] tracking-[0.18em] uppercase text-pe-muted">
          {es ? 'Correo electrónico' : 'Email'}
        </label>
        <input
          id="forgot-email"
          type="email"
          required
          autoComplete="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          className="bg-pe-white border border-pe-black/12 font-sans text-sm text-pe-charcoal px-3 py-2.5 focus:outline-hidden focus:border-pe-rose/60 transition-colors duration-200 placeholder:text-pe-muted"
          placeholder={es ? 'tu@email.com' : 'you@email.com'}
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
        {loading ? <Loader2 size={15} className="animate-spin" /> : <Send size={15} />}
        {submitLabelFor(loading, es)}
      </button>

      <p className="font-sans text-[0.78rem] text-pe-muted text-center">
        <a
          href={`/${locale}/auth/login`}
          className="text-pe-rose-ink hover:underline underline-offset-2"
        >
          {es ? 'Volver a iniciar sesión' : 'Back to sign in'}
        </a>
      </p>
    </form>
  );
}
