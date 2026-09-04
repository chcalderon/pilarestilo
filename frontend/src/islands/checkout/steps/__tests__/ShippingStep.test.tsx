import { useState } from 'react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';
import ShippingStep, { zoneForComuna } from '../ShippingStep';
import type { CourierConfig, CustomerAddressDto, ShippingZoneConfig } from '../../../../lib/api';

/**
 * The zone used to be a dropdown the customer filled in herself, with nothing tying it to where
 * she actually lived -- LOCAL only had 4 of the 10 real Aconcagua comunas and REGIONAL shipped
 * empty since V42, so no auto-detection could have worked even if it existed. V97 repairs the
 * data and the zone is no longer shown to the customer at all: it is derived silently from
 * whichever address she picks. These tests cover the matching logic and that silent wiring.
 */

const getMyAddresses = vi.fn();
const getLocationTree = vi.fn();

vi.mock('../../../../lib/api', async () => {
  const actual = await vi.importActual<typeof import('../../../../lib/api')>('../../../../lib/api');
  return {
    ...actual,
    getMyAddresses: (...args: unknown[]) => getMyAddresses(...args),
    getLocationTree: (...args: unknown[]) => getLocationTree(...args),
    createMyAddress: vi.fn(),
    updateMyAddress: vi.fn(),
    setMyAddressAsDefault: vi.fn(),
  };
});

const ZONES: ShippingZoneConfig[] = [
  {
    code: 'LOCAL', titleEs: 'Zona local', titleEn: 'Local zone', etaEs: '24-48 hs', etaEn: '24-48h',
    comunas: ['Los Andes', 'San Esteban', 'Calle Larga', 'Rinconada', 'San Felipe', 'Putaendo', 'Santa María', 'Panquehue', 'Llay-Llay', 'Catemu'],
    active: true, sortOrder: 1,
  },
  {
    code: 'REGIONAL', titleEs: 'Región de Valparaíso', titleEn: 'Valparaíso Region', etaEs: '2-4 dias habiles', etaEn: '2-4 business days',
    comunas: ['Valparaíso', 'Viña del Mar', 'Quilpué', 'Villa Alemana', 'Quillota', 'San Antonio'],
    active: true, sortOrder: 2,
  },
  {
    code: 'NACIONAL', titleEs: 'Otras regiones', titleEn: 'Other Chilean regions', etaEs: '3-7 dias habiles', etaEn: '3-7 business days',
    comunas: [], active: true, sortOrder: 3,
  },
];

const COURIERS: CourierConfig[] = [{ id: 'starken', name: 'Starken', logoUrl: null, active: true }];

describe('zoneForComuna', () => {
  it('matches a comuna to the zone that lists it', () => {
    expect(zoneForComuna(ZONES, 'Los Andes')).toBe('LOCAL');
    expect(zoneForComuna(ZONES, 'Viña del Mar')).toBe('REGIONAL');
  });

  it('is accent- and case-insensitive, since address data is free-typed', () => {
    expect(zoneForComuna(ZONES, 'vina del mar')).toBe('REGIONAL');
    expect(zoneForComuna(ZONES, 'SAN ESTEBAN')).toBe('LOCAL');
  });

  it('falls back to NACIONAL for a comuna neither LOCAL nor REGIONAL lists', () => {
    expect(zoneForComuna(ZONES, 'Arica')).toBe('NACIONAL');
    expect(zoneForComuna(ZONES, 'Providencia')).toBe('NACIONAL');
  });

  it('returns null for no comuna at all', () => {
    expect(zoneForComuna(ZONES, null)).toBeNull();
    expect(zoneForComuna(ZONES, undefined)).toBeNull();
    expect(zoneForComuna(ZONES, '')).toBeNull();
  });
});

