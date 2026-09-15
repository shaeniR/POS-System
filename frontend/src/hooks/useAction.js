import { useCallback, useRef, useState } from 'react';

/**
 * Runs a user action (save, pay, checkout...) while tracking a busy flag and the last error.
 * A second call while one is still running is ignored, which prevents accidental double submissions.
 *
 * `run` resolves to { ok: true, data } or { ok: false, error }.
 */
export function useAction() {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const running = useRef(false);

  const run = useCallback(async (action) => {
    if (running.current) return { ok: false, ignored: true };
    running.current = true;
    setBusy(true);
    setError(null);
    try {
      return { ok: true, data: await action() };
    } catch (e) {
      setError(e);
      return { ok: false, error: e };
    } finally {
      running.current = false;
      setBusy(false);
    }
  }, []);

  return { busy, error, setError, run };
}
