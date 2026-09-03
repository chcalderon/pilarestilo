import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';
import AdminTopbarActions from '../AdminTopbarActions';

const clearAuth = vi.fn();

vi.mock('../../../lib/authStore', () => ({
  useAuthStore: () => ({ clearAuth }),
}));

beforeEach(() => {
  vi.clearAllMocks();
  vi.stubGlobal('location', { ...window.location, href: '' });
});

describe('AdminTopbarActions', () => {
  it('links to the storefront in place without touching the session', () => {
    render(<AdminTopbarActions />);
    const link = screen.getByRole('link', { name: /ver tienda/i });
    expect(link).toHaveAttribute('href', '/');
    expect(link).not.toHaveAttribute('target');
    expect(clearAuth).not.toHaveBeenCalled();
  });

  it('clears the session and redirects to the admin login on sign out', async () => {
    render(<AdminTopbarActions />);
    await userEvent.click(screen.getByRole('button', { name: /cerrar sesión/i }));
    expect(clearAuth).toHaveBeenCalledTimes(1);
    expect(window.location.href).toBe('/admin/login');
  });
});
