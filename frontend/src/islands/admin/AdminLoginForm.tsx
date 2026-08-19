import { useState } from 'react';
import { Eye, EyeOff, LogIn, Loader2 } from 'lucide-react';
import { loginUser } from '../../lib/api';
import { useAuthStore } from '../../lib/authStore';
import { isAdminPanelRole } from '../../lib/roles';

interface Props {
  redirect?: string;
  /** Set when the middleware bounced us here because the backend was unreachable, not on a 401. */
  backendUnavailable?: boolean;
}

const BACKEND_UNAVAILABLE_MESSAGE =
  'El servidor administrativo no está disponible en este momento. Intenta nuevamente en unos minutos.';

export default function AdminLoginForm({ redirect, backendUnavailable = false }: Props) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPass, setShowPass] = useState(false);
  const [error, setError] = useState(backendUnavailable ? BACKEND_UNAVAILABLE_MESSAGE : '');
  const [loading, setLoading] = useState(false);
  const { setAuth } = useAuthStore();

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setLoading(true);
    setError('');
    try {
      const data = await loginUser(email, password);
      if (!isAdminPanelRole(data.role)) {
        setError('Acceso denegado. Se requieren privilegios de administrador.');
        return;
      }
      setAuth(data.accessToken, {
        id: data.userId,
        email: data.email,
        role: data.role,
        permissions: data.permissions ?? [],
        permissionCodes: data.permissionCodes ?? [],
        vigencyStart: data.vigencyStart,
        vigencyEnd: data.vigencyEnd,
      });
      const target = redirect && redirect.startsWith('/admin') && !redirect.startsWith('/admin/login')
        ? redirect
        : '/admin/dashboard';
      window.location.href = target;
    } catch (error) {
      const detail = error instanceof Error ? error.message : '';
      if (/API error 5\d{2}\b|API timeout\b/i.test(detail)) {
        setError(BACKEND_UNAVAILABLE_MESSAGE);
      } else {
        setError('Email o contraseña incorrectos.');
      }
    } finally {
      setLoading(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-5" noValidate>
      <div className="flex flex-col gap-1.5">
        <label className="font-sans text-[0.72rem] tracking-[0.18em] uppercase text-pe-charcoal/60">
          Correo electrónico
        </label>
        <input
          type="email"
          required
          autoComplete="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          className="bg-pe-white border border-pe-black/12 font-sans text-sm text-pe-charcoal px-3 py-2.5 focus:outline-hidden focus:border-pe-rose/60 transition-colors duration-200 placeholder:text-pe-charcoal/30"
          placeholder="admin@pilarestilo.com"
        />
      </div>

      <div className="flex flex-col gap-1.5">
        <label className="font-sans text-[0.72rem] tracking-[0.18em] uppercase text-pe-charcoal/60">
          Contraseña
        </label>
        <div className="relative">
          <input
            type={showPass ? 'text' : 'password'}
            required
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className="w-full bg-pe-white border border-pe-black/12 font-sans text-sm text-pe-charcoal px-3 py-2.5 pr-10 focus:outline-hidden focus:border-pe-rose/60 transition-colors duration-200"
            placeholder="********"
          />
          <button
            type="button"
            onClick={() => setShowPass((v) => !v)}
            className="absolute right-3 top-1/2 -translate-y-1/2 text-pe-charcoal/40 hover:text-pe-rose transition-colors"
            aria-label={showPass ? 'Ocultar contraseña' : 'Mostrar contraseña'}
          >
            {showPass ? <EyeOff size={15} /> : <Eye size={15} />}
          </button>
        </div>
      </div>

      {error && (
        <p className="font-sans text-[0.78rem] font-medium text-[#FFF4F6] bg-[#8E4F58]/78 px-3 py-2 border border-[#E8C9CC]/55 shadow-[inset_3px_0_0_0_#E8C9CC]">
          {error}
        </p>
      )}

      <button
        type="submit"
        disabled={loading}
        className="flex items-center justify-center gap-2 bg-pe-rose text-pe-offwhite font-sans text-[0.78rem] tracking-[0.18em] uppercase px-6 py-3 hover:bg-pe-rose-deep transition-colors duration-200 disabled:opacity-60 disabled:cursor-not-allowed"
      >
        {loading ? <Loader2 size={15} className="animate-spin" /> : <LogIn size={15} />}
        {loading ? 'Ingresando...' : 'Acceder al panel'}
      </button>

      <p className="font-sans text-[0.72rem] text-pe-charcoal/40 text-center">
        Acceso exclusivo para administradores
      </p>
    </form>
  );
}
