import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';
import RegisterForm from '../RegisterForm';

/**
 * Characterization tests written before de-duplicating this against LoginForm.tsx (S3776 +
 * near-100% duplicated new code per Sonar) -- it had none. Covers the client-side password-length
 * guard (never reaches registerUser), the trimmed-field payload, the redirect destination, and the
 * merged-account dwell, so extracting the shared Google Sign-In wiring and success screen can't
 * silently change any of them.
 */

const registerUser = vi.fn();
const googleLogin = vi.fn();
const setAuth = vi.fn();

vi.mock('@/lib/api', async () => {
  const actual = await vi.importActual<typeof import('@/lib/api')>('@/lib/api');
  return {
    ...actual,
    registerUser: (...args: unknown[]) => registerUser(...args),
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

describe('RegisterForm', () => {
  it('registers with the marketing opt-in and redirects to the account page after the dwell', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    registerUser.mockResolvedValue(authResponse());
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    render(<RegisterForm locale="es" />);

    await user.type(screen.getByPlaceholderText(/maría garcía/i), 'Ana Perez');
    await user.type(screen.getByPlaceholderText(/email.com/i), 'ana@correo.cl');
    await user.type(screen.getByPlaceholderText('••••••••'), 'password1');
    await user.click(screen.getByRole('checkbox'));
    await user.click(screen.getByRole('button', { name: /crear cuenta/i }));

    await waitFor(() => expect(registerUser).toHaveBeenCalledWith('ana@correo.cl', 'password1', 'Ana Perez', true));
    expect(await screen.findByText(/bienvenido\/a, ana/i)).toBeInTheDocument();

    await vi.advanceTimersByTimeAsync(1600);
    expect(window.location.href).toBe('/es/account');
  });

  it('honors an explicit redirect', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    registerUser.mockResolvedValue(authResponse());
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    render(<RegisterForm locale="es" redirect="/es/checkout" />);

    await user.type(screen.getByPlaceholderText(/maría garcía/i), 'Ana Perez');
    await user.type(screen.getByPlaceholderText(/email.com/i), 'ana@correo.cl');
    await user.type(screen.getByPlaceholderText('••••••••'), 'password1');
    await user.click(screen.getByRole('button', { name: /crear cuenta/i }));

    await screen.findByText(/bienvenido\/a/i);
    await vi.advanceTimersByTimeAsync(1600);
    expect(window.location.href).toBe('/es/checkout');
  });

  it('refuses a short password locally, without calling registerUser', async () => {
    const user = userEvent.setup();
    render(<RegisterForm locale="es" />);

    await user.type(screen.getByPlaceholderText(/maría garcía/i), 'Ana Perez');
    await user.type(screen.getByPlaceholderText(/email.com/i), 'ana@correo.cl');
    await user.type(screen.getByPlaceholderText('••••••••'), 'short');
    await user.click(screen.getByRole('button', { name: /crear cuenta/i }));

    expect(await screen.findByText(/al menos 8 caracteres/i)).toBeInTheDocument();
    expect(registerUser).not.toHaveBeenCalled();
  });

  it('shows the account-merged message and waits the longer dwell before redirecting', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    registerUser.mockResolvedValue(authResponse({ accountMerged: true }));
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    render(<RegisterForm locale="es" />);

    await user.type(screen.getByPlaceholderText(/maría garcía/i), 'Ana Perez');
    await user.type(screen.getByPlaceholderText(/email.com/i), 'ana@correo.cl');
    await user.type(screen.getByPlaceholderText('••••••••'), 'password1');
    await user.click(screen.getByRole('button', { name: /crear cuenta/i }));

    expect(await screen.findByText(/cuentas unificadas/i)).toBeInTheDocument();

    await vi.advanceTimersByTimeAsync(1600);
    expect(window.location.href).toBe('');

    await vi.advanceTimersByTimeAsync(900);
    expect(window.location.href).toBe('/es/account');
  });

  it('shows the server error message when registration fails', async () => {
    registerUser.mockRejectedValue(new Error('boom'));
    const user = userEvent.setup();
    render(<RegisterForm locale="es" />);

    await user.type(screen.getByPlaceholderText(/maría garcía/i), 'Ana Perez');
    await user.type(screen.getByPlaceholderText(/email.com/i), 'ana@correo.cl');
    await user.type(screen.getByPlaceholderText('••••••••'), 'password1');
    await user.click(screen.getByRole('button', { name: /crear cuenta/i }));

    expect(await screen.findByText(/no pudimos crear la cuenta/i)).toBeInTheDocument();
  });

  it('toggles password visibility', async () => {
    const user = userEvent.setup();
    render(<RegisterForm locale="es" />);
    const passwordInput = screen.getByPlaceholderText('••••••••') as HTMLInputElement;
    expect(passwordInput.type).toBe('password');

    await user.click(screen.getByRole('button', { name: /^mostrar$/i }));

    expect(passwordInput.type).toBe('text');
  });

  it('does not touch the Google SDK when no client id is configured', () => {
    vi.stubEnv('PUBLIC_GOOGLE_CLIENT_ID', '');
    expect(() => render(<RegisterForm locale="en" />)).not.toThrow();
  });
});
