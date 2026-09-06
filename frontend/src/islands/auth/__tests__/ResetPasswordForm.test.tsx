import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';
import ResetPasswordForm from '../ResetPasswordForm';
import { ApiError } from '@/lib/api';

const resetPassword = vi.fn();
vi.mock('@/lib/api', async () => {
  const actual = await vi.importActual<typeof import('@/lib/api')>('@/lib/api');
  return { ...actual, resetPassword: (...a: unknown[]) => resetPassword(...a) };
});

beforeEach(() => vi.clearAllMocks());

async function fill(
  user: ReturnType<typeof userEvent.setup>,
  over: Partial<Record<'email' | 'code' | 'pass' | 'confirm', string>> = {},
) {
  await user.type(screen.getByLabelText(/correo/i), over.email ?? 'camila@example.com');
  await user.type(screen.getByLabelText(/código/i), over.code ?? '418302');
  await user.type(screen.getByLabelText(/nueva contraseña/i), over.pass ?? 'BrandNew123');
  await user.type(screen.getByLabelText(/repite la contraseña/i), over.confirm ?? 'BrandNew123');
}

describe('ResetPasswordForm', () => {
  it('submits email, code and password', async () => {
    resetPassword.mockResolvedValue(undefined);
    const user = userEvent.setup();
    render(<ResetPasswordForm locale="es" />);
    await fill(user);
    await user.click(screen.getByRole('button', { name: /guardar contraseña/i }));
    await waitFor(() =>
      expect(resetPassword).toHaveBeenCalledWith('camila@example.com', '418302', 'BrandNew123'),
    );
    expect(await screen.findByText(/contraseña actualizada/i)).toBeInTheDocument();
  });

  it('blocks submit when the code is not 6 digits', async () => {
    const user = userEvent.setup();
    render(<ResetPasswordForm locale="es" />);
    await fill(user, { code: '123' });
    await user.click(screen.getByRole('button', { name: /guardar contraseña/i }));
    expect(await screen.findByText(/el código tiene 6 dígitos/i)).toBeInTheDocument();
    expect(resetPassword).not.toHaveBeenCalled();
  });

  it('shows a generic inline error on a 400', async () => {
    resetPassword.mockRejectedValue(new ApiError('x', 400));
    const user = userEvent.setup();
    render(<ResetPasswordForm locale="es" />);
    await fill(user);
    await user.click(screen.getByRole('button', { name: /guardar contraseña/i }));
    expect(await screen.findByText(/no es válido o ya expiró/i)).toBeInTheDocument();
  });

  it('rejects mismatched passwords before calling the API', async () => {
    const user = userEvent.setup();
    render(<ResetPasswordForm locale="es" />);
    await fill(user, { confirm: 'Different123' });
    await user.click(screen.getByRole('button', { name: /guardar contraseña/i }));
    expect(await screen.findByText(/no coinciden/i)).toBeInTheDocument();
    expect(resetPassword).not.toHaveBeenCalled();
  });
});
