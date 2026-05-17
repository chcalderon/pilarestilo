import { useState, useEffect, useCallback, useRef } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import type { NavigationSectionDto } from '../../lib/api';

interface Props {
  sections: NavigationSectionDto[];
  locale: string;
}

const INTENT_DELAY = 120;   // ms before showing
const GRACE_DELAY = 200;    // ms grace before hiding

export default function MegaMenuTray({ sections, locale }: Props) {
  const [activeSlug, setActiveSlug] = useState<string | null>(null);
  const intentTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const graceTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const open = useCallback((slug: string) => {
    if (graceTimer.current) clearTimeout(graceTimer.current);
    intentTimer.current = setTimeout(() => setActiveSlug(slug), INTENT_DELAY);
  }, []);

  const close = useCallback(() => {
    if (intentTimer.current) clearTimeout(intentTimer.current);
    graceTimer.current = setTimeout(() => setActiveSlug(null), GRACE_DELAY);
  }, []);

  const cancelClose = useCallback(() => {
    if (graceTimer.current) clearTimeout(graceTimer.current);
  }, []);

  useEffect(() => {
    const handleOpen = (e: Event) => open((e as CustomEvent).detail.slug);
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
      if (e.key === 'Escape') setActiveSlug(null);
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, []);

  const activeSection = sections.find((s) => s.rootCategorySlug === activeSlug) ?? null;

  // Split children into columns (max 3 groups of children, 4th slot = banner)
  const getColumns = (section: NavigationSectionDto) => {
    const count = Math.min(section.columnCount - 1, 3); // reserve last col for banner
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
            onClick={() => setActiveSlug(null)}
          />
        )}
      </AnimatePresence>

      {/* Tray */}
      <AnimatePresence>
        {activeSection && (
          <motion.div
            key={activeSection.rootCategorySlug}
            initial={prefersReducedMotion ? { opacity: 0 } : { opacity: 0, y: 8 }}
            animate={prefersReducedMotion ? { opacity: 1 } : { opacity: 1, y: 0 }}
            exit={prefersReducedMotion ? { opacity: 0 } : { opacity: 0, y: 4 }}
            transition={{ duration: 0.28, ease: [0.22, 1, 0.36, 1] }}
            className="mega-tray fixed left-0 right-0 z-40 bg-pe-black border-t border-pe-white/8 shadow-2xl overflow-auto"
            style={{ top: 'var(--header-height, 0px)', minHeight: '60vh', maxHeight: '80vh' }}
            onMouseEnter={cancelClose}
            onMouseLeave={close}
            role="menu"
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
                {/* Child columns */}
                {getColumns(activeSection).map((col, i) => (
                  <motion.div
                    key={i}
                    variants={
                      prefersReducedMotion
                        ? {}
                        : { hidden: { opacity: 0, y: 6 }, show: { opacity: 1, y: 0 } }
                    }
                  >
                    <ul role="list" className="flex flex-col gap-1.5">
                      {col.map((child) => (
                        <li key={child.id}>
                          <a
                            href={`/${locale}/categories/${child.slug}`}
                            role="menuitem"
                            className="block font-sans text-[0.7rem] tracking-[0.16em] uppercase text-pe-white/70 hover:text-pe-rose-soft transition-colors duration-150 py-0.5"
                          >
                            {child.name}
                          </a>
                          {child.children?.map((gc) => (
                            <a
                              key={gc.id}
                              href={`/${locale}/categories/${gc.slug}`}
                              role="menuitem"
                              className="block ml-3 font-sans text-[0.61rem] tracking-[0.1em] uppercase text-pe-white/40 hover:text-pe-rose-soft/70 transition-colors duration-150 py-0.5"
                            >
                              {gc.name}
                            </a>
                          ))}
                        </li>
                      ))}
                    </ul>
                  </motion.div>
                ))}

                {/* Banner column (last) */}
                {activeSection.bannerImageUrl && (
                  <motion.div
                    variants={
                      prefersReducedMotion
                        ? {}
                        : { hidden: { opacity: 0, y: 6 }, show: { opacity: 1, y: 0 } }
                    }
                    className="flex flex-col"
                  >
                    <a
                      href={activeSection.bannerLink || `/${locale}/categories/${activeSection.rootCategorySlug}`}
                      role="menuitem"
                      className="group relative block overflow-hidden rounded-sm"
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
                  <motion.div
                    variants={
                      prefersReducedMotion
                        ? {}
                        : { hidden: { opacity: 0, y: 6 }, show: { opacity: 1, y: 0 } }
                    }
                    className="flex flex-col"
                  >
                    <a
                      href={`/${locale}/categories/${activeSection.rootCategorySlug}`}
                      role="menuitem"
                      className="group relative block overflow-hidden rounded-sm"
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
          </motion.div>
        )}
      </AnimatePresence>
    </>
  );
}
