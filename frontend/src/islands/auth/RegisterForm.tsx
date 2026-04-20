import { useState } from 'react';
import { Eye, EyeOff, UserPlus, Loader2 } from 'lucide-react';
import { registerUser } from '../../lib/api';
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
  const { setAuth }              = useAuthStore();

  const es = locale === 'es';

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
      setAuth(data.accessToken, { id: data.userId, email: data.email, role: data.role });
      document.cookie = `pe_token=${data.accessToken}; path=/; max-age=86400; SameSite=Lax`;
      window.location.href = redirect ?? `/${locale}/account`;
    } catch {
      setError(
        es
          ? 'No pudimos crear la cuenta. Intentá con otro email.'
          : 'Could not create account. Try a different email.'
      );
    } finally {
      setLoading(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-5" noValidate>
      {/* Full name */}
      <div className="flex flex-col gap-1.5">
        <label className="font-sans text-[0.72rem] tracking-[0.18em] uppercase text-pe-charcoal/60">
          {es ? 'Nombre completo' : 'Full name'}
        </label>
        <input
          type="text"
          required
          autoComplete="name"
          value={fullName}
          onChange={e => setFullName(e.target.value)}
          className="bg-pe-white border border-pe-black/12 font-sans text-sm text-pe-charcoal px-3 py-2.5 focus:outline-none focus:border-pe-rose/60 transition-colors duration-200 placeholder:text-pe-charcoal/30"
          placeholder={es ? 'María García' : 'Jane Smith'}
        />
      </div>

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
          className="bg-pe-white border border-pe-black/12 font-sans text-sm text-pe-charcoal px-3 py-2.5 focus:outline-none focus:border-pe-rose/60 transition-colors duration-200 placeholder:text-pe-charcoal/30"
          placeholder={es ? 'tu@email.com' : 'you@email.com'}
        />
      </div>

      {/* Password */}
      <div className="flex flex-col gap-1.5">
        <label className="font-sans text-[0.72rem] tracking-[0.18em] uppercase text-pe-charcoal/60">
          {es ? 'Contraseña' : 'Password'}
          <span className="ml-1.5 text-pe-charcoal/35 normal-case tracking-normal">
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
            className="w-full bg-pe-white border border-pe-black/12 font-sans text-sm text-pe-charcoal px-3 py-2.5 pr-10 focus:outline-none focus:border-pe-rose/60 transition-colors duration-200"
            placeholder="••••••••"
          />
          <button
            type="button"
            onClick={() => setShowPass(v => !v)}
            className="absolute right-3 top-1/2 -translate-y-1/2 text-pe-charcoal/40 hover:text-pe-rose transition-colors"
            aria-label={showPass ? (es ? 'Ocultar' : 'Hide') : (es ? 'Mostrar' : 'Show')}
          >
            {showPass ? <EyeOff size={15} /> : <Eye size={15} />}
          </button>
        </div>
      </div>

      {/* Error */}
      {error && (
        <p className="font-sans text-[0.78rem] text-pe-rose-deep bg-pe-rose-soft/40 px-3 py-2 border-l-2 border-pe-rose">
          {error}
        </p>
      )}

      {/* Submit */}
      <button
        type="submit"
        disabled={loading}
        className="flex items-center justify-center gap-2 bg-pe-rose text-pe-offwhite font-sans text-[0.78rem] tracking-[0.18em] uppercase px-6 py-3 hover:bg-pe-rose-deep transition-colors duration-200 disabled:opacity-60 disabled:cursor-not-allowed"
      >
        {loading ? <Loader2 size={15} className="animate-spin" /> : <UserPlus size={15} />}
        {loading ? (es ? 'Creando cuenta…' : 'Creating account…') : (es ? 'Crear cuenta' : 'Create account')}
      </button>

      {/* Login link */}
      <p className="font-sans text-[0.78rem] text-pe-charcoal/50 text-center">
        {es ? '¿Ya tenés cuenta?' : 'Already have an account?'}{' '}
        <a
          href={`/${locale}/auth/login`}
          className="text-pe-rose-deep hover:underline underline-offset-2"
        >
          {es ? 'Iniciá sesión' : 'Sign in'}
        </a>
      </p>
    </form>
  );
}
