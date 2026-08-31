import { useEffect, useRef } from 'react';
import { track, type CommerceEvent } from '../../lib/analytics';

interface Props {
  readonly event: CommerceEvent;
  readonly properties?: Record<string, unknown>;
}

/**
 * Fires one commerce event when it mounts, and only once — the ref guard survives the
 * double-invoke React does in dev StrictMode. For pages that are static Astro markup
 * (product listing, category) and have no island of their own to hang a `track()` call on.
 * `track()` already no-ops when PostHog is absent or capture is off.
 */
export default function TrackEvent({ event, properties }: Props) {
  const fired = useRef(false);

  useEffect(() => {
    if (fired.current) return;
    fired.current = true;
    track(event, properties);
  }, [event, properties]);

  return null;
}