function address(overrides: Partial<CustomerAddressDto> = {}): CustomerAddressDto {
  return {
    id: 'addr-1',
    customerId: 'cust-1',
    label: 'Casa',
    recipientName: 'Ana Perez',
    phone: '+56911111111',
    line1: 'Av. Siempre Viva 123',
    comuna: 'Los Andes',
    city: 'Los Andes',
    region: 'Valparaíso',
    isDefault: true,
    ...overrides,
  } as CustomerAddressDto;
}

/**
 * Mirrors how CheckoutPage actually holds this step's props, so the effects under test settle
 * the same way they do in the real app. zoneCode is never rendered by ShippingStep itself
 * anymore (it is not customer-facing) -- the debug node below is test-only, so the derived
 * value stays observable without adding test hooks to production markup.
 */
function Harness({ initialZoneCode = '' }: { readonly initialZoneCode?: string }) {
  const [zoneCode, setZoneCode] = useState(initialZoneCode);
  const [courierId, setCourierId] = useState('');
  const [addressId, setAddressId] = useState('');

  return (
    <>
      <div data-testid="zone-debug">{zoneCode}</div>
      <ShippingStep
        locale="es"
        token="tok"
        zones={ZONES}
        couriers={COURIERS}
        zoneCode={zoneCode}
        courierId={courierId}
        addressId={addressId}
        onChange={(value) => {
          if (value.zoneCode !== undefined) setZoneCode(value.zoneCode);
          if (value.courierId !== undefined) setCourierId(value.courierId);
          if (value.addressId !== undefined) setAddressId(value.addressId);
        }}
        onContinue={() => {}}
      />
    </>
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  getLocationTree.mockResolvedValue([]);
});

describe('ShippingStep: zone follows the selected address, invisibly', () => {
  it('never renders a zone selector -- the customer only ever chooses the courier', async () => {
    getMyAddresses.mockResolvedValue([address({ comuna: 'Los Andes' })]);
    render(<Harness />);

    await waitFor(() => {
      expect(screen.getByTestId('zone-debug')).toHaveTextContent('LOCAL');
    });
    expect(screen.queryByLabelText(/zona de envío/i)).not.toBeInTheDocument();
    expect(screen.getByLabelText(/courier/i)).toBeInTheDocument();
  });

  it('derives LOCAL for a default address in an Aconcagua comuna', async () => {
    getMyAddresses.mockResolvedValue([address({ comuna: 'Los Andes' })]);
    render(<Harness />);

    await waitFor(() => {
      expect(screen.getByTestId('zone-debug')).toHaveTextContent('LOCAL');
    });
  });

  it('derives REGIONAL for a default address elsewhere in the Valparaíso region', async () => {
    getMyAddresses.mockResolvedValue([address({ comuna: 'Viña del Mar' })]);
    render(<Harness />);

    await waitFor(() => {
      expect(screen.getByTestId('zone-debug')).toHaveTextContent('REGIONAL');
    });
  });

  it('falls back to NACIONAL for a comuna outside both explicit zones', async () => {
    getMyAddresses.mockResolvedValue([address({ comuna: 'Arica' })]);
    render(<Harness />);

    await waitFor(() => {
      expect(screen.getByTestId('zone-debug')).toHaveTextContent('NACIONAL');
    });
  });

  it('re-derives when the customer switches to a different saved address', async () => {
    getMyAddresses.mockResolvedValue([
      address({ id: 'addr-1', comuna: 'Los Andes', isDefault: true }),
      address({ id: 'addr-2', comuna: 'Arica', isDefault: false, label: 'Trabajo' }),
    ]);
    render(<Harness />);

    await waitFor(() => {
      expect(screen.getByTestId('zone-debug')).toHaveTextContent('LOCAL');
    });

    const user = userEvent.setup();
    await user.click(screen.getByRole('radio', { name: /trabajo/i }));

    await waitFor(() => {
      expect(screen.getByTestId('zone-debug')).toHaveTextContent('NACIONAL');
    });
  });
});
