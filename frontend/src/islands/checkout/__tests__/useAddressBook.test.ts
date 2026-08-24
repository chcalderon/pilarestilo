import { describe, expect, it, vi, beforeEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import {
  emptyAddressDraft,
  validateDraft,
  useAddressBook,
  type AddressDraft,
} from '../useAddressBook';
import type { LocationRegionDto } from '@/lib/api';

/**
 * Characterization tests written before restructuring validateDraft's seven independent
 * if/ternary checks into a table-driven loop (S3776, complexity 21) -- it had none. Covers every
 * field's error message in both locales, the all-valid case, and the hook's load/save/make-default
 * round trip against the mocked API.
 */

const getMyAddresses = vi.fn();
const getLocationTree = vi.fn();
const createMyAddress = vi.fn();
const updateMyAddress = vi.fn();
const setMyAddressAsDefault = vi.fn();

vi.mock('@/lib/api', async () => {
  const actual = await vi.importActual<typeof import('@/lib/api')>('@/lib/api');
  return {
    ...actual,
    getMyAddresses: (...args: unknown[]) => getMyAddresses(...args),
    getLocationTree: (...args: unknown[]) => getLocationTree(...args),
    createMyAddress: (...args: unknown[]) => createMyAddress(...args),
    updateMyAddress: (...args: unknown[]) => updateMyAddress(...args),
    setMyAddressAsDefault: (...args: unknown[]) => setMyAddressAsDefault(...args),
  };
});

function validDraft(): AddressDraft {
  return {
    label: 'Casa',
    recipientName: 'Ana Perez',
    phone: '+56911111111',
    line1: 'Av. Siempre Viva 123',
    line2: '',
    regionId: '1',
    cityId: '10',
    comunaId: '100',
    reference: '',
    isDefault: false,
  };
}

function region(): LocationRegionDto {
  return {
    id: 1,
    name: 'Metropolitana',
    cities: [{ id: 10, regionId: 1, name: 'Santiago', communes: [{ id: 100, regionId: 1, cityId: 10, name: 'Providencia' }] }],
  };
}

beforeEach(() => {
  vi.clearAllMocks();
  getMyAddresses.mockResolvedValue([]);
  getLocationTree.mockResolvedValue([region()]);
});

describe('validateDraft', () => {
  it('accepts a fully filled-in draft', () => {
    expect(validateDraft(validDraft(), 'es')).toEqual({});
  });

  it('flags a missing label', () => {
    expect(validateDraft({ ...validDraft(), label: '  ' }, 'es').label).toMatch(/alias/i);
    expect(validateDraft({ ...validDraft(), label: '  ' }, 'en').label).toMatch(/label/i);
  });

  it('flags a missing recipient name', () => {
    expect(validateDraft({ ...validDraft(), recipientName: '' }, 'es').recipientName).toMatch(/quién recibe/i);
  });

  it('flags a phone with too few or too many digits', () => {
    expect(validateDraft({ ...validDraft(), phone: '123' }, 'es').phone).toMatch(/8 y 15/);
    expect(validateDraft({ ...validDraft(), phone: '1'.repeat(20) }, 'es').phone).toMatch(/8 y 15/);
    expect(validateDraft({ ...validDraft(), phone: '12345678' }, 'es').phone).toBeUndefined();
  });

  it('flags a missing street line', () => {
    expect(validateDraft({ ...validDraft(), line1: '' }, 'es').line1).toMatch(/calle y número/i);
  });

  it('flags missing region, city and comuna independently', () => {
    expect(validateDraft({ ...validDraft(), regionId: '' }, 'es').regionId).toMatch(/región/i);
    expect(validateDraft({ ...validDraft(), cityId: '' }, 'es').cityId).toMatch(/ciudad/i);
    expect(validateDraft({ ...validDraft(), comunaId: '' }, 'es').comunaId).toMatch(/comuna/i);
  });
});

describe('useAddressBook', () => {
  it('loads addresses and the location tree on mount', async () => {
    getMyAddresses.mockResolvedValue([{ id: 'a1', label: 'Casa' }]);
    const { result } = renderHook(() => useAddressBook('tok', 'es'));

    await waitFor(() => expect(result.current.addresses).toHaveLength(1));
    await waitFor(() => expect(result.current.regions).toHaveLength(1));
    expect(getMyAddresses).toHaveBeenCalledWith('tok');
  });

  it('clears addresses without a token, and never calls getMyAddresses', async () => {
    const { result } = renderHook(() => useAddressBook(null, 'es'));
    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.addresses).toEqual([]);
    expect(getMyAddresses).not.toHaveBeenCalled();
  });

  it('saves a new address, resolving region/city/comuna by id, and reloads', async () => {
    createMyAddress.mockResolvedValue({ id: 'new-1' });
    const { result } = renderHook(() => useAddressBook('tok', 'es'));
    await waitFor(() => expect(result.current.regions).toHaveLength(1));

    let savedId = '';
    await act(async () => {
      savedId = await result.current.save(validDraft(), null);
    });

    expect(savedId).toBe('new-1');
    expect(createMyAddress).toHaveBeenCalledWith(
      expect.objectContaining({ regionId: 1, cityId: 10, comunaId: 100, comuna: 'Providencia', city: 'Santiago', region: 'Metropolitana' }),
      'tok'
    );
    expect(getMyAddresses).toHaveBeenCalledTimes(2);
  });

  it('updates an existing address when editingId is given', async () => {
    updateMyAddress.mockResolvedValue({ id: 'a1' });
    const { result } = renderHook(() => useAddressBook('tok', 'es'));
    await waitFor(() => expect(result.current.regions).toHaveLength(1));

    let savedId = '';
    await act(async () => {
      savedId = await result.current.save(validDraft(), 'a1');
    });

    expect(savedId).toBe('a1');
    expect(updateMyAddress).toHaveBeenCalledWith('a1', expect.any(Object), 'tok');
  });

  it('rejects a draft whose region/city/comuna do not resolve against the loaded tree', async () => {
    const { result } = renderHook(() => useAddressBook('tok', 'es'));
    await waitFor(() => expect(result.current.regions).toHaveLength(1));

    await expect(result.current.save({ ...validDraft(), regionId: '999' }, null)).rejects.toThrow(/región, ciudad y comuna/i);
    expect(createMyAddress).not.toHaveBeenCalled();
  });

  it('marks an address as default and reloads', async () => {
    const { result } = renderHook(() => useAddressBook('tok', 'es'));
    await waitFor(() => expect(result.current.loading).toBe(false));

    await act(async () => {
      await result.current.makeDefault('a1');
    });

    expect(setMyAddressAsDefault).toHaveBeenCalledWith('a1', 'tok');
    expect(getMyAddresses).toHaveBeenCalledTimes(2);
  });
});
