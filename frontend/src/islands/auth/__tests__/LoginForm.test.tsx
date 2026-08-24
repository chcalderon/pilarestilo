import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';
import LoginForm from '../LoginForm';

/**
 * Characterization tests written before de-duplicating this against RegisterForm.tsx (S3776 +
 * near-100% duplicated new code per Sonar) -- it had none. Covers the redirect destination (admin
 * vs storefront, by role), the merged-account message, and the two dwell durations, so extracting
 * the shared Google Sign-In wiring and success screen can't silently change either.
 */

const loginUser = vi.fn();
const googleLogin = vi.fn();
const setAuth = vi.fn();

vi.mock('@/lib/api', async () => {
  const actual = await vi.importActual<typeof import('@/lib/api')>('@/lib/api');
  return {
    ...actual,
    loginUser: (...args: unknown[]) => loginUser(...args),
    googleLogin: (...args: unknown[]) => googleLogin(...args),
  };
});

vi.mock('@/lib/authStore', () => ({
  useAuthStore: () => ({ setAuth }),
}));

function authResponse(overrides: Record<string, unknown> = {}) {
  return {
    accessToken: 'access-token',
    userId: 'user-1',
    email: 'ana@correo.cl',
    role: 'CUSTOMER',
    fullName: 'Ana Perez',
    ...overrides,
  };
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.stubGlobal('location', { ...window.location, href: '' });
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.useRealTimers();
});

describe('LoginForm', () => {
  it('logs in and redirects a customer to the storefront after the welcome dwell', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    loginUser.mockResolvedValue(authResponse());
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    render(<LoginForm locale="es" />);

    await user.type(screen.getByPlaceholderText(/email.com/i), 'ana@correo.cl');
    await user.type(screen.getByPlaceholderText(/contraseña|password/i), 'password1');
    await user.click(screen.getByRole('button', { name: /iniciar sesi/i }));

    await waitFor(() => expect(loginUser).toHaveBeenCalledWith('ana@correo.cl', 'password1'));
    expect(await screen.findByText(/bienvenido\/a, ana/i)).toBeInTheDocument();
    expect(window.location.href).toBe('');

    await vi.advanceTimersByTimeAsync(1600);
    expect(window.location.href).toBe('/es/');
  });

  it('redirects an admin-panel role straight to the admin dashboard', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    loginUser.mockResolvedValue(authResponse({ role: 'ADMIN' }));
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    render(<LoginForm locale="es" />);

    await user.type(screen.getByPlaceholderText(/email.com/i), 'admin@correo.cl');
    await user.type(screen.getByPlaceholderText(/contraseña|password/i), 'password1');
    await user.click(screen.getByRole('button', { name: /iniciar sesi/i }));

    await screen.findByText(/bienvenido\/a/i);
    await vi.advanceTimersByTimeAsync(1600);
    expect(window.location.href).toBe('/admin/dashboard');
  });

  it('honors an explicit redirect for a non-admin role', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    loginUser.mockResolvedValue(authResponse());
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    render(<LoginForm locale="es" redirect="/es/checkout" />);

    await user.type(screen.getByPlaceholderText(/email.com/i), 'ana@correo.cl');
    await user.type(screen.getByPlaceholderText(/contraseña|password/i), 'password1');
    await user.click(screen.getByRole('button', { name: /iniciar sesi/i }));

    await screen.findByText(/bienvenido\/a/i);
    await vi.advanceTimersByTimeAsync(1600);
    expect(window.location.href).toBe('/es/checkout');
  });

  it('shows the account-merged message and waits the longer dwell before redirecting', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    loginUser.mockResolvedValue(authResponse({ accountMerged: true }));
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    render(<LoginForm locale="es" />);

    await user.type(screen.getByPlaceholderText(/email.com/i), 'ana@correo.cl');
    await user.type(screen.getByPlaceholderText(/contraseña|password/i), 'password1');
    await user.click(screen.getByRole('button', { name: /iniciar sesi/i }));

    expect(await screen.findByText(/cuentas unificadas/i)).toBeInTheDocument();

    await vi.advanceTimersByTimeAsync(1600);
    expect(window.location.href).toBe('');

    await vi.advanceTimersByTimeAsync(900);
    expect(window.location.href).toBe('/es/');
  });

  it('shows an error message when login fails, and does not redirect', async () => {
    loginUser.mockRejectedValue(new Error('boom'));
    const user = userEvent.setup();
    render(<LoginForm locale="es" />);

    await user.type(screen.getByPlaceholderText(/email.com/i), 'ana@correo.cl');
    await user.type(screen.getByPlaceholderText(/contraseña|password/i), 'wrong');
    await user.click(screen.getByRole('button', { name: /iniciar sesi/i }));

    expect(await screen.findByText(/email o contraseña incorrectos/i)).toBeInTheDocument();
    expect(window.location.href).toBe('');
  });

  it('toggles password visibility', async () => {
    const user = userEvent.setup();
    render(<LoginForm locale="es" />);
    const passwordInput = screen.getByPlaceholderText(/contraseña|password/i) as HTMLInputElement;
    expect(passwordInput.type).toBe('password');

    await user.click(screen.getByRole('button', { name: /mostrar contrase/i }));

    expect(passwordInput.type).toBe('text');
  });

  it('does not touch the Google SDK when no client id is configured', () => {
    vi.stubEnv('PUBLIC_GOOGLE_CLIENT_ID', '');
    expect(() => render(<LoginForm locale="en" />)).not.toThrow();
  });
});
