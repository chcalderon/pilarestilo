/** @type {import('tailwindcss').Config} */
export default {
  content: ['./src/**/*.{astro,html,js,jsx,md,mdx,svelte,ts,tsx,vue}'],
  darkMode: ['selector', '[data-theme="dark"]'],
  theme: {
    extend: {
      colors: {
        'pe-rose': '#B76E79',
        'pe-rose-deep': '#8E4F58',
        'pe-rose-soft': '#E8C9CC',
        'pe-black': '#1A1A1A',
        'pe-offwhite': '#F2EAE0',
        'pe-cream': '#E3D2BE',
        /*
         * Bound to the theme variable, not a fixed hex. The variables already flipped with the
         * theme and Tailwind ignored them, so every text-pe-charcoal rendered #3A3A3A on a
         * near-black panel — 423 places where muted text was dark-on-dark. The rgb(... /
         * <alpha-value>) form is what keeps the /55 opacity modifiers working.
         */
        'pe-charcoal': 'rgb(var(--pe-charcoal-rgb) / <alpha-value>)',
        /* Readable by construction: see the note beside these variables in globals.css. */
        'pe-muted': 'var(--pe-muted-ink)',
        'pe-rose-ink': 'var(--pe-rose-ink)',
        'pe-gold-ink': 'var(--pe-gold-ink)',
        'pe-rose-display': 'var(--pe-rose-display)',
        'pe-rose-action': 'var(--pe-rose-action)',
        /* Status families: positive / danger / warning, three roles each. The bare name is the
           -rgb chip fill (so bg-pe-{fam}/10 and border-pe-{fam}/40 resolve); -ink is the readable
           text step; -surface is the panel tint. See globals.css. */
        'pe-positive': 'rgb(var(--pe-positive-rgb) / <alpha-value>)',
        'pe-positive-ink': 'var(--pe-positive-ink)',
        'pe-positive-surface': 'var(--pe-positive-surface)',
        'pe-danger': 'rgb(var(--pe-danger-rgb) / <alpha-value>)',
        'pe-danger-ink': 'var(--pe-danger-ink)',
        'pe-danger-surface': 'var(--pe-danger-surface)',
        'pe-warning': 'rgb(var(--pe-warning-rgb) / <alpha-value>)',
        'pe-warning-ink': 'var(--pe-warning-ink)',
        'pe-warning-surface': 'var(--pe-warning-surface)',
        'pe-on-dark': 'var(--pe-on-dark)',
        'pe-on-light': 'var(--pe-on-light)',
        'pe-on-dark-muted': 'var(--pe-on-dark-muted)',
        'pe-gold': '#C6A96B',
        'pe-white': '#FFFFFF',
        'pe-beige': '#F5F1EB',
      },
      fontFamily: {
        display: ['"Cormorant Garamond"', 'Georgia', 'serif'],
        script: ['"Pinyon Script"', '"Allura"', 'cursive'],
        sans: ['Montserrat', 'system-ui', 'sans-serif'],
      },
      letterSpacing: {
        'micro': '0.18em',
        'wider-x': '0.28em',
      },
      boxShadow: {
        'editorial': '0 30px 60px -30px rgba(26,26,26,0.25)',
        'card-lift': '0 18px 40px -22px rgba(143,79,88,0.35)',
      },
      keyframes: {
        'fade-up': {
          '0%':   { opacity: '0', transform: 'translateY(18px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
        'fade-in': {
          '0%':   { opacity: '0' },
          '100%': { opacity: '1' },
        },
        'letter-stagger': {
          '0%':   { opacity: '0', transform: 'translateY(40%)' },
          '60%':  { opacity: '1', transform: 'translateY(-4%)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
        'lift': {
          '0%':   { transform: 'translateY(0)' },
          '100%': { transform: 'translateY(-6px)' },
        },
        'rose-shimmer': {
          '0%, 100%': { opacity: '0.55' },
          '50%':      { opacity: '0.95' },
        },
      },
      animation: {
        'fade-up':         'fade-up 0.9s cubic-bezier(0.22,0.61,0.36,1) both',
        'fade-in':         'fade-in 1.1s ease-out both',
        'letter-stagger':  'letter-stagger 1.0s cubic-bezier(0.22,0.61,0.36,1) both',
        'rose-shimmer':    'rose-shimmer 6s ease-in-out infinite',
      },
    },
  },
  plugins: [],
};
