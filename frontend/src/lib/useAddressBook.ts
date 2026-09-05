import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  createMyAddress,
  deleteMyAddress,
  getLocationTree,
  getMyAddresses,
  setMyAddressAsDefault,
  updateMyAddress,
  type CustomerAddressDto,
  type LocationCityDto,
  type LocationCommuneDto,
  type LocationRegionDto,
} from './api';
import type { Locale } from '../i18n/index';

export interface AddressDraft {
  label: string;
  recipientName: string;
  phone: string;
  line1: string;
  line2: string;
  regionId: string;
  cityId: string;
  comunaId: string;
  reference: string;
  isDefault: boolean;
}

/** Field name → message. Keyed so a form can render each error under its own input. */
export type AddressErrors = Partial<Record<keyof AddressDraft, string>>;

export function emptyAddressDraft(): AddressDraft {
  return {
    label: '',
    recipientName: '',
    phone: '',
    line1: '',
    line2: '',
    regionId: '',
    cityId: '',
    comunaId: '',
    reference: '',
    isDefault: false,
  };
}

export function draftFromAddress(address: CustomerAddressDto): AddressDraft {
  return {
    label: address.label ?? '',
    recipientName: address.recipientName ?? '',
    phone: address.phone ?? '',
    line1: address.line1 ?? '',
    line2: address.line2 ?? '',
    regionId: address.regionId ? String(address.regionId) : '',
    cityId: address.cityId ? String(address.cityId) : '',
    comunaId: address.communeId ? String(address.communeId) : '',
    reference: address.reference ?? '',
    isDefault: address.isDefault,
  };
}

type FieldCheck = (draft: AddressDraft, es: boolean) => string | undefined;

const FIELD_CHECKS: ReadonlyArray<readonly [keyof AddressDraft, FieldCheck]> = [
  ['label', (d, es) => {
    if (d.label.trim()) return undefined;
    return es ? 'Ingresa un alias, por ejemplo "Casa".' : 'Enter a label, e.g. "Home".';
  }],
  ['recipientName', (d, es) => {
    if (d.recipientName.trim()) return undefined;
    return es ? 'Ingresa quién recibe.' : 'Enter who receives the parcel.';
  }],
  ['phone', (d, es) => {
    const digits = d.phone.replace(/\D/g, '');
    if (digits.length >= 8 && digits.length <= 15) return undefined;
    return es ? 'El teléfono debe tener entre 8 y 15 dígitos.' : 'Phone must have between 8 and 15 digits.';
  }],
  ['line1', (d, es) => {
    if (d.line1.trim()) return undefined;
    return es ? 'Ingresa calle y número.' : 'Enter street and number.';
  }],
  ['regionId', (d, es) => {
    if (d.regionId) return undefined;
    return es ? 'Selecciona una región.' : 'Select a region.';
  }],
  ['cityId', (d, es) => {
    if (d.cityId) return undefined;
    return es ? 'Selecciona una ciudad.' : 'Select a city.';
  }],
  ['comunaId', (d, es) => {
    if (d.comunaId) return undefined;
    return es ? 'Selecciona una comuna.' : 'Select a comuna.';
  }],
];

/**
 * Returns one message per invalid field rather than the first failure. A per-field form shows
 * them all at once under their own inputs; a single-message form (AccountPage) collapses this to
 * `Object.values(errors)[0]` instead of keeping its own parallel copy of these same rules.
 */
export function validateDraft(draft: AddressDraft, locale: Locale): AddressErrors {
  const es = locale === 'es';
  const errors: AddressErrors = {};
  for (const [field, check] of FIELD_CHECKS) {
    const message = check(draft, es);
    if (message) errors[field] = message;
  }
  return errors;
}

export interface UseAddressBookOptions {
  /**
   * Gates both fetches (addresses + region tree). Defaults to true: checkout always needs this
   * data as soon as the step mounts. AccountPage passes `tab === 'addresses'` so it only fetches
   * while that tab is actually open -- matching what it already did before this hook was shared,
   * not a new behavior.
   */
  enabled?: boolean;
}

