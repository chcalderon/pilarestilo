import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';
import { RegisterPopoverForm } from '../RegisterPopoverForm';
import type { AuthTokenResponse } from '@/lib/api';

/**
 * Characterization tests written before reducing this component's Cognitive Complexity (S3776)
 * -- it had none. Covers the tab switch, both submit paths, the shared post-auth welcome dwell,
 * and validation/error surfacing, so the Google Sign-In effect can be extracted into its own hook
 * without silently changing the password form's behaviour.
 */

const registerUser = vi.fn();
const loginUser = vi.fn();
const googleLogin = vi.fn();
const setAuth = vi.fn();

vi.mock('@/lib/api', async () => {
  const actual = await vi.importActual<typeof import('@/lib/api')>('@/lib/api');
  return {
    ...actual,
    registerUser: (...args: unknown[]) => registerUser(...args),
    loginUser: (...args: unknown[]) => loginUser(...args),
    googleLogin: (...args: unknown[]) => googleLogin(...args),
  };
});

vi.mock('@/lib/authStore', () => ({
  useAuthStore: (selector: (s: { setAuth: typeof setAuth }) => unknown) => selector({ setAuth }),
}));

function authResponse(overrides: Partial<AuthTokenResponse> = {}): AuthTokenResponse {
  return {
    accessToken: 'access-token',
    refreshToken: 'refresh-token',
    tokenType: 'Bearer',
    userId: 'user-1',
    email: 'ana@correo.cl',
    role: 'CUSTOMER',
    fullName: 'Ana Perez',
    permissions: [],
    ...overrides,
  } as AuthTokenResponse;
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.stubGlobal('location', { ...window.location, reload: vi.fn() });
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.useRealTimers();
});

/**
 * The tab-switcher button and the submit button render the identical accessible name
 * ("Iniciar sesion" / "Log in") whenever the login tab is active, so `getByRole` can't tell
 * them apart. The submit button is the only `type="submit"` on the page.
 */
function getSubmitButton(container: HTMLElement): HTMLButtonElement {
  const button = container.querySelector('button[type="submit"]');
  if (!button) throw new Error('submit button not found');
  return button as HTMLButtonElement;
}

