import { useState, useEffect, useCallback, useRef } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import type { NavigationSectionDto } from '../../lib/api';

interface Props {
  readonly sections: NavigationSectionDto[];
  readonly locale: string;
}

const INTENT_DELAY = 120;   // ms before showing
const GRACE_DELAY = 200;    // ms grace before hiding

export default function MegaMenuTray({ sections, locale }: Props) {
  const [activeSlug, setActiveSlug] = useState<string | null>(null);
  const intentTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const graceTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const trayRef = useRef<HTMLDivElement | null>(null);
  const pendingFocusSlugRef = useRef<string | null>(null);
  const [focusRequestVersion, setFocusRequestVersion] = useState(0);

  const syncState = useCallback((nextSlug: string | null) => {
    window.dispatchEvent(new CustomEvent('mega-menu:state', { detail: { activeSlug: nextSlug } }));
  }, []);

  const requestClose = useCallback((restoreFocus = false) => {
    setActiveSlug(null);
    if (restoreFocus) {
      window.dispatchEvent(new CustomEvent('mega-menu:closed', { detail: { restoreFocus: true } }));
    }
  }, []);

  const open = useCallback((slug: string) => {
    if (graceTimer.current) clearTimeout(graceTimer.current);
    intentTimer.current = setTimeout(() => setActiveSlug(slug), INTENT_DELAY);
  }, []);

  const close = useCallback(() => {
    if (intentTimer.current) clearTimeout(intentTimer.current);
    graceTimer.current = setTimeout(() => requestClose(false), GRACE_DELAY);
  }, [requestClose]);

  const cancelClose = useCallback(() => {
    if (graceTimer.current) clearTimeout(graceTimer.current);
    syncState(activeSlug);
  }, [activeSlug, syncState]);

  useEffect(() => {
    const handleOpen = (e: Event) => {
      const detail = (e as CustomEvent<{ slug?: string; focusFirst?: boolean }>).detail;
      const slug = detail?.slug;
      if (!slug) return;
      if (detail.focusFirst) {
        pendingFocusSlugRef.current = slug;
        setFocusRequestVersion((value) => value + 1);
      }
      open(slug);
    };
    const handleClose = () => close();
    window.addEventListener('mega-menu:open', handleOpen);
    window.addEventListener('mega-menu:close', handleClose);
    return () => {
      window.removeEventListener('mega-menu:open', handleOpen);
      window.removeEventListener('mega-menu:close', handleClose);
    };
  }, [open, close]);

  // Close on Esc
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') requestClose(true);
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [requestClose]);

  useEffect(() => {
    syncState(activeSlug);
  }, [activeSlug, syncState]);

  const activeSection = sections.find((s) => s.rootCategorySlug === activeSlug) ?? null;

  useEffect(() => {
    if (!activeSection) return;
    if (pendingFocusSlugRef.current !== activeSection.rootCategorySlug) return;

    let cancelled = false;
    const focusFirstMenuItem = (attempt = 0) => {
      if (cancelled) return;
      const firstItem = trayRef.current?.querySelector("a[href]");
      if (firstItem instanceof HTMLElement) {
        pendingFocusSlugRef.current = null;
        firstItem.focus();
        return;
      }
      if (attempt >= 8) return;
      window.requestAnimationFrame(() => focusFirstMenuItem(attempt + 1));
    };

    focusFirstMenuItem();
    return () => {
      cancelled = true;
    };
  }, [activeSection, focusRequestVersion]);

  // Split children into columns (max 3 groups of children, 4th slot = banner)
  const getColumns = (section: NavigationSectionDto) => {
    const count = Math.max(1, Math.min(section.columnCount - 1, 3)); // reserve last col for banner
    if (section.children.length === 0) return [];
    const perCol = Math.ceil(section.children.length / count);
    const cols = [];
    for (let i = 0; i < count; i++) {
      const slice = section.children.slice(i * perCol, (i + 1) * perCol);
      if (slice.length > 0) cols.push(slice);
    }
    return cols;
  };

  const prefersReducedMotion = typeof window !== 'undefined' &&
    window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;

  const itemMotion = prefersReducedMotion
    ? undefined
    : { hidden: { opacity: 0, y: 6 }, show: { opacity: 1, y: 0 } };

  const renderColumnsLayout = (section: NavigationSectionDto) => (
    <>
      {getColumns(section).map((col) => (
        <motion.div key={col[0]?.id} variants={itemMotion}>
          <ul className="flex flex-col gap-1.5">
            {col.map((child) => (
              <li key={child.id}>
                <a
                  href={`/${locale}/categories/${child.slug}`}
                  className="block font-sans text-[0.7rem] tracking-[0.16em] uppercase text-pe-on-dark-muted hover:text-pe-rose-soft transition-colors duration-150 py-0.5"
                >
                  {child.name}
                </a>
                {child.children?.map((gc) => (
                  <a
                    key={gc.id}
                    href={`/${locale}/categories/${gc.slug}`}
                    className="block ml-3 font-sans text-[0.61rem] tracking-[0.1em] uppercase text-pe-on-dark-muted hover:text-pe-rose-soft/70 transition-colors duration-150 py-0.5"
                  >
                    {gc.name}
                  </a>
                ))}
              </li>
            ))}
          </ul>
        </motion.div>
      ))}
    </>
  );

  const renderFeaturedGridLayout = (section: NavigationSectionDto) => {
    const featuredChildren = section.children.filter((child) => child.featured || child.imageUrl);
    const remainingChildren = section.children.filter((child) => !featuredChildren.includes(child));

    return (
      <>
        <motion.div
          variants={itemMotion}
          className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3"
          style={{ gridColumn: `span ${Math.max(1, section.columnCount - 1)} / span ${Math.max(1, section.columnCount - 1)}` }}
        >
          {(featuredChildren.length > 0 ? featuredChildren : section.children.slice(0, 3)).map((child) => (
            <a
              key={child.id}
              href={`/${locale}/categories/${child.slug}`}
              className="group relative overflow-hidden border border-pe-white/8 bg-pe-white/[0.03] min-h-[12rem]"
            >
              {child.imageUrl ? (
                <img
                  src={child.imageUrl}
                  alt={child.name}
                  className="absolute inset-0 h-full w-full object-cover opacity-75 transition-transform duration-500 group-hover:scale-[1.03]"
                  loading="lazy"
                />
              ) : null}
              <div className="absolute inset-0 bg-gradient-to-t from-black/80 via-black/25 to-transparent" />
              <div className="relative flex h-full flex-col justify-end p-4">
                <p className="font-display text-xl text-pe-cream">{child.name}</p>
                {child.children[0] ? (
                  <p className="mt-1 font-sans text-[0.62rem] uppercase tracking-[0.16em] text-pe-cream/70">
                    {child.children[0].name}
                  </p>
                ) : null}
              </div>
            </a>
          ))}
        </motion.div>
        {remainingChildren.length > 0 ? (
          <motion.div variants={itemMotion} className="space-y-2">
            <p className="font-sans text-[0.62rem] uppercase tracking-[0.18em] text-pe-on-dark-muted">
              {locale === 'es' ? 'Explorar' : 'Explore'}
            </p>
            <ul className="space-y-1.5">
              {remainingChildren.map((child) => (
                <li key={child.id}>
                  <a
                    href={`/${locale}/categories/${child.slug}`}
                    className="block font-sans text-[0.68rem] tracking-[0.14em] uppercase text-pe-on-dark-muted hover:text-pe-rose-soft transition-colors"
                  >
                    {child.name}
                  </a>
                </li>
              ))}
            </ul>
          </motion.div>
        ) : null}
      </>
    );
  };

  const renderEditorialLayout = (section: NavigationSectionDto) => {
    const leadChildren = section.children.slice(0, 2);
    const trailChildren = section.children.slice(2);

    return (
      <>
        <motion.div
          variants={itemMotion}
          className="grid gap-4 md:grid-cols-2"
          style={{ gridColumn: `span ${Math.max(1, section.columnCount - 1)} / span ${Math.max(1, section.columnCount - 1)}` }}
        >
          {leadChildren.map((child) => (
            <a
              key={child.id}
              href={`/${locale}/categories/${child.slug}`}
              className="group flex min-h-[15rem] flex-col justify-end overflow-hidden border border-pe-white/8 bg-pe-white/[0.03] p-5"
              style={child.imageUrl ? { backgroundImage: `linear-gradient(to top, rgba(0,0,0,0.78), rgba(0,0,0,0.15)), url(${child.imageUrl})`, backgroundSize: 'cover', backgroundPosition: 'center' } : undefined}
            >
              <p className="font-display text-2xl text-pe-cream transition-colors group-hover:text-pe-rose-soft">
                {child.name}
              </p>
              {child.children[0] ? (
                <p className="mt-2 font-sans text-[0.62rem] uppercase tracking-[0.18em] text-pe-cream/70">
                  {child.children[0].name}
                </p>
              ) : null}
            </a>
          ))}
        </motion.div>
        <motion.div variants={itemMotion} className="space-y-3">
          <p className="font-sans text-[0.62rem] uppercase tracking-[0.18em] text-pe-on-dark-muted">
            {locale === 'es' ? 'Selecciones' : 'Selections'}
          </p>
          <ul className="space-y-2">
            {trailChildren.map((child) => (
              <li key={child.id}>
                <a
                  href={`/${locale}/categories/${child.slug}`}
                  className="block border-b border-pe-white/8 pb-2 font-display text-lg text-pe-cream/85 hover:text-pe-rose-soft transition-colors"
                >
                  {child.name}
                </a>
              </li>
            ))}
          </ul>
        </motion.div>
      </>
    );
  };

  return (
    <>
      {/* Backdrop */}
      <AnimatePresence>
        {activeSlug && (
          <motion.div
            key="backdrop"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.18 }}
            className="fixed inset-0 z-30 bg-black/25 backdrop-blur-[2px]"
            style={{ top: 'var(--header-height, 0px)' }}
            onClick={() => requestClose(false)}
          />
        )}
      </AnimatePresence>

      {/* Tray */}
      <AnimatePresence>
        {activeSection && (
          <motion.nav
            ref={trayRef}
            key={activeSection.rootCategorySlug}
            initial={prefersReducedMotion ? { opacity: 0 } : { opacity: 0, y: 8 }}
            animate={prefersReducedMotion ? { opacity: 1 } : { opacity: 1, y: 0 }}
            exit={prefersReducedMotion ? { opacity: 0 } : { opacity: 0, y: 4 }}
            transition={{ duration: 0.28, ease: [0.22, 1, 0.36, 1] }}
            className="mega-tray fixed left-0 right-0 z-40 bg-pe-black border-t border-pe-white/8 shadow-2xl overflow-auto"
            style={{ top: 'var(--header-height, 0px)', minHeight: '60vh', maxHeight: '80vh' }}
            onMouseEnter={cancelClose}
            onMouseLeave={close}
            aria-label={activeSection.rootCategoryName}
          >
            <div className="max-w-7xl mx-auto px-6 py-8">
              {/* Section header */}
              <div className="mb-6">
                <a
                  href={`/${locale}/categories/${activeSection.rootCategorySlug}`}
                  className="font-display text-2xl text-pe-cream hover:text-pe-rose-soft transition-colors duration-200"
                >
                  {activeSection.rootCategoryName}
                </a>
              </div>

              {/* Columns grid */}
              <motion.div
                className="grid gap-8"
                style={{ gridTemplateColumns: `repeat(${activeSection.columnCount}, minmax(0, 1fr))` }}
                variants={{
                  show: { transition: { delayChildren: 0.08, staggerChildren: 0.04 } },
                  hidden: {},
                }}
                initial="hidden"
                animate="show"
              >
                {(() => {
                  if (activeSection.layout === 'FEATURED_GRID') return renderFeaturedGridLayout(activeSection);
                  if (activeSection.layout === 'EDITORIAL') return renderEditorialLayout(activeSection);
                  return renderColumnsLayout(activeSection);
                })()}

                {/* Banner column (last) */}
                {activeSection.bannerImageUrl && (
                  <motion.div variants={itemMotion} className="flex flex-col">
                    <a
                      href={activeSection.bannerLink || `/${locale}/categories/${activeSection.rootCategorySlug}`}
                      className="group relative block overflow-hidden rounded-xs"
                    >
                      <img
                        src={activeSection.bannerImageUrl}
                        alt={activeSection.bannerTitle || activeSection.rootCategoryName}
                        loading="lazy"
                        className="w-full object-cover transition-transform duration-500 group-hover:scale-[1.03]"
                        style={{ aspectRatio: '3/4', maxHeight: '280px' }}
                      />
                      {(activeSection.bannerTitle || activeSection.bannerSubtitle) && (
                        <div className="absolute inset-0 flex flex-col justify-end p-4 bg-gradient-to-t from-black/60 via-black/10 to-transparent">
                          {activeSection.bannerTitle && (
                            <p className="font-display text-pe-cream text-lg leading-tight">
                              {activeSection.bannerTitle}
                            </p>
                          )}
                          {activeSection.bannerSubtitle && (
                            <p className="font-sans text-pe-cream/80 text-[0.65rem] tracking-widest uppercase mt-1">
                              {activeSection.bannerSubtitle}
                            </p>
                          )}
                        </div>
                      )}
                    </a>
                  </motion.div>
                )}

                {/* Hero image fallback (no banner configured) */}
                {!activeSection.bannerImageUrl && activeSection.heroImageUrl && (
                  <motion.div variants={itemMotion} className="flex flex-col">
                    <a
                      href={`/${locale}/categories/${activeSection.rootCategorySlug}`}
                      className="group relative block overflow-hidden rounded-xs"
                    >
                      <img
                        src={activeSection.heroImageUrl}
                        alt={activeSection.rootCategoryName}
                        loading="lazy"
                        className="w-full object-cover transition-transform duration-500 group-hover:scale-[1.03]"
                        style={{ aspectRatio: '3/4', maxHeight: '280px' }}
                      />
                    </a>
                  </motion.div>
                )}
              </motion.div>
            </div>
          </motion.nav>
        )}
      </AnimatePresence>
    </>
  );
}
