import { useState, useEffect, useCallback, useRef, type RefObject } from 'react';
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

const itemClass = 'flex items-center justify-between px-5 py-4 font-sans text-[0.7rem] tracking-[0.2em] uppercase text-pe-on-dark-muted hover:text-pe-rose-soft hover:bg-pe-white/4 transition-colors';
const sectionButtonClass = 'w-full flex items-center justify-between px-5 py-4 font-display text-xl text-pe-cream hover:text-pe-rose-soft hover:bg-pe-white/4 transition-colors text-left';
const sectionLinkClass = 'flex items-center justify-between px-5 py-4 font-display text-xl text-pe-cream hover:text-pe-rose-soft hover:bg-pe-white/4 transition-colors';
const childButtonClass = 'w-full flex items-center justify-between min-h-11 px-5 py-3.5 font-sans text-[0.72rem] tracking-[0.14em] uppercase text-pe-on-dark-muted hover:text-pe-rose-soft hover:bg-pe-white/4 transition-colors text-left';
const childLinkClass = 'flex items-center justify-between min-h-11 px-5 py-3.5 font-sans text-[0.72rem] tracking-[0.14em] uppercase text-pe-on-dark-muted hover:text-pe-rose-soft hover:bg-pe-white/4 transition-colors';
const grandchildLinkClass = 'flex items-center min-h-11 px-5 py-3.5 font-sans text-[0.72rem] tracking-[0.14em] uppercase text-pe-on-dark-muted hover:text-pe-rose-soft hover:bg-pe-white/4 transition-colors';
const viewAllClass = 'flex items-center gap-2 min-h-11 px-5 py-3.5 border-b border-pe-white/5 font-sans text-[0.63rem] tracking-[0.18em] uppercase text-pe-rose-soft/80 hover:text-pe-rose-soft transition-colors';

interface RootLevelProps {
  readonly sections: NavigationSectionDto[];
  readonly locale: string;
  readonly onClose: () => void;
  readonly onSelectSection: (section: NavigationSectionDto) => void;
}

function RootLevelNav({ sections, locale, onClose, onSelectSection }: RootLevelProps) {
  return (
    <nav aria-label={locale === 'es' ? 'Categorías' : 'Categories'}>
      <ul className="divide-y divide-pe-white/5">
        <li>
          <a href={`/${locale}/products`} onClick={onClose} className={itemClass}>
            {locale === 'es' ? 'Todo' : 'All'}
          </a>
        </li>
        {sections.map((section) => (
          <li key={section.rootCategorySlug}>
            {section.children.length > 0 ? (
              <button type="button" onClick={() => onSelectSection(section)} className={sectionButtonClass} aria-expanded="false">
                <span>{section.rootCategoryName}</span>
                <span className="text-pe-on-dark-muted text-sm" aria-hidden="true">›</span>
              </button>
            ) : (
              <a href={`/${locale}/categories/${section.rootCategorySlug}`} onClick={onClose} className={sectionLinkClass}>
                {section.rootCategoryName}
              </a>
            )}
          </li>
        ))}
      </ul>
    </nav>
  );
}

interface SectionLevelProps {
  readonly section: NavigationSectionDto;
  readonly locale: string;
  readonly onClose: () => void;
  readonly onSelectChild: (section: NavigationSectionDto, child: NavigationChildDto) => void;
}

function SectionLevelNav({ section, locale, onClose, onSelectChild }: SectionLevelProps) {
  return (
    <nav aria-label={section.rootCategoryName}>
      <a href={`/${locale}/categories/${section.rootCategorySlug}`} onClick={onClose} className={viewAllClass}>
        {locale === 'es' ? `Ver todo en ${section.rootCategoryName}` : `View all in ${section.rootCategoryName}`}
      </a>
      <ul className="divide-y divide-pe-white/5">
        {section.children.map((child) => (
          <li key={child.slug}>
            {child.children.length > 0 ? (
              <button type="button" onClick={() => onSelectChild(section, child)} className={childButtonClass}>
                <span>{child.name}</span>
                <span className="text-pe-on-dark-muted text-xs" aria-hidden="true">›</span>
              </button>
            ) : (
              <a href={`/${locale}/categories/${child.slug}`} onClick={onClose} className={childLinkClass}>
                {child.name}
              </a>
            )}
          </li>
        ))}
      </ul>
    </nav>
  );
}

