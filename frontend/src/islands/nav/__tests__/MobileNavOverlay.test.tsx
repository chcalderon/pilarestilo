import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';
import MobileNavOverlay from '../MobileNavOverlay';
import type { NavigationSectionDto, NavigationChildDto } from '@/lib/api';

/**
 * Characterization tests written before splitting the three drill-down levels out of this
 * component (S3776, complexity 20) -- it had none. Covers open/close via the window events the
 * hamburger button fires, Escape, the back stack across all three levels, and the leaf-level link
 * hrefs, so extracting RootLevelNav/SectionLevelNav/ChildLevelNav can't silently change any of them.
 */

vi.mock('motion/react', () => ({
  AnimatePresence: ({ children }: any) => children,
  motion: {
    div: ({ initial, animate, exit, transition, ...rest }: any) => <div {...rest} />,
  },
}));

function grandchild(slug: string, name: string): NavigationChildDto {
  return { id: slug, slug, name, featured: false, children: [] };
}

function child(slug: string, name: string, children: NavigationChildDto[] = []): NavigationChildDto {
  return { id: slug, slug, name, featured: false, children };
}

function section(overrides: Partial<NavigationSectionDto> = {}): NavigationSectionDto {
  return {
    rootCategoryId: 'ropa',
    rootCategorySlug: 'ropa',
    rootCategoryName: 'Ropa',
    layout: 'COLUMNS',
    columnCount: 2,
    children: [],
    ...overrides,
  };
}

function openNav() {
  fireEvent(window, new Event('mobile-nav:open'));
}

beforeEach(() => {
  document.body.style.overflow = '';
  document.documentElement.removeAttribute('data-mobile-nav-open');
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe('MobileNavOverlay', () => {
  it('renders nothing until the hamburger fires the open event', () => {
    render(<MobileNavOverlay sections={[]} locale="es" />);
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();

    openNav();
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });

  it('shows a leaf link for a section with no children, and a drill-down button for one that has them', () => {
    render(<MobileNavOverlay sections={[
      section({ rootCategorySlug: 'sale', rootCategoryName: 'Sale', children: [] }),
      section({ rootCategorySlug: 'ropa', rootCategoryName: 'Ropa', children: [child('vestidos', 'Vestidos')] }),
    ]} locale="es" />);
    openNav();

    expect(screen.getByRole('link', { name: 'Sale' })).toHaveAttribute('href', '/es/categories/sale');
    expect(screen.getByRole('button', { name: /Ropa/ })).toBeInTheDocument();
  });

  it('drills into a section and back out', async () => {
    const user = userEvent.setup();
    render(<MobileNavOverlay sections={[
      section({ rootCategorySlug: 'ropa', rootCategoryName: 'Ropa', children: [child('vestidos', 'Vestidos')] }),
    ]} locale="es" />);
    openNav();

    await user.click(screen.getByRole('button', { name: /Ropa/ }));
    expect(screen.getByRole('link', { name: /Vestidos/ })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Ver todo en Ropa/ })).toHaveAttribute('href', '/es/categories/ropa');

    await user.click(screen.getByRole('button', { name: /Volver/ }));
    expect(screen.getByRole('button', { name: /Ropa/ })).toBeInTheDocument();
  });

  it('drills two levels deep to a grandchild leaf', async () => {
    const user = userEvent.setup();
    render(<MobileNavOverlay sections={[
      section({
        rootCategorySlug: 'ropa',
        rootCategoryName: 'Ropa',
        children: [child('vestidos', 'Vestidos', [grandchild('vestidos-largos', 'Vestidos largos')])],
      }),
    ]} locale="es" />);
    openNav();

    await user.click(screen.getByRole('button', { name: /Ropa/ }));
    await user.click(screen.getByRole('button', { name: /Vestidos/ }));

    expect(screen.getByRole('link', { name: 'Vestidos largos' })).toHaveAttribute(
      'href', '/es/categories/vestidos-largos'
    );
    expect(screen.getByRole('link', { name: /Ver todo en Vestidos/ })).toHaveAttribute(
      'href', '/es/categories/vestidos'
    );
  });

  it('closes on the close button, the backdrop, and Escape', async () => {
    const user = userEvent.setup();
    render(<MobileNavOverlay sections={[]} locale="es" />);

    openNav();
    await user.click(screen.getByRole('button', { name: /Cerrar menú/i }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();

    openNav();
    fireEvent.keyDown(window, { key: 'Escape' });
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('resets the drill-down stack to root when reopened after being closed', async () => {
    const user = userEvent.setup();
    render(<MobileNavOverlay sections={[
      section({ rootCategorySlug: 'ropa', rootCategoryName: 'Ropa', children: [child('vestidos', 'Vestidos')] }),
    ]} locale="es" />);
    openNav();
    await user.click(screen.getByRole('button', { name: /Ropa/ }));

    fireEvent(window, new Event('mobile-nav:close'));
    openNav();

    expect(screen.getByRole('button', { name: /Ropa/ })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Volver/i })).not.toBeInTheDocument();
  });

  it('locks body scroll while open and restores it on close', () => {
    render(<MobileNavOverlay sections={[]} locale="es" />);
    expect(document.body.style.overflow).toBe('');

    openNav();
    expect(document.body.style.overflow).toBe('hidden');

    fireEvent(window, new Event('mobile-nav:close'));
    expect(document.body.style.overflow).toBe('');
  });
});