describe('RegisterPopoverForm', () => {
  it('shows the register tab by default, with the name field', () => {
    render(<RegisterPopoverForm locale="es" />);

    expect(screen.getByLabelText(/nombre/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /crear cuenta/i })).toBeInTheDocument();
  });

  it('honors initialTab="login" and hides the name field', () => {
    const { container } = render(<RegisterPopoverForm locale="es" initialTab="login" />);

    expect(screen.queryByLabelText(/^nombre/i)).not.toBeInTheDocument();
    expect(getSubmitButton(container)).toHaveTextContent(/iniciar sesion/i);
  });

  it('switches tabs on click, clearing any prior error', async () => {
    loginUser.mockRejectedValue(new Error('Credenciales invalidas'));
    const user = userEvent.setup();
    const { container } = render(<RegisterPopoverForm locale="es" initialTab="login" />);

    await user.type(screen.getByLabelText(/email/i), 'ana@correo.cl');
    await user.type(screen.getByLabelText(/^contrasena$/i), 'password1');
    await user.click(getSubmitButton(container));
    await screen.findByRole('alert');

    await user.click(screen.getByRole('button', { name: /registrarse/i }));

    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(screen.getByLabelText(/^nombre/i)).toBeInTheDocument();
  });

  it('registers with the trimmed form fields including the marketing opt-in', async () => {
    registerUser.mockResolvedValue(authResponse());
    const user = userEvent.setup();
    render(<RegisterPopoverForm locale="es" />);

    await user.type(screen.getByLabelText(/nombre/i), 'Ana Perez');
    await user.type(screen.getByLabelText(/email/i), 'ana@correo.cl');
    await user.type(screen.getByLabelText(/^contrasena$/i), 'password1');
    await user.click(screen.getByRole('checkbox'));
    await user.click(screen.getByRole('button', { name: /crear cuenta/i }));

    await waitFor(() => expect(registerUser).toHaveBeenCalledWith('ana@correo.cl', 'password1', 'Ana Perez', true));
  });

  it('logs in without the marketing flag or the name field', async () => {
    loginUser.mockResolvedValue(authResponse());
    const user = userEvent.setup();
    const { container } = render(<RegisterPopoverForm locale="es" initialTab="login" />);

    await user.type(screen.getByLabelText(/email/i), 'ana@correo.cl');
    await user.type(screen.getByLabelText(/^contrasena$/i), 'password1');
    await user.click(getSubmitButton(container));

    await waitFor(() => expect(loginUser).toHaveBeenCalledWith('ana@correo.cl', 'password1'));
    expect(registerUser).not.toHaveBeenCalled();
  });

  it('shows the server error message when login fails', async () => {
    loginUser.mockRejectedValue(new Error('Email o contrasena incorrectos'));
    const user = userEvent.setup();
    const { container } = render(<RegisterPopoverForm locale="es" initialTab="login" />);

    await user.type(screen.getByLabelText(/email/i), 'ana@correo.cl');
    await user.type(screen.getByLabelText(/^contrasena$/i), 'wrong');
    await user.click(getSubmitButton(container));

    expect(await screen.findByRole('alert')).toHaveTextContent('Email o contrasena incorrectos');
  });

  it('falls back to a generic error message when the failure carries none', async () => {
    loginUser.mockRejectedValue('not an Error instance');
    const user = userEvent.setup();
    const { container } = render(<RegisterPopoverForm locale="es" initialTab="login" />);

    await user.type(screen.getByLabelText(/email/i), 'ana@correo.cl');
    await user.type(screen.getByLabelText(/^contrasena$/i), 'password1');
    await user.click(getSubmitButton(container));

    expect(await screen.findByRole('alert')).toHaveTextContent(/error al procesar/i);
  });

  it('shows the localized generic error in English', async () => {
    loginUser.mockRejectedValue('not an Error instance');
    const user = userEvent.setup();
    const { container } = render(<RegisterPopoverForm locale="en" initialTab="login" />);

    await user.type(screen.getByLabelText(/email/i), 'ana@correo.cl');
    await user.type(screen.getByLabelText(/^password$/i), 'password1');
    await user.click(getSubmitButton(container));

    expect(await screen.findByRole('alert')).toHaveTextContent(/failed to process request/i);
  });

  it('toggles password visibility', async () => {
    const user = userEvent.setup();
    render(<RegisterPopoverForm locale="es" initialTab="login" />);
    const passwordInput = screen.getByLabelText(/^contrasena$/i) as HTMLInputElement;
    expect(passwordInput.type).toBe('password');

    await user.click(screen.getByRole('button', { name: /mostrar contrasena/i }));

    expect(passwordInput.type).toBe('text');
  });

  it('disables the fields and shows the processing label while submitting', async () => {
    let resolveLogin: (value: AuthTokenResponse) => void = () => {};
    loginUser.mockReturnValue(new Promise((resolve) => { resolveLogin = resolve; }));
    const user = userEvent.setup();
    const { container } = render(<RegisterPopoverForm locale="es" initialTab="login" />);

    await user.type(screen.getByLabelText(/email/i), 'ana@correo.cl');
    await user.type(screen.getByLabelText(/^contrasena$/i), 'password1');
    await user.click(getSubmitButton(container));

    expect(screen.getByLabelText(/email/i)).toBeDisabled();
    expect(screen.getByText(/procesando/i)).toBeInTheDocument();

    resolveLogin(authResponse());
    expect(await screen.findByText(/bienvenido\/a/i)).toBeInTheDocument();
  });

  it('shows the welcome message immediately on success, before the reload dwell elapses', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    loginUser.mockResolvedValue(authResponse({ fullName: 'Ana Perez' }));
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    const { container } = render(<RegisterPopoverForm locale="es" initialTab="login" />);

    await user.type(screen.getByLabelText(/email/i), 'ana@correo.cl');
    await user.type(screen.getByLabelText(/^contrasena$/i), 'password1');
    await user.click(getSubmitButton(container));

    expect(await screen.findByText(/bienvenido\/a, ana/i)).toBeInTheDocument();
    expect(setAuth).not.toHaveBeenCalled();

    await vi.advanceTimersByTimeAsync(1600);
    expect(setAuth).toHaveBeenCalledWith('access-token', expect.objectContaining({ id: 'user-1' }));
  });

  it('falls back to the local part of the email for the welcome name when fullName is missing', async () => {
    loginUser.mockResolvedValue(authResponse({ fullName: undefined, email: 'sinnombre@correo.cl' }));
    const user = userEvent.setup();
    const { container } = render(<RegisterPopoverForm locale="es" initialTab="login" />);

    await user.type(screen.getByLabelText(/email/i), 'sinnombre@correo.cl');
    await user.type(screen.getByLabelText(/^contrasena$/i), 'password1');
    await user.click(getSubmitButton(container));

    expect(await screen.findByText(/bienvenido\/a, sinnombre/i)).toBeInTheDocument();
  });

  it('does not touch the Google SDK when no client id is configured', () => {
    vi.stubEnv('PUBLIC_GOOGLE_CLIENT_ID', '');
    expect(() => render(<RegisterPopoverForm locale="es" />)).not.toThrow();
  });
});
