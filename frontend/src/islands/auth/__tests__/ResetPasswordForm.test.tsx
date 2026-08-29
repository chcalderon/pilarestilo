import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';
import ResetPasswordForm from '../ResetPasswordForm';
import { ApiError } from '@/lib/api';

const resetPassword = vi.fn();

vi.mock('@/lib/api', async () => {
  const actual = await vi.importActual<typeof import('@/lib/api')>('@/lib/api');
  return {
    ...actual,
    resetPassword: (...args: unknown[]) => resetPassword(...args),
  };
});

function setSearch(search: string) {
  vi.stubGlobal('location', { ...window.location, search });
}

beforeEach(() => {
  vi.clearAllMocks();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('ResetPasswordForm', () => {
  it('renders the dead-link state when the URL has no token, without calling the API', () => {
    setSearch('');
    render(<ResetPasswordForm locale="es" />);
    expect(screen.getByText(/el enlace no es válido o ya expiró/i)).toBeInTheDocument();
    expect(resetPassword).not.toHaveBeenCalled();
  });

  it('rejects a password shorter than 8 characters before calling the API', async () => {
    setSearch('?token=abc123');
    const user = userEvent.setup();
    render(<ResetPasswordForm locale="es" />);

    await user.type(screen.getByLabelText(/nueva contraseña/i), 'short');
    await user.type(screen.getByLabelText(/repite la contraseña/i), 'short');
    await user.click(screen.getByRole('button', { name: /guardar contraseña/i }));

    expect(await screen.findByText(/al menos 8/i)).toBeInTheDocument();
    expect(resetPassword).not.toHaveBeenCalled();
  });

  it('rejects mismatched passwords', async () => {
    setSearch('?token=abc123');
    const user = userEvent.setup();
    render(<ResetPasswordForm locale="es" />);

    await user.type(screen.getByLabelText(/nueva contraseña/i), 'BrandNew123');
    await user.type(screen.getByLabelText(/repite la contraseña/i), 'Different123');
    await user.click(screen.getByRole('button', { name: /guardar contraseña/i }));

    expect(await screen.findByText(/no coinciden/i)).toBeInTheDocument();
    expect(resetPassword).not.toHaveBeenCalled();
  });

  it('submits and shows the success screen on 204', async () => {
    setSearch('?token=abc123');
    resetPassword.mockResolvedValue(undefined);
    const user = userEvent.setup();
    render(<ResetPasswordForm locale="es" />);

    await user.type(screen.getByLabelText(/nueva contraseña/i), 'BrandNew123');
    await user.type(screen.getByLabelText(/repite la contraseña/i), 'BrandNew123');
    await user.click(screen.getByRole('button', { name: /guardar contraseña/i }));

    await waitFor(() => expect(resetPassword).toHaveBeenCalledWith('abc123', 'BrandNew123'));
    expect(await screen.findByText(/contraseña actualizada/i)).toBeInTheDocument();
  });

  it('shows the generic dead-link state on a 400 from the backend', async () => {
    setSearch('?token=stale');
    resetPassword.mockRejectedValue(new ApiError('El enlace no es válido o ya expiró', 400));
    const user = userEvent.setup();
    render(<ResetPasswordForm locale="es" />);

    await user.type(screen.getByLabelText(/nueva contraseña/i), 'BrandNew123');
    await user.type(screen.getByLabelText(/repite la contraseña/i), 'BrandNew123');
    await user.click(screen.getByRole('button', { name: /guardar contraseña/i }));

    expect(await screen.findByText(/solicitar un enlace nuevo/i)).toBeInTheDocument();
  });
});
