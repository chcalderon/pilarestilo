import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';
import NavNotificationBell from '../NavNotificationBell';
import type { InAppNotificationDto, UserProfileDto } from '@/lib/api';

/**
 * Characterization tests written before pulling the theme-color computation (13 dark/light
 * ternaries) and the dropdown markup out of this component (S3776, complexity 23) -- it had none.
 * Covers the logged-out no-render case, the unread badge, opening the dropdown, marking a
 * notification read on click (and navigating to its link), and the config-nudge alerts for a
 * profile missing a notification channel or phone.
 */

const getMyProfile = vi.fn();
const getRecentNotifications = vi.fn();
const getUnreadNotificationsCount = vi.fn();
const markNotificationRead = vi.fn();

vi.mock('@/lib/api', async () => {
  const actual = await vi.importActual<typeof import('@/lib/api')>('@/lib/api');
  return {
    ...actual,
    getMyProfile: (...args: unknown[]) => getMyProfile(...args),
    getRecentNotifications: (...args: unknown[]) => getRecentNotifications(...args),
    getUnreadNotificationsCount: (...args: unknown[]) => getUnreadNotificationsCount(...args),
    markNotificationRead: (...args: unknown[]) => markNotificationRead(...args),
  };
});

let authState: { user: { id: string } | null; token: string | null } = { user: null, token: null };

vi.mock('@/lib/authStore', () => ({
  useAuthStore: () => authState,
  readAuthTokenCookie: () => null,
}));

function profile(overrides: Partial<UserProfileDto> = {}): UserProfileDto {
  return {
    id: 'user-1',
    fullName: 'Ana Perez',
    email: 'ana@correo.cl',
    role: 'CUSTOMER',
    notificationChannelPreference: 'EMAIL',
    phone: '+56911111111',
    ...overrides,
  } as UserProfileDto;
}

function notification(overrides: Partial<InAppNotificationDto> = {}): InAppNotificationDto {
  return {
    id: 'notif-1',
    type: 'ORDER_CONFIRMED',
    title: 'Tu pedido fue confirmado',
    body: 'El pedido #123 está en preparación.',
    metadata: null,
    read: false,
    createdAt: new Date().toISOString(),
    ...overrides,
  };
}

beforeEach(() => {
  vi.clearAllMocks();
  authState = { user: { id: 'user-1' }, token: 'tok' };
  getMyProfile.mockResolvedValue(profile());
  getRecentNotifications.mockResolvedValue({ content: [] });
  getUnreadNotificationsCount.mockResolvedValue({ count: 0 });
  markNotificationRead.mockResolvedValue(undefined);
  vi.stubGlobal('location', { ...window.location, href: '' });
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('NavNotificationBell', () => {
  it('renders nothing when logged out', () => {
    authState = { user: null, token: null };
    const { container } = render(<NavNotificationBell locale="es" />);
    expect(container).toBeEmptyDOMElement();
  });

  it('shows the unread count badge once the count loads', async () => {
    getUnreadNotificationsCount.mockResolvedValue({ count: 3 });
    render(<NavNotificationBell locale="es" />);

    expect(await screen.findByText('3')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /3 notificaciones sin leer/i })).toBeInTheDocument();
  });

  it('caps the badge at 9+', async () => {
    getUnreadNotificationsCount.mockResolvedValue({ count: 42 });
    render(<NavNotificationBell locale="es" />);

    expect(await screen.findByText('9+')).toBeInTheDocument();
  });

  it('opens the dropdown and lists unread notifications', async () => {
    getUnreadNotificationsCount.mockResolvedValue({ count: 1 });
    getRecentNotifications.mockResolvedValue({ content: [notification({ read: false })] });
    const user = userEvent.setup();
    render(<NavNotificationBell locale="es" />);

    await waitFor(() => expect(getRecentNotifications).toHaveBeenCalled());
    await user.click(screen.getByRole('button', { name: /notificaci/i }));

    expect(await screen.findByText('Tu pedido fue confirmado')).toBeInTheDocument();
  });

  it('marks a notification read and navigates to its link on click', async () => {
    getUnreadNotificationsCount.mockResolvedValue({ count: 1 });
    getRecentNotifications.mockResolvedValue({
      content: [notification({ id: 'n1', read: false, metadata: { link: '/es/account?tab=orders' } })],
    });
    const user = userEvent.setup();
    render(<NavNotificationBell locale="es" />);

    await user.click(screen.getByRole('button', { name: /notificaci/i }));
    await user.click(await screen.findByText('Tu pedido fue confirmado'));

    await waitFor(() => expect(markNotificationRead).toHaveBeenCalledWith('n1', 'tok'));
    expect(window.location.href).toBe('/es/account?tab=orders');
  });

  it('shows a config-nudge alert when the profile has no notification channel set', async () => {
    getMyProfile.mockResolvedValue(profile({ notificationChannelPreference: 'AUTO' }));
    const user = userEvent.setup();
    render(<NavNotificationBell locale="es" />);

    await waitFor(() => expect(getMyProfile).toHaveBeenCalled());
    await user.click(screen.getByRole('button', { name: /notificaci/i }));

    expect(await screen.findByText(/configura tu canal de notificaciones/i)).toBeInTheDocument();
  });

  it('shows a config-nudge alert when the profile has no phone', async () => {
    getMyProfile.mockResolvedValue(profile({ phone: undefined }));
    const user = userEvent.setup();
    render(<NavNotificationBell locale="es" />);

    await waitFor(() => expect(getMyProfile).toHaveBeenCalled());
    await user.click(screen.getByRole('button', { name: /notificaci/i }));

    expect(await screen.findByText(/agrega tu número de teléfono/i)).toBeInTheDocument();
  });
});