interface ChildLevelProps {
  readonly child: NavigationChildDto;
  readonly locale: string;
  readonly onClose: () => void;
}

function ChildLevelNav({ child, locale, onClose }: ChildLevelProps) {
  return (
    <nav aria-label={child.name}>
      <a href={`/${locale}/categories/${child.slug}`} onClick={onClose} className={viewAllClass}>
        {locale === 'es' ? `Ver todo en ${child.name}` : `View all in ${child.name}`}
      </a>
      <ul className="divide-y divide-pe-white/5">
        {child.children.map((gc) => (
          <li key={gc.slug}>
            <a href={`/${locale}/categories/${gc.slug}`} onClick={onClose} className={grandchildLinkClass}>
              {gc.name}
            </a>
          </li>
        ))}
      </ul>
    </nav>
  );
}

/** The hamburger button in Navbar lives outside this component's subtree, so open/close travel
 * as window events rather than props. */
function useOpenCloseEvents(setOpen: (open: boolean) => void, close: () => void) {
  useEffect(() => {
    const handleOpen = () => setOpen(true);
    const handleClose = () => close();
    window.addEventListener('mobile-nav:open', handleOpen);
    window.addEventListener('mobile-nav:close', handleClose);
    return () => {
      window.removeEventListener('mobile-nav:open', handleOpen);
      window.removeEventListener('mobile-nav:close', handleClose);
    };
  }, [setOpen, close]);
}

function useEscapeToClose(open: boolean, close: () => void) {
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') close(); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, close]);
}

function useLockBodyScrollAndFocus(open: boolean, closeButtonRef: RefObject<HTMLButtonElement | null>) {
  useEffect(() => {
    document.body.style.overflow = open ? 'hidden' : '';
    document.documentElement.dataset.mobileNavOpen = open ? 'true' : 'false';
    if (open) {
      window.requestAnimationFrame(() => closeButtonRef.current?.focus());
    }
    return () => {
      document.body.style.overflow = '';
      document.documentElement.dataset.mobileNavOpen = 'false';
    };
  }, [open, closeButtonRef]);
}

interface MobileNavHeaderProps {
  readonly locale: string;
  readonly canGoBack: boolean;
  readonly onBack: () => void;
  readonly onClose: () => void;
  readonly closeButtonRef: RefObject<HTMLButtonElement>;
}

function MobileNavHeader({ locale, canGoBack, onBack, onClose, closeButtonRef }: MobileNavHeaderProps) {
  return (
    <div className="flex items-center justify-between px-5 py-4 border-b border-pe-white/8">
      {canGoBack ? (
        <button
          type="button"
          onClick={onBack}
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
        onClick={onClose}
        className="icon-hit rounded-full text-pe-on-dark-muted hover:text-pe-white hover:bg-pe-white/8 transition-colors"
        aria-label={locale === 'es' ? 'Cerrar menú' : 'Close menu'}
      >
        <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
          <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
        </svg>
      </button>
    </div>
  );
}

export default function MobileNavOverlay({ sections, locale }: Props) {
  const [open, setOpen] = useState(false);
  const [stack, setStack] = useState<NavLevel[]>([{ type: 'root' }]);
  const closeButtonRef = useRef<HTMLButtonElement | null>(null);

  const close = useCallback(() => {
    setOpen(false);
    setStack([{ type: 'root' }]);
  }, []);

  useOpenCloseEvents(setOpen, close);
  useEscapeToClose(open, close);
  useLockBodyScrollAndFocus(open, closeButtonRef);

  const current = stack.at(-1)!;
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
            <MobileNavHeader
              locale={locale}
              canGoBack={canGoBack}
              onBack={goBack}
              onClose={close}
              closeButtonRef={closeButtonRef}
            />

            {/* Content */}
            <div className="flex-1 overflow-y-auto overscroll-contain">
              {current.type === 'root' && (
                <RootLevelNav sections={sections} locale={locale} onClose={close} onSelectSection={pushSection} />
              )}
              {current.type === 'section' && (
                <SectionLevelNav section={current.section} locale={locale} onClose={close} onSelectChild={pushChild} />
              )}
              {current.type === 'child' && (
                <ChildLevelNav child={current.child} locale={locale} onClose={close} />
              )}
            </div>
          </motion.div>
        </>
      )}
    </AnimatePresence>
  );
}
