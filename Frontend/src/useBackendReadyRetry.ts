import { useEffect, useRef } from 'react';
import { BACKEND_READY_EVENT } from './backendWakeup';

type BackendReadyRetry = () => void | Promise<void>;

export function useBackendReadyRetry(retry: BackendReadyRetry) {
  const retryRef = useRef(retry);
  retryRef.current = retry;

  useEffect(() => {
    // Production read recovery is centralized in installBackendAwareFetch(),
    // which is installed before React renders. Keeping this page-level
    // readiness listener active there would replay the same GET twice when
    // Render finishes waking. Retain the hook only as a development fallback,
    // where the production fetch wrapper is intentionally not installed.
    if (import.meta.env.PROD) {
      return;
    }

    const handleBackendReady = () => {
      void retryRef.current();
    };

    window.addEventListener(BACKEND_READY_EVENT, handleBackendReady);
    return () => {
      window.removeEventListener(BACKEND_READY_EVENT, handleBackendReady);
    };
  }, []);
}
