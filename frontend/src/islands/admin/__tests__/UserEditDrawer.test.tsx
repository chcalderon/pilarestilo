import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';
import UserEditDrawer from '../UserEditDrawer';
import type { AdminUserDto } from '../../../lib/api';

/**
 * Characterization tests written before reducing this component's Cognitive Complexity (S3776,
 * complexity 20) -- it had none. Six largely-independent SectionCard blocks (basics, status,
 * password, credit, worker role, danger zone) each gated by canUpdate/isLegacyAdmin/isSelf/isCustomer,
 * so the refactor (splitting each SectionCard's body into its own component) is verified against the
 * actual save payloads, not just "it renders". assignRole/revokeRole call raw `fetch`, not the api.ts
 * module, so those two need a global fetch mock rather than a vi.mock of '../../../lib/api'.
 */

const updateAdminUser = vi.fn();
const deleteAdminUser = vi.fn();
const resetAdminUserPassword = vi.fn();
const getCustomerCredit = vi.fn();
const grantCustomerCredit = vi.fn();

vi.mock('../../../lib/api', async () => {
  const actual = await vi.importActual<typeof import('../../../lib/api')>('../../../lib/api');
  return {
    ...actual,
    updateAdminUser: (...args: unknown[]) => updateAdminUser(...args),
    deleteAdminUser: (...args: unknown[]) => deleteAdminUser(...args),
    resetAdminUserPassword: (...args: unknown[]) => resetAdminUserPassword(...args),
    getCustomerCredit: (...args: unknown[]) => getCustomerCredit(...args),
    grantCustomerCredit: (...args: unknown[]) => grantCustomerCredit(...args),
  };
});

const TOKEN = 'test-token';

function adminUser(overrides: Partial<AdminUserDto> = {}): AdminUserDto {
  return {
    id: 'user-1',
    email: 'ana@pilarestilo.com',
    fullName: 'Ana Perez',
    role: 'SELLER',
    active: true,
    createdAt: '2026-01-01T00:00:00Z',
    ...overrides,
  };
}

function renderDrawer(overrides: {
  user?: Partial<AdminUserDto>;
  currentUserId?: string;
  canUpdate?: boolean;
  isLegacyAdmin?: boolean;
} = {}) {
  const onClose = vi.fn();
  const onSaved = vi.fn();
  render(
    <UserEditDrawer
      user={adminUser(overrides.user)}
      token={TOKEN}
      currentUserId={overrides.currentUserId ?? 'admin-1'}
      canUpdate={overrides.canUpdate ?? true}
      isLegacyAdmin={overrides.isLegacyAdmin ?? true}
      onClose={onClose}
      onSaved={onSaved}
    />,
  );
  return { onClose, onSaved };
}

beforeEach(() => {
  vi.clearAllMocks();
  getCustomerCredit.mockResolvedValue(null);
});

describe('UserEditDrawer: basics', () => {
  it('shows the user header details', () => {
    renderDrawer();

    expect(screen.getByText('Ana Perez')).toBeInTheDocument();
    expect(screen.getByText('ana@pilarestilo.com')).toBeInTheDocument();
    expect(screen.getByText('Activo')).toBeInTheDocument();
  });

  it('saves a trimmed full name and flashes the ok badge', async () => {
    updateAdminUser.mockResolvedValue(adminUser({ fullName: 'Ana Maria Perez' }));
    const user = userEvent.setup();
    const { onSaved } = renderDrawer();

    const nameInput = screen.getByPlaceholderText('Nombre del usuario');
    await user.clear(nameInput);
    await user.type(nameInput, '  Ana Maria Perez  ');
    await user.click(screen.getByRole('button', { name: /guardar nombre/i }));

    await waitFor(() => expect(updateAdminUser).toHaveBeenCalledWith('user-1', { fullName: 'Ana Maria Perez' }, TOKEN));
    expect(onSaved).toHaveBeenCalled();
    expect(await screen.findByText('Guardado')).toBeInTheDocument();
  });

  it('disables the save-name button when the name is unchanged', () => {
    renderDrawer();

    expect(screen.getByRole('button', { name: /guardar nombre/i })).toBeDisabled();
  });

  it('shows the own-account guard and disables editing when viewing yourself', () => {
    renderDrawer({ currentUserId: 'user-1' });

    expect(screen.getByText(/no puedes modificar tu propia cuenta/i)).toBeInTheDocument();
    expect(screen.getByPlaceholderText('Nombre del usuario')).toBeDisabled();
  });

  it('shows the read-only-editor notice for a non-legacy admin with update rights', () => {
    renderDrawer({ isLegacyAdmin: false, canUpdate: true });

    expect(screen.getByText(/acciones sensibles como contrasena/i)).toBeInTheDocument();
  });
});

