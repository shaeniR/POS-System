import { useCallback, useEffect, useRef, useState } from 'react';

/**
 * Loads data when the component mounts (and whenever `deps` change).
 * `reload({ silent: true })` refreshes in the background without showing the loading state,
 * which is used for polling and for refreshing after an action.
 */
export function useFetch(loader, deps = []) {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const loaderRef = useRef(loader);
  loaderRef.current = loader;
  const latestRequest = useRef(0);

  const reload = useCallback(async ({ silent = false } = {}) => {
    const requestId = ++latestRequest.current;
    if (!silent) setLoading(true);
    try {
      const result = await loaderRef.current();
      // Ignore responses that arrive after a newer request was started
      if (requestId === latestRequest.current) {
        setData(result);
        setError(null);
      }
      return result;
    } catch (e) {
      // A failed background refresh keeps the data already on screen
      if (requestId === latestRequest.current && !silent) setError(e);
      return undefined;
    } finally {
      if (requestId === latestRequest.current && !silent) setLoading(false);
    }
  }, []);

  useEffect(() => {
    reload();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);

  return { data, setData, loading, error, reload };
}

/** Calls `callback` every `intervalMs` while the component is mounted. */
export function useInterval(callback, intervalMs, enabled = true) {
  const callbackRef = useRef(callback);
  callbackRef.current = callback;

  useEffect(() => {
    if (!enabled) return undefined;
    const id = setInterval(() => callbackRef.current(), intervalMs);
    return () => clearInterval(id);
  }, [intervalMs, enabled]);
}
