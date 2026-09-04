import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import '@testing-library/jest-dom/vitest';
import ReviewStep from '../ReviewStep';
import type { CartItem } from '../../../../lib/cartStore';
import type { CustomerAddressDto } from '../../../../lib/api';

/**
 * The shipping zone (LOCAL/REGIONAL/NACIONAL) is an internal routing detail the customer never
 * chose and should never see -- only the ETA it produces ("2-4 dias habiles") is customer-facing,
 * shown here before payment so she knows what she is committing to.
 */

function items(): CartItem[] {
  return [
    {
      id: 'i1', name: 'Vestido rosa', brand: 'Pilar Estilo',
      price: { amount: 20000, currency: 'CLP' }, imageUrl: '', condition: 'NEW', quantity: 1,
    },
  ];
}

function address(): CustomerAddressDto {
  return {
    id: 'addr-1', customerId: 'u1', label: 'Casa', recipientName: 'Ana Perez', phone: '+56911111111',
    line1: 'Av. Siempre Viva 123', comuna: 'Los Andes', city: 'Los Andes', region: 'Valparaíso',
    isDefault: true,
  } as CustomerAddressDto;
}

describe('ReviewStep: the shipping zone stays internal', () => {
  it('shows the courier and the ETA, never a zone name or code', () => {
    render(
      <ReviewStep
        locale="es"
        items={items()}
        address={address()}
        method="TRANSFER"
        courierName="Starken"
        shippingEta="24-48 hs"
        total={20000}
        currency="CLP"
        submitting={false}
        error=""
        stockIssues={{}}
        onRemoveItem={vi.fn()}
        onBack={vi.fn()}
        onFixShipping={vi.fn()}
        onSubmit={vi.fn()}
      />
    );

    expect(screen.getByText('Starken')).toBeInTheDocument();
    expect(screen.getByText('24-48 hs')).toBeInTheDocument();
    expect(screen.queryByText('LOCAL')).not.toBeInTheDocument();
    expect(screen.queryByText('REGIONAL')).not.toBeInTheDocument();
    expect(screen.queryByText(/zona/i)).not.toBeInTheDocument();
  });
});
