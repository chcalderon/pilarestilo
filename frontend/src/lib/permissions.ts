import { useAuthStore, type StoredUser } from './authStore';

export type LegacyPermissionKey = 'roles_permisos' | 'usuarios' | 'configuracion';
export type AdminModuleKey = 'roles' | 'users' | 'settings';

export interface PermissionDescriptor {
  permissionCode: string;
  legacyViewKey?: LegacyPermissionKey;
}

const MODULE_REQUIREMENTS: Record<AdminModuleKey, PermissionDescriptor> = {
  roles: { permissionCode: 'roles.read', legacyViewKey: 'roles_permisos' },
  users: { permissionCode: 'users.read', legacyViewKey: 'usuarios' },
  settings: { permissionCode: 'settings.read', legacyViewKey: 'configuracion' },
};

function hasLegacyAdminBypass(user: StoredUser | null | undefined): boolean {
  return user?.role === 'ADMIN';
}

export function hasPermission(
  user: StoredUser | null | undefined,
  permissionCode: string,
  legacyViewKey?: LegacyPermissionKey,
): boolean {
  if (!user) {
    return false;
  }
  if (hasLegacyAdminBypass(user)) {
    return true;
  }
  if ((user.permissionCodes ?? []).includes(permissionCode)) {
    return true;
  }
  return legacyViewKey ? (user.permissions ?? []).includes(legacyViewKey) : false;
}

export function hasAnyPermission(
  user: StoredUser | null | undefined,
  descriptors: PermissionDescriptor[],
): boolean {
  return descriptors.some((descriptor) =>
    hasPermission(user, descriptor.permissionCode, descriptor.legacyViewKey),
  );
}

export function hasModuleAccess(
  user: StoredUser | null | undefined,
  module: AdminModuleKey,
): boolean {
  return hasPermission(
    user,
    MODULE_REQUIREMENTS[module].permissionCode,
    MODULE_REQUIREMENTS[module].legacyViewKey,
  );
}

export function useCan(permissionCode: string, legacyViewKey?: LegacyPermissionKey): boolean {
  const user = useAuthStore((state) => state.user);
  return hasPermission(user, permissionCode, legacyViewKey);
}

export function useCanAny(descriptors: PermissionDescriptor[]): boolean {
  const user = useAuthStore((state) => state.user);
  return hasAnyPermission(user, descriptors);
}

export function useModuleAccess(module: AdminModuleKey): boolean {
  const user = useAuthStore((state) => state.user);
  return hasModuleAccess(user, module);
}
