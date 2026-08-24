import { useState } from "react";
import type { AuthTokenResponse } from "./api";

/**
 * The loading/error/try-catch-finally wrapper shared by every password-login/register submit and
 * every Google Sign-In callback: run one auth call, hand the result to `finishAuth` on success, or
 * show a fixed message on failure. Pulled out mainly to keep that shape out of each component's own
 * Cognitive Complexity count -- Sonar's TS engine folds a nested function's control flow into its
 * enclosing component, so three copies of this try/catch were also three separate S3776 hits.
 */
export function useAuthAction(finishAuth: (data: AuthTokenResponse) => void) {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  async function run(action: () => Promise<AuthTokenResponse>, fallbackMessage: string) {
    setLoading(true);
    setError("");
    try {
      const data = await action();
      finishAuth(data);
    } catch {
      setError(fallbackMessage);
    } finally {
      setLoading(false);
    }
  }

  return { loading, error, setError, run };
}