export function useAddressBook(token: string | null, locale: Locale, options?: UseAddressBookOptions) {
  const enabled = options?.enabled ?? true;
  const [addresses, setAddresses] = useState<CustomerAddressDto[]>([]);
  const [loading, setLoading] = useState(false);
  const [regions, setRegions] = useState<LocationRegionDto[]>([]);
  const [regionsLoading, setRegionsLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const [defaultingId, setDefaultingId] = useState<string | null>(null);

  const reload = useCallback(async () => {
    if (!token || !enabled) {
      setAddresses([]);
      return [] as CustomerAddressDto[];
    }
    setLoading(true);
    try {
      const rows = await getMyAddresses(token);
      setAddresses(rows);
      return rows;
    } catch {
      setAddresses([]);
      return [] as CustomerAddressDto[];
    } finally {
      setLoading(false);
    }
  }, [token, enabled]);

  useEffect(() => {
    void reload();
  }, [reload]);

  useEffect(() => {
    if (!enabled) return;
    let cancelled = false;
    setRegionsLoading(true);
    void (async () => {
      try {
        const tree = await getLocationTree();
        if (!cancelled) setRegions(tree);
      } catch {
        if (!cancelled) setRegions([]);
      } finally {
        if (!cancelled) setRegionsLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [enabled]);

  const save = useCallback(
    async (draft: AddressDraft, editingId: string | null): Promise<string> => {
      if (!token) throw new Error('missing token');

      const region = regions.find((r) => r.id === Number(draft.regionId));
      const city = region?.cities?.find((c) => c.id === Number(draft.cityId));
      const comuna = city?.communes?.find((c) => c.id === Number(draft.comunaId));
      if (!region || !city || !comuna) {
        throw new Error(
          locale === 'es'
            ? 'Selecciona región, ciudad y comuna válidas.'
            : 'Choose a valid region, city and comuna.'
        );
      }

      const payload = {
        label: draft.label.trim(),
        recipientName: draft.recipientName.trim(),
        phone: draft.phone.trim(),
        line1: draft.line1.trim(),
        line2: draft.line2.trim() || undefined,
        regionId: region.id,
        cityId: city.id,
        comunaId: comuna.id,
        comuna: comuna.name,
        city: city.name,
        region: region.name,
        reference: draft.reference.trim() || undefined,
        isDefault: draft.isDefault,
      };

      setSaving(true);
      try {
        const saved = editingId
          ? await updateMyAddress(editingId, payload, token)
          : await createMyAddress(payload, token);
        await reload();
        return editingId ?? saved.id;
      } finally {
        setSaving(false);
      }
    },
    [token, regions, locale, reload]
  );

  const remove = useCallback(
    async (addressId: string) => {
      if (!token) return;
      setDeletingId(addressId);
      try {
        await deleteMyAddress(addressId, token);
        await reload();
      } finally {
        setDeletingId(null);
      }
    },
    [token, reload]
  );

  const makeDefault = useCallback(
    async (addressId: string) => {
      if (!token) return;
      setDefaultingId(addressId);
      try {
        await setMyAddressAsDefault(addressId, token);
        await reload();
      } finally {
        setDefaultingId(null);
      }
    },
    [token, reload]
  );

  return {
    addresses, loading, regions, regionsLoading, saving, deletingId, defaultingId,
    reload, save, remove, makeDefault,
  };
}

/** Cities of the selected region; empty until one is chosen. */
export function useCityOptions(
  regions: LocationRegionDto[],
  regionId: string
): LocationCityDto[] {
  return useMemo(() => {
    if (!regionId) return [];
    return regions.find((r) => r.id === Number(regionId))?.cities ?? [];
  }, [regions, regionId]);
}

export function useComunaOptions(
  cities: LocationCityDto[],
  cityId: string
): LocationCommuneDto[] {
  return useMemo(() => {
    if (!cityId) return [];
    return cities.find((c) => c.id === Number(cityId))?.communes ?? [];
  }, [cities, cityId]);
}
