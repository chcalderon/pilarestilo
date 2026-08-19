import { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';

/**
 * Renders an overlay against the document, not against wherever it was written.
 *
 * Every drawer and modal in the panel is returned by a component that some list renders inside a
 * `space-y-4` container, and that utility adds a top margin to each of its children. On a backdrop
 * pinned with `inset-0`, where both top and bottom are fixed, sixteen pixels of margin come
 * straight out of the height: the scrim stopped short of the bottom of the screen and a strip of
 * the page underneath stayed bright.
 *
 * A portal takes the overlay out of that flow entirely, which is where a modal belongs anyway. It
 * also frees it from any ancestor that would otherwise become its containing block.
 */
export default function Overlay({ children }: { children: React.ReactNode }) {
  const [host, setHost] = useState<HTMLElement | null>(null);

  // Mounted on the client only: there is no document while Astro renders the page on the server.
  useEffect(() => setHost(document.body), []);

  if (!host) return null;
  return createPortal(children, host);
}