describe('UserEditDrawer: status', () => {
  it('toggles the account status', async () => {
    updateAdminUser.mockResolvedValue(adminUser({ active: false }));
    const user = userEvent.setup();
    const { onSaved } = renderDrawer();

    await user.click(screen.getByRole('button', { name: /bloquear usuario/i }));

    await waitFor(() => expect(updateAdminUser).toHaveBeenCalledWith('user-1', { active: false }, TOKEN));
    expect(onSaved).toHaveBeenCalled();
    expect(screen.getByText('Bloqueado')).toBeInTheDocument();
  });

  it('shows an error message when the status update fails', async () => {
    updateAdminUser.mockRejectedValue(new Error('boom'));
    const user = userEvent.setup();
    renderDrawer();

    await user.click(screen.getByRole('button', { name: /bloquear usuario/i }));

    expect(await screen.findByText(/no se pudo cambiar el estado/i)).toBeInTheDocument();
  });
});

describe('UserEditDrawer: password (legacy admin only)', () => {
  it('is hidden for a non-legacy admin', () => {
    renderDrawer({ isLegacyAdmin: false });

    expect(screen.queryByText('Restablecer contraseña')).not.toBeInTheDocument();
  });

  it('requires at least 8 characters', async () => {
    const user = userEvent.setup();
    renderDrawer();

    await user.click(screen.getByRole('button', { name: /restablecer contraseña/i }));
    await user.type(screen.getByPlaceholderText(/m[ií]nimo 8 caracteres/i), 'short');
    await user.click(screen.getByRole('button', { name: /actualizar contraseña/i }));

    expect(await screen.findByText(/m[ií]nimo 8 caracteres/i)).toBeInTheDocument();
    expect(resetAdminUserPassword).not.toHaveBeenCalled();
  });

  it('requires the confirmation to match', async () => {
    const user = userEvent.setup();
    renderDrawer();

    await user.click(screen.getByRole('button', { name: /restablecer contraseña/i }));
    await user.type(screen.getByPlaceholderText(/m[ií]nimo 8 caracteres/i), 'password123');
    await user.type(screen.getByPlaceholderText(/repite la contraseña/i), 'password124');
    await user.click(screen.getByRole('button', { name: /actualizar contraseña/i }));

    expect(await screen.findByText(/no coinciden/i)).toBeInTheDocument();
    expect(resetAdminUserPassword).not.toHaveBeenCalled();
  });

  it('resets the password and clears the fields', async () => {
    resetAdminUserPassword.mockResolvedValue(undefined);
    const user = userEvent.setup();
    renderDrawer();

    await user.click(screen.getByRole('button', { name: /restablecer contraseña/i }));
    await user.type(screen.getByPlaceholderText(/m[ií]nimo 8 caracteres/i), 'password123');
    await user.type(screen.getByPlaceholderText(/repite la contraseña/i), 'password123');
    await user.click(screen.getByRole('button', { name: /actualizar contraseña/i }));

    await waitFor(() => expect(resetAdminUserPassword).toHaveBeenCalledWith('user-1', 'password123', TOKEN));
  });
});

describe('UserEditDrawer: credit (legacy admin only)', () => {
  it('is hidden for a non-legacy admin', () => {
    renderDrawer({ isLegacyAdmin: false });

    expect(screen.queryByText('Crédito')).not.toBeInTheDocument();
  });

  it('loads and shows the current balance', async () => {
    getCustomerCredit.mockResolvedValue({ id: 'c1', customerId: 'user-1', balanceAmount: 15000, balanceCurrency: 'CLP' });
    renderDrawer();

    expect(await screen.findByText('$15.000')).toBeInTheDocument();
  });

  it('rejects an invalid amount', async () => {
    const user = userEvent.setup();
    renderDrawer();

    const amountInput = screen.getByPlaceholderText('50000');
    await user.clear(amountInput);
    await user.click(screen.getByRole('button', { name: /otorgar cr[eé]dito/i }));

    expect(await screen.findByText(/monto inv[aá]lido/i)).toBeInTheDocument();
    expect(grantCustomerCredit).not.toHaveBeenCalled();
  });

  it('grants credit with the entered amount and reason', async () => {
    grantCustomerCredit.mockResolvedValue({ id: 'c1', customerId: 'user-1', balanceAmount: 50000, balanceCurrency: 'CLP' });
    const user = userEvent.setup();
    const { onSaved } = renderDrawer();

    await user.click(screen.getByRole('button', { name: /otorgar cr[eé]dito/i }));

    await waitFor(() => expect(grantCustomerCredit).toHaveBeenCalledWith('user-1', 50000, 'Crédito administrativo', TOKEN));
    expect(onSaved).toHaveBeenCalled();
  });
});

