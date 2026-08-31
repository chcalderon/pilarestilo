import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, cleanup } from '@testing-library/react';
import '@testing-library/jest-dom/vitest';
import TrackEvent from '../TrackEvent';
import { track } from '../../../lib/analytics';

vi.mock('../../../lib/analytics', () => ({
  track: vi.fn(),
}));

beforeEach(() => {
  vi.mocked(track).mockClear();
  cleanup();
});

describe('TrackEvent', () => {
  it('fires its event once on mount and renders nothing', () => {
    const { container } = render(
      <TrackEvent event="product_viewed" properties={{ product_id: 'p1' }} />
    );

    expect(container).toBeEmptyDOMElement();
    expect(track).toHaveBeenCalledTimes(1);
    expect(track).toHaveBeenCalledWith('product_viewed', { product_id: 'p1' });
  });

  it('does not re-fire when the parent re-renders', () => {
    const { rerender } = render(<TrackEvent event="cart_viewed" properties={{ n: 1 }} />);
    rerender(<TrackEvent event="cart_viewed" properties={{ n: 1 }} />);

    expect(track).toHaveBeenCalledTimes(1);
  });
});
