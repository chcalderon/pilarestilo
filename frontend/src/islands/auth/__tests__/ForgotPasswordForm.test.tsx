import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';
import ForgotPasswordForm from '../ForgotPasswordForm';
import { ApiError } from '@/lib/api';

const requestPasswordReset = vi.fn();

vi.mock('@/lib/api', async () => {
  const actual = await vi.importActual<typeof import('@/lib/api')>('@/lib/api');
  return {
    ...actual,
    requestPasswordReset: (...args: unknown[]) => requestPasswordReset(...args),
  };
});

beforeEach(() => {
  vi.clearAllMocks();
});

describe('ForgotPasswordForm', () => {
  it('shows the same generic confirmation after a successful request', async () => {
    requestPasswordReset.mockResolvedValue({ message: 'ok' });
    const user = userEvent.setup();
    render(<ForgotPasswordForm locale="es" />);

    await user.type(screen.getByPlaceholderText(/email.com/i), 'ana@correo.cl');
    await user.click(screen.getByRole('button', { name: /enviar código/i }));

    expect(await screen.findByText(/revisa tu correo/i)).toBeInTheDocument();
    expect(requestPasswordReset).toHaveBeenCalledWith('ana@correo.cl');
  });

  it('shows the same confirmation screen even though the address may not exist', async () => {
    requestPasswordReset.mockResolvedValue({ message: 'ok' });
    const user = userEvent.setup();
    render(<ForgotPasswordForm locale="es" />);

    await user.type(screen.getByPlaceholderText(/email.com/i), 'ghost@nowhere.invalid');
    await user.click(screen.getByRole('button', { name: /enviar código/i }));

    expect(await screen.findByText(/revisa tu correo/i)).toBeInTheDocument();
  });

  it('surfaces a throttling message on 429 without the confirmation screen', async () => {
    requestPasswordReset.mockRejectedValue(new ApiError('Too many requests', 429));
    const user = userEvent.setup();
    render(<ForgotPasswordForm locale="es" />);

    await user.type(screen.getByPlaceholderText(/email.com/i), 'ana@correo.cl');
    await user.click(screen.getByRole('button', { name: /enviar código/i }));

    expect(await screen.findByText(/demasiados intentos/i)).toBeInTheDocument();
    expect(screen.queryByText(/revisa tu correo/i)).not.toBeInTheDocument();
  });
});
