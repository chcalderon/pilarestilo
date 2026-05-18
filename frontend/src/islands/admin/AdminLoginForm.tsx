import { useState } from 'react';
import { Eye, EyeOff, LogIn, Loader2 } from 'lucide-react';
import { loginUser } from '../../lib/api';
import { useAuthStore } from '../../lib/authStore';

interface Props {
  redirect?: string;
}

export default function AdminLoginForm({ redirect }: Props) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPass, setShowPass] = useState(false);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const { setAuth } = useAuthStore();

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setLoading(true);
    setError('');
    try {
      const data = await loginUser(email, password);
      const allowedRoles = ['ADMIN', 'SUPERVISOR', 'ADMINISTRACION', 'DESPACHADOR', 'SELLER'];
      if (!allowedRoles.includes(data.role)) {
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
      document.cookie = `pe_token=${data.accessToken}; path=/; max-age=86400; SameSite=Lax`;
      const target = redirect && redirect.startsWith('/admin') && !redirect.startsWith('/admin/login')
        ? redirect
        : '/admin/dashboard';
      window.location.href = target;
    } catch (error) {
      const detail = error instanceof Error ? error.message : '';
      if (/API error 5\d{2}\b|API timeout\b/i.test(detail)) {
        setError('El servidor administrativo no est\u00e1 disponible en este momento. Intenta nuevamente en unos minutos.');
      } else {
        setError('Email o contrase\u00f1a incorrectos.');
      }
    } finally {
      setLoading(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-5" noValidate>
      <div className="flex flex-col gap-1.5">
        <label className="font-sans text-[0.72rem] tracking-[0.18em] uppercase text-pe-charcoal/60">
          Correo electr\u00f3nico
        </label>
        <input
          type="email"
          required
          autoComplete="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          className="bg-pe-white border border-pe-black/12 font-sans text-sm text-pe-charcoal px-3 py-2.5 focus:outline-none focus:border-pe-rose/60 transition-colors duration-200 placeholder:text-pe-charcoal/30"
          placeholder="admin@pilarestilo.com"
        />
      </div>

      <div className="flex flex-col gap-1.5">
        <label className="font-sans text-[0.72rem] tracking-[0.18em] uppercase text-pe-charcoal/60">
          Contrase\u00f1a
        </label>
        <div className="relative">
          <input
            type={showPass ? 'text' : 'password'}
            required
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className="w-full bg-pe-white border border-pe-black/12 font-sans text-sm text-pe-charcoal px-3 py-2.5 pr-10 focus:outline-none focus:border-pe-rose/60 transition-colors duration-200"
            placeholder="********"
          />
          <button
            type="button"
            onClick={() => setShowPass((v) => !v)}
            className="absolute right-3 top-1/2 -translate-y-1/2 text-pe-charcoal/40 hover:text-pe-rose transition-colors"
            aria-label={showPass ? 'Ocultar contrase\u00f1a' : 'Mostrar contrase\u00f1a'}
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