describe('UserEditDrawer: worker role (legacy admin, non-customer only)', () => {
  it('is hidden for a customer', () => {
    renderDrawer({ user: { role: 'CUSTOMER' } });

    expect(screen.queryByText('Rol laboral')).not.toBeInTheDocument();
  });

  it('is hidden for a non-legacy admin', () => {
    renderDrawer({ isLegacyAdmin: false });

    expect(screen.queryByText('Rol laboral')).not.toBeInTheDocument();
  });

  it('requires a vigency start date before assigning', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    renderDrawer();

    await user.click(screen.getByRole('button', { name: /^asignar rol$/i }));

    expect(await screen.findByText(/fecha de inicio requerida/i)).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
    vi.unstubAllGlobals();
  });

  it('assigns the selected role with its vigency window', async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true });
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    const { onSaved } = renderDrawer();

    await user.selectOptions(screen.getByRole('combobox'), 'ADMINISTRACION');
    const dateInputs = document.querySelectorAll('input[type="date"]');
    await user.type(dateInputs[0] as HTMLInputElement, '2026-09-01');
    await user.click(screen.getByRole('button', { name: /^asignar rol$/i }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(
      '/api/admin/workers/user-1/assign',
      expect.objectContaining({ method: 'POST' }),
    ));
    const [, options] = fetchMock.mock.calls[0];
    expect(JSON.parse(options.body)).toMatchObject({ role: 'ADMINISTRACION', vigencyStart: '2026-09-01', vigencyEnd: null });
    expect(onSaved).toHaveBeenCalled();
    vi.unstubAllGlobals();
  });

  it('revokes the worker role', async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true });
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    renderDrawer();

    await user.click(screen.getByRole('button', { name: /revocar/i }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(
      '/api/admin/workers/user-1/revoke',
      expect.objectContaining({ method: 'DELETE' }),
    ));
    vi.unstubAllGlobals();
  });
});

describe('UserEditDrawer: danger zone (legacy admin only)', () => {
  it('is hidden for a non-legacy admin', () => {
    renderDrawer({ isLegacyAdmin: false });

    expect(screen.queryByText('Zona de riesgo')).not.toBeInTheDocument();
  });

  it('offers to promote a customer to worker', () => {
    renderDrawer({ user: { role: 'CUSTOMER' } });

    expect(screen.getByRole('button', { name: /pasar a trabajador/i })).toBeInTheDocument();
  });

  it('offers to demote a worker to customer', () => {
    renderDrawer({ user: { role: 'SELLER' } });

    expect(screen.getByRole('button', { name: /pasar a cliente/i })).toBeInTheDocument();
  });

  it('changes the user role and closes the drawer', async () => {
    updateAdminUser.mockResolvedValue(adminUser({ role: 'CUSTOMER' }));
    const user = userEvent.setup();
    const { onSaved, onClose } = renderDrawer({ user: { role: 'SELLER' } });

    await user.click(screen.getByRole('button', { name: /pasar a cliente/i }));

    await waitFor(() => expect(updateAdminUser).toHaveBeenCalledWith('user-1', { role: 'CUSTOMER' }, TOKEN));
    expect(onSaved).toHaveBeenCalled();
    expect(onClose).toHaveBeenCalled();
  });

  it('asks for confirmation before deleting, then deletes', async () => {
    deleteAdminUser.mockResolvedValue(undefined);
    const user = userEvent.setup();
    const { onSaved, onClose } = renderDrawer();

    await user.click(screen.getByRole('button', { name: /eliminar usuario/i }));
    expect(screen.getByText(/confirmar eliminaci[oó]n/i)).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /s[ií], eliminar/i }));

    await waitFor(() => expect(deleteAdminUser).toHaveBeenCalledWith('user-1', TOKEN));
    expect(onSaved).toHaveBeenCalled();
    expect(onClose).toHaveBeenCalled();
  });

  it('cancels the delete confirmation', async () => {
    const user = userEvent.setup();
    renderDrawer();

    await user.click(screen.getByRole('button', { name: /eliminar usuario/i }));
    await user.click(screen.getByRole('button', { name: /cancelar/i }));

    expect(screen.queryByText(/confirmar eliminaci[oó]n/i)).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /eliminar usuario/i })).toBeInTheDocument();
  });

  it('shows an error and re-offers the delete button when deletion fails', async () => {
    deleteAdminUser.mockRejectedValue(new Error('boom'));
    const user = userEvent.setup();
    renderDrawer();

    await user.click(screen.getByRole('button', { name: /eliminar usuario/i }));
    await user.click(screen.getByRole('button', { name: /s[ií], eliminar/i }));

    expect(await screen.findByText(/no se pudo eliminar/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /eliminar usuario/i })).toBeInTheDocument();
  });
});
