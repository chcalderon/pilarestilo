/// <reference path="../.astro/types.d.ts" />

/**
 * Set by ThemeBootstrap in the document head, before anything else runs. It is the only thing that
 * decides or stores the theme; every other script asks it rather than reading storage itself.
 */
interface Window {
  peTheme: {
    current(): 'light' | 'dark';
    set(theme: 'light' | 'dark'): 'light' | 'dark';
    toggle(): 'light' | 'dark';
  };
}
