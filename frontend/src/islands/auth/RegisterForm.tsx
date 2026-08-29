import { useRef, useState } from 'react';
import { Eye, EyeOff, UserPlus, Loader2 } from 'lucide-react';
import { registerUser, googleLogin } from '../../lib/api';
import { useGoogleSignIn } from '../../lib/useGoogleSignIn';
import { useAuthRedirect } from '../../lib/useAuthRedirect';
import { useAuthAction } from '../../lib/useAuthAction';
import { AuthSuccessScreen } from '../../components/auth/AuthSuccessScreen';

interface Props {
  readonly locale: 'es' | 'en';
  readonly redirect?: string;
}

function togglePasswordLabelFor(showPass: boolean, es: boolean): string {
  if (showPass) return es ? 'Ocultar' : 'Hide';
  return es ? 'Mostrar' : 'Show';
}

function submitLabelFor(loading: boolean, es: boolean): string {
  if (loading) return es ? 'Creando cuenta…' : 'Creating account…';
  return es ? 'Crear cuenta' : 'Create account';
}

interface ConsentProps {
  readonly locale: 'es' | 'en';
  readonly es: boolean;
  readonly marketing: boolean;
  readonly onMarketingChange: (checked: boolean) => void;
}

/** The terms come with the account and are not a checkbox; marketing is opt-in and is. */
function RegisterConsent({ locale, es, marketing, onMarketingChange }: ConsentProps) {
  return (
    <div className="flex flex-col gap-3">
      <label className="flex items-start gap-2.5 cursor-pointer">
        <input
          type="checkbox"
          checked={marketing}
          onChange={(e) => onMarketingChange(e.target.checked)}
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
  );
}

export default function RegisterForm({ locale, redirect }: Props) {
  const [email, setEmail]         = useState('');
  const [fullName, setFullName]   = useState('');
  const [password, setPassword]   = useState('');
  const [showPass, setShowPass]   = useState(false);
  const [marketing, setMarketing] = useState(false);
  const googleBtnRef              = useRef<HTMLDivElement>(null);

  const es = locale === 'es';

  const { success, finishAuth } = useAuthRedirect(() => redirect ?? `/${locale}/account`);
  const { loading, error, setError, run } = useAuthAction(finishAuth);

  useGoogleSignIn({
    clientId: (import.meta as any).env?.PUBLIC_GOOGLE_CLIENT_ID as string | undefined,
    buttonRef: googleBtnRef,
    onCredential: (credential) => run(
      () => googleLogin(credential),
      es ? 'No se pudo registrar con Google.' : 'Could not register with Google.'
    ),
    buttonText: 'signup_with',
    buttonWidth: 320,
  });

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (password.length < 8) {
      setError(es ? 'La contraseña debe tener al menos 8 caracteres.' : 'Password must be at least 8 characters.');
      return;
    }
    void run(
      () => registerUser(email, password, fullName, marketing),
      es ? 'No pudimos crear la cuenta. Intenta con otro email.' : 'Could not create account. Try a different email.'
    );
  }

  if (success) {
    return <AuthSuccessScreen success={success} es={es} />;
  }

  const togglePasswordLabel = togglePasswordLabelFor(showPass, es);
  const submitLabel = submitLabelFor(loading, es);

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
            className="absolute right-3 top-1/2 -translate-y-1/2 text-pe-muted hover:text-pe-rose-ink transition-colors"
            aria-label={togglePasswordLabel}
          >
            {showPass ? <EyeOff size={15} /> : <Eye size={15} />}
          </button>
        </div>
      </div>

      {/* Error */}
      {error && (
        <p className="font-sans text-[0.78rem] text-pe-danger-ink bg-pe-danger-surface px-3 py-2 border border-pe-danger/40">
          {error}
        </p>
      )}

      <RegisterConsent locale={locale} es={es} marketing={marketing} onMarketingChange={setMarketing} />

      {/* Submit */}
      <button
        type="submit"
        disabled={loading}
        className="flex items-center justify-center gap-2 bg-pe-rose-action text-pe-offwhite font-sans text-[0.78rem] tracking-[0.18em] uppercase px-6 py-3 hover:bg-pe-rose-deep transition-colors duration-200 disabled:opacity-60 disabled:cursor-not-allowed"
      >
        {loading ? <Loader2 size={15} className="animate-spin" /> : <UserPlus size={15} />}
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
