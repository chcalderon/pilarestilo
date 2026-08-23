import { useState, useEffect, useCallback, useRef } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import type { NavigationSectionDto, NavigationChildDto } from '../../lib/api';

interface Props {
  readonly sections: NavigationSectionDto[];
  readonly locale: string;
}

type NavLevel =
  | { type: 'root' }
  | { type: 'section'; section: NavigationSectionDto }
  | { type: 'child'; section: NavigationSectionDto; child: NavigationChildDto };

export default function MobileNavOverlay({ sections, locale }: Props) {
  const [open, setOpen] = useState(false);
  const [stack, setStack] = useState<NavLevel[]>([{ type: 'root' }]);
  const closeButtonRef = useRef<HTMLButtonElement | null>(null);

  const close = useCallback(() => {
    setOpen(false);
    setStack([{ type: 'root' }]);
  }, []);

  // Listen for open/close events from hamburger button in Navbar
  useEffect(() => {
    const handleOpen = () => setOpen(true);
    const handleClose = () => close();
    window.addEventListener('mobile-nav:open', handleOpen);
    window.addEventListener('mobile-nav:close', handleClose);
    return () => {
      window.removeEventListener('mobile-nav:open', handleOpen);
      window.removeEventListener('mobile-nav:close', handleClose);
    };
  }, [close]);

  // Esc closes
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') close(); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, close]);

  // Prevent body scroll when open
  useEffect(() => {
    document.body.style.overflow = open ? 'hidden' : '';
    document.documentElement.setAttribute('data-mobile-nav-open', open ? 'true' : 'false');
    if (open) {
      window.requestAnimationFrame(() => closeButtonRef.current?.focus());
    }
    return () => {
      document.body.style.overflow = '';
      document.documentElement.setAttribute('data-mobile-nav-open', 'false');
    };
  }, [open]);

  const current = stack[stack.length - 1];
  const canGoBack = stack.length > 1;

  const pushSection = (section: NavigationSectionDto) => {
    setStack((prev) => [...prev, { type: 'section', section }]);
  };
  const pushChild = (section: NavigationSectionDto, child: NavigationChildDto) => {
    setStack((prev) => [...prev, { type: 'child', section, child }]);
  };
  const goBack = () => setStack((prev) => prev.slice(0, -1));

  const prefersReducedMotion = typeof window !== 'undefined'
    && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  return (
    <AnimatePresence>
      {open && (
        <>
          {/* Backdrop */}
          <motion.div
            key="mobile-backdrop"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.22 }}
            className="fixed inset-0 z-50 bg-black/40"
            onClick={close}
          />

          {/* Overlay panel */}
          <motion.div
            key={`level-${stack.length}`}
            initial={prefersReducedMotion ? { opacity: 0 } : { x: '100%' }}
            animate={prefersReducedMotion ? { opacity: 1 } : { x: 0 }}
            exit={prefersReducedMotion ? { opacity: 0 } : { x: '100%' }}
            transition={{ duration: 0.28, ease: [0.22, 1, 0.36, 1] }}
            className="fixed inset-y-0 right-0 z-50 flex flex-col w-full max-w-sm bg-pe-black shadow-2xl"
            role="dialog"
            aria-modal="true"
            aria-label={locale === 'es' ? 'Menú de navegación' : 'Navigation menu'}
          >
            {/* Header */}
            <div className="flex items-center justify-between px-5 py-4 border-b border-pe-white/8">
              {canGoBack ? (
                <button
                  type="button"
                  onClick={goBack}
                  className="flex items-center gap-2 text-pe-on-dark-muted hover:text-pe-white transition-colors text-[0.7rem] tracking-widest uppercase"
                  aria-label={locale === 'es' ? 'Volver' : 'Back'}
                >
                  <span aria-hidden="true">←</span>
                  {locale === 'es' ? 'Volver' : 'Back'}
                </button>
              ) : (
                <span className="font-display text-pe-cream text-lg">
                  {locale === 'es' ? 'Menú' : 'Menu'}
                </span>
              )}
              <button
                type="button"
                ref={closeButtonRef}
                onClick={close}
                className="text-pe-on-dark-muted hover:text-pe-white transition-colors p-1"
                aria-label={locale === 'es' ? 'Cerrar menú' : 'Close menu'}
              >
                <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                  <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
                </svg>
              </button>
            </div>

            {/* Content */}
            <div className="flex-1 overflow-y-auto overscroll-contain">
              {current.type === 'root' && (
                <nav aria-label={locale === 'es' ? 'Categorías' : 'Categories'}>
                  <ul role="list" className="divide-y divide-pe-white/5">
                    <li>
                      <a href={`/${locale}/products`} onClick={close}
                         className="flex items-center justify-between px-5 py-4 font-sans text-[0.7rem] tracking-[0.2em] uppercase text-pe-on-dark-muted hover:text-pe-rose-soft hover:bg-pe-white/4 transition-colors">
                        {locale === 'es' ? 'Todo' : 'All'}
                      </a>
                    </li>
                    {sections.map((section) => (
                      <li key={section.rootCategorySlug}>
                        {section.children.length > 0 ? (
                          <button
                            type="button"
                            onClick={() => pushSection(section)}
                            className="w-full flex items-center justify-between px-5 py-4 font-display text-xl text-pe-cream hover:text-pe-rose-soft hover:bg-pe-white/4 transition-colors text-left"
                            aria-expanded="false"
                          >
                            <span>{section.rootCategoryName}</span>
                            <span className="text-pe-on-dark-muted text-sm" aria-hidden="true">›</span>
                          </button>
                        ) : (
                          <a href={`/${locale}/categories/${section.rootCategorySlug}`} onClick={close}
                             className="flex items-center justify-between px-5 py-4 font-display text-xl text-pe-cream hover:text-pe-rose-soft hover:bg-pe-white/4 transition-colors">
                            {section.rootCategoryName}
                          </a>
                        )}
                      </li>
                    ))}
                  </ul>
                </nav>
              )}

              {current.type === 'section' && (
                <nav aria-label={current.section.rootCategoryName}>
                  {/* Root category link */}
                  <a href={`/${locale}/categories/${current.section.rootCategorySlug}`} onClick={close}
                     className="flex items-center gap-2 px-5 py-3.5 border-b border-pe-white/5 font-sans text-[0.63rem] tracking-[0.18em] uppercase text-pe-rose-soft/80 hover:text-pe-rose-soft transition-colors">
                    {locale === 'es' ? `Ver todo en ${current.section.rootCategoryName}` : `View all in ${current.section.rootCategoryName}`}
                  </a>
                  <ul role="list" className="divide-y divide-pe-white/5">
                    {current.section.children.map((child) => (
                      <li key={child.slug}>
                        {child.children.length > 0 ? (
                          <button
                            type="button"
                            onClick={() => pushChild(current.section, child)}
                            className="w-full flex items-center justify-between px-5 py-3.5 font-sans text-[0.72rem] tracking-[0.14em] uppercase text-pe-on-dark-muted hover:text-pe-rose-soft hover:bg-pe-white/4 transition-colors text-left"
                          >
                            <span>{child.name}</span>
                            <span className="text-pe-on-dark-muted text-xs" aria-hidden="true">›</span>
                          </button>
                        ) : (
                          <a href={`/${locale}/categories/${child.slug}`} onClick={close}
                             className="flex items-center px-5 py-3.5 font-sans text-[0.72rem] tracking-[0.14em] uppercase text-pe-on-dark-muted hover:text-pe-rose-soft hover:bg-pe-white/4 transition-colors">
                            {child.name}
                          </a>
                        )}
                      </li>
                    ))}
                  </ul>
                </nav>
              )}

              {current.type === 'child' && (
                <nav aria-label={current.child.name}>
                  <a href={`/${locale}/categories/${current.child.slug}`} onClick={close}
                     className="flex items-center gap-2 px-5 py-3.5 border-b border-pe-white/5 font-sans text-[0.63rem] tracking-[0.18em] uppercase text-pe-rose-soft/80 hover:text-pe-rose-soft transition-colors">
                    {locale === 'es' ? `Ver todo en ${current.child.name}` : `View all in ${current.child.name}`}
                  </a>
                  <ul role="list" className="divide-y divide-pe-white/5">
                    {current.child.children.map((gc) => (
                      <li key={gc.slug}>
                        <a href={`/${locale}/categories/${gc.slug}`} onClick={close}
                           className="flex items-center px-5 py-3.5 font-sans text-[0.72rem] tracking-[0.14em] uppercase text-pe-on-dark-muted hover:text-pe-rose-soft hover:bg-pe-white/4 transition-colors">
                          {gc.name}
                        </a>
                      </li>
                    ))}
                  </ul>
                </nav>
              )}
            </div>

            {/* Bottom bar inside overlay — persistent links */}
            <div className="border-t border-pe-white/8 px-5 py-3.5 flex items-center justify-around">
              <a href={`/${locale}/products`} onClick={close}
                 className="flex flex-col items-center gap-1 text-pe-on-dark-muted hover:text-pe-rose-soft transition-colors">
                <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
                <span className="font-sans text-[0.55rem] tracking-widest uppercase">{locale === 'es' ? 'Buscar' : 'Search'}</span>
              </a>
              <a href={`/${locale}/wishlist`} onClick={close}
                 className="flex flex-col items-center gap-1 text-pe-on-dark-muted hover:text-pe-rose-soft transition-colors">
                <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/></svg>
                <span className="font-sans text-[0.55rem] tracking-widest uppercase">{locale === 'es' ? 'Favoritos' : 'Wishlist'}</span>
              </a>
              <a href={`/${locale}/cart`} onClick={close}
                 className="flex flex-col items-center gap-1 text-pe-on-dark-muted hover:text-pe-rose-soft transition-colors">
                <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M6 2L3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4z"/><line x1="3" y1="6" x2="21" y2="6"/><path d="M16 10a4 4 0 0 1-8 0"/></svg>
                <span className="font-sans text-[0.55rem] tracking-widest uppercase">{locale === 'es' ? 'Carro' : 'Cart'}</span>
              </a>
            </div>
          </motion.div>
        </>
      )}
    </AnimatePresence>
  );
}
