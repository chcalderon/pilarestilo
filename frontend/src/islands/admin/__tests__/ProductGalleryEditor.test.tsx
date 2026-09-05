import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';
import ProductGalleryEditor from '../ProductGalleryEditor';
import { uploadMediaFile } from '../../../lib/api';

vi.mock('../../../lib/api', () => ({ uploadMediaFile: vi.fn() }));

function setup(value: string[], overrides: Partial<Parameters<typeof ProductGalleryEditor>[0]> = {}) {
  const onChange = vi.fn();
  const onCoverChange = vi.fn();
  render(
    <ProductGalleryEditor
      value={value}
      onChange={onChange}
      coverUrl="https://img/cover.jpg"
      onCoverChange={onCoverChange}
      token="t"
      {...overrides}
    />,
  );
  return { onChange, onCoverChange };
}

beforeEach(() => vi.mocked(uploadMediaFile).mockResolvedValue('https://img/new.jpg'));

describe('ProductGalleryEditor', () => {
  it('moves an image down', async () => {
    const user = userEvent.setup();
    const { onChange } = setup(['https://img/a.jpg', 'https://img/b.jpg']);
    await user.click(screen.getAllByRole('button', { name: /bajar/i })[0]);
    expect(onChange).toHaveBeenCalledWith(['https://img/b.jpg', 'https://img/a.jpg']);
  });

  it('removes an image', async () => {
    const user = userEvent.setup();
    const { onChange } = setup(['https://img/a.jpg', 'https://img/b.jpg']);
    await user.click(screen.getAllByRole('button', { name: /quitar/i })[0]);
    expect(onChange).toHaveBeenCalledWith(['https://img/b.jpg']);
  });

  it('swaps a thumbnail with the cover', async () => {
    const user = userEvent.setup();
    const { onChange, onCoverChange } = setup(['https://img/a.jpg', 'https://img/b.jpg']);
    await user.click(screen.getAllByRole('button', { name: /portada/i })[0]);
    expect(onCoverChange).toHaveBeenCalledWith('https://img/a.jpg');
    expect(onChange).toHaveBeenCalledWith(['https://img/cover.jpg', 'https://img/b.jpg']);
  });

  it('hides the add control at 9 images', () => {
    setup(Array.from({ length: 9 }, (_, i) => `https://img/${i}.jpg`));
    expect(screen.queryByText(/agregar foto/i)).not.toBeInTheDocument();
    expect(screen.getByText(/máximo 10 fotos/i)).toBeInTheDocument();
  });
});
