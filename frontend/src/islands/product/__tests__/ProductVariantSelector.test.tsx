import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';
import ProductVariantSelector from '../ProductVariantSelector';
import type { VariantFieldConfigDto } from '../../../lib/api';

/**
 * Characterization tests locking in the behavior the config-driven rewrite (Task 14) must
 * preserve: resolved field labels rendering, and selecting a variant enabling Add to cart.
 */
const CLOTHING_CONFIG: VariantFieldConfigDto = {
  primary: { label: 'Color', inputType: 'FREE_TEXT', options: [], min: null, max: null, allowMultiple: false, allowCustom: true },
  secondary: {
    label: 'Talla', inputType: 'OPTIONS',
    options: ['XS', 'S', 'M', 'L', 'XL', 'XXL', 'XXXL', 'UNICO'],
    min: null, max: null, allowMultiple: true, allowCustom: true,
  },
};

const BASE_PROPS = {
  productId: 'p1',
  name: 'Vestido',
  brand: 'Pilar Estilo',
  price: { amount: 20000, currency: 'CLP' },
  imageUrl: 'https://img',
  condition: 'NEW' as const,
  stock: 5,
  locale: 'es' as const,
};

describe('ProductVariantSelector: config-driven schema', () => {
  it('renders primary/secondary labels for a CLOTHING-equivalent product and enables Add to cart after selecting a variant', async () => {
    const user = userEvent.setup();
    render(
      <ProductVariantSelector
        {...BASE_PROPS}
        variantFieldConfig={CLOTHING_CONFIG}
        variants={[
          { color: 'Negro', size: 'M', stock: 2, stockOnHand: 2, stockReserved: 0, stockAvailable: 2 },
          { color: 'Rojo', size: 'S', stock: 3, stockOnHand: 3, stockReserved: 0, stockAvailable: 3 },
        ]}
      />,
    );

    expect(screen.getByText('Color')).toBeInTheDocument();
    expect(screen.getByText('Talla')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Rojo' }));
    await user.click(screen.getByRole('button', { name: 'S' }));

    expect(screen.getByRole('button', { name: /Agregar al Carrito/i })).not.toBeDisabled();
  });
});
