import { useState } from 'react';
import { Eye, EyeOff, LogIn, Loader2 } from 'lucide-react';
import { loginUser } from '../../lib/api';
import { useAuthStore } from '../../lib/authStore';

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
  const { setAuth }                  = useAuthStore();

  const es = locale === 'es';

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setLoading(true);
    setError('');
    try {
      const data = await loginUser(email, password);
      setAuth(data.accessToken, { id: data.userId, email: data.email, role: data.role });
      document.cookie = `pe_token=${data.accessToken}; path=/; max-age=86400; SameSite=Lax`;
      window.location.href = redirect ?? `/${locale}/account`;
    } catch {
      setError(es ? 'Email o contraseña incorrectos.' : 'Invalid email or password.');
    } finally {
      setLoading(false);
    }
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
          className="bg-pe-white border border-pe-black/12 font-sans text-sm text-pe-charcoal px-3 py-2.5 focus:outline-none focus:border-pe-rose/60 transition-colors duration-200 placeholder:text-pe-charcoal/30"
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
            className="w-full bg-pe-white border border-pe-black/12 font-sans text-sm text-pe-charcoal px-3 py-2.5 pr-10 focus:outline-none focus:border-pe-rose/60 transition-colors duration-200"
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
        {loading ? <Loader2 size={15} className="animate-spin" /> : <LogIn size={15} />}
        {loading ? (es ? 'Ingresando…' : 'Signing in…') : (es ? 'Iniciar sesión' : 'Sign in')}
      </button>

      {/* Register link */}
      <p className="font-sans text-[0.78rem] text-pe-charcoal/50 text-center">
        {es ? '¿No tenés cuenta?' : "Don't have an account?"}{' '}
        <a
          href={`/${locale}/auth/register`}
          className="text-pe-rose-deep hover:underline underline-offset-2"
        >
          {es ? 'Registrate' : 'Register'}
        </a>
      </p>
    </form>
  );
}
