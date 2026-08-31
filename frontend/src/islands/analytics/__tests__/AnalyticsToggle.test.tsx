import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';
import AnalyticsToggle from '../AnalyticsToggle';
import { isAnalyticsOptedOut, setAnalyticsOptOut } from '../../../lib/analytics';

vi.mock('../../../lib/analytics', () => ({
  isAnalyticsOptedOut: vi.fn(() => false),
  setAnalyticsOptOut: vi.fn(),
}));

beforeEach(() => {
  vi.mocked(isAnalyticsOptedOut).mockReturnValue(false);
  vi.mocked(setAnalyticsOptOut).mockClear();
  cleanup();
});

describe('AnalyticsToggle', () => {
  it('reflects the current opt-out state on mount', () => {
    vi.mocked(isAnalyticsOptedOut).mockReturnValue(true);
    render(<AnalyticsToggle locale="es" />);
    expect(screen.getByRole('checkbox')).not.toBeChecked();
    expect(screen.getByText(/no se registra tu navegación/i)).toBeInTheDocument();
  });

  it('turning it off writes the opt-out flag', async () => {
    const user = userEvent.setup();
    render(<AnalyticsToggle locale="es" />);
    const box = screen.getByRole('checkbox');
    expect(box).toBeChecked();

    await user.click(box);

    expect(setAnalyticsOptOut).toHaveBeenCalledWith(true);
    expect(box).not.toBeChecked();
  });

  it('turning it back on clears the flag', async () => {
    vi.mocked(isAnalyticsOptedOut).mockReturnValue(true);
    const user = userEvent.setup();
    render(<AnalyticsToggle locale="en" />);
    const box = screen.getByRole('checkbox');

    await user.click(box);

    expect(setAnalyticsOptOut).toHaveBeenCalledWith(false);
    expect(box).toBeChecked();
  });
});
