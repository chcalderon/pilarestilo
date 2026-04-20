/** @type {import('tailwindcss').Config} */
export default {
  content: ['./src/**/*.{astro,html,js,jsx,md,mdx,svelte,ts,tsx,vue}'],
  theme: {
    extend: {
      colors: {
        'pe-rose': '#B76E79',
        'pe-rose-deep': '#8E4F58',
        'pe-rose-soft': '#E8C9CC',
        'pe-black': '#1A1A1A',
        'pe-offwhite': '#F8F4EF',
        'pe-cream': '#EDE3D8',
        'pe-charcoal': '#3A3A3A',
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
