import js from '@eslint/js';
import tseslint from 'typescript-eslint';
import react from 'eslint-plugin-react';
import reactHooks from 'eslint-plugin-react-hooks';
import jsxA11y from 'eslint-plugin-jsx-a11y';
import astro from 'eslint-plugin-astro';
import globals from 'globals';

// Catches at write time what used to surface only in a manual Sonar scan days later:
// nested ternaries, missing button types, unassociated labels, react-hooks deps, unused code.
// Deliberately non-type-checked (no `parserOptions.project`) -- this repo mixes .astro and
// .tsx under one tsconfig, and the fast, untyped rule set already covers what accumulated.

export default tseslint.config(
  {
    ignores: ['dist/**', '.astro/**', 'node_modules/**', 'coverage/**', 'playwright-report/**', 'test-results/**'],
  },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  {
    files: ['**/*.{ts,tsx}'],
    plugins: {
      react,
      'react-hooks': reactHooks,
      'jsx-a11y': jsxA11y,
    },
    languageOptions: {
      parserOptions: {
        ecmaFeatures: { jsx: true },
      },
    },
    settings: {
      react: { version: '18.3' },
    },
    rules: {
      ...react.configs.recommended.rules,
      ...reactHooks.configs.recommended.rules,
      ...jsxA11y.configs.recommended.rules,

      // The rule that would have caught almost everything in today's cleanup pass.
      'no-nested-ternary': 'error',
      // A <button> with no type defaults to "submit" inside a <form> -- silent double-submit risk.
      'react/button-has-type': 'error',
      'react/jsx-key': 'error',
      'react/prop-types': 'off', // TypeScript already enforces prop shapes.
      'react/react-in-jsx-scope': 'off', // new JSX transform, no import needed.

      // TypeScript already catches undefined/type errors; these two just add noise on top of it.
      'no-undef': 'off',
      '@typescript-eslint/no-explicit-any': 'off',
      '@typescript-eslint/no-unused-vars': ['warn', { argsIgnorePattern: '^_', varsIgnorePattern: '^_' }],

      // Tailwind's Preflight strips `list-style`, which removes the implicit AX list role in
      // Safari/VoiceOver -- explicit role="list" on <ul>/<ol> is the known workaround, used
      // deliberately across this codebase, not an oversight the rule should flag.
      'jsx-a11y/no-redundant-roles': 'off',
    },
  },
  ...astro.configs['flat/recommended'],
  {
    files: ['**/*.astro'],
    rules: {
      'no-nested-ternary': 'error',
    },
  },
  {
    // Repo-root config files: plain Node, no bundler, no TS coverage of globals.
    files: ['*.mjs', '*.ts'],
    languageOptions: {
      globals: globals.node,
    },
  },
  {
    // Playwright script: page.evaluate() callbacks run in the browser but live in the same
    // file/AST as the Node driver code around them, so both global sets apply here at once.
    files: ['scripts/**/*.mjs'],
    languageOptions: {
      globals: { ...globals.node, ...globals.browser },
    },
  },
  {
    // Test files: mocks and fixtures legitimately shadow/redeclare more than production code.
    files: ['**/*.test.{ts,tsx}', '**/e2e/**'],
    rules: {
      '@typescript-eslint/no-unused-vars': 'off',
    },
  },
  {
    // Ambient declaration files: a triple-slash reference is the correct way to pull in Astro's
    // generated types.d.ts here -- an `import` would turn this into a module and break the
    // global `Window` augmentation below it.
    files: ['**/*.d.ts'],
    rules: {
      '@typescript-eslint/triple-slash-reference': 'off',
    },
  },
);
