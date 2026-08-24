import { useEffect } from "react";
import type { RefObject } from "react";

interface GoogleSignInOptions {
  readonly clientId: string | undefined;
  readonly buttonRef: RefObject<HTMLDivElement | null>;
  readonly onCredential: (credential: string) => void | Promise<void>;
  readonly buttonText?: "continue_with" | "signup_with";
  readonly buttonWidth?: number;
}

/**
 * Wires up Google Identity Services against the container div. The script tag is rendered
 * elsewhere (see the astro layout) and may finish loading before or after this effect runs, so
 * both orders have to be handled: call in immediately if `window.google` is already there,
 * otherwise wait for the script's own `load` event.
 */
export function useGoogleSignIn({
  clientId,
  buttonRef,
  onCredential,
  buttonText = "continue_with",
  buttonWidth = 280,
}: GoogleSignInOptions) {
  useEffect(() => {
    if (!clientId) return;

    function initGoogle() {
      const g = (window as any).google;
      if (!g?.accounts?.id) return;
      g.accounts.id.initialize({
        client_id: clientId,
        callback: (response: { credential: string }) => onCredential(response.credential),
      });
      if (buttonRef.current) {
        g.accounts.id.renderButton(buttonRef.current, {
          type: "standard",
          theme: "outline",
          size: "large",
          text: buttonText,
          width: buttonRef.current.offsetWidth || buttonWidth,
          logo_alignment: "left",
        });
      }
    }

    if ((window as any).google?.accounts?.id) {
      initGoogle();
      return undefined;
    }
    const script = document.querySelector('script[src*="accounts.google.com/gsi/client"]');
    script?.addEventListener("load", initGoogle);
    return () => script?.removeEventListener("load", initGoogle);
  }, [clientId, buttonRef, onCredential, buttonText, buttonWidth]);
}
