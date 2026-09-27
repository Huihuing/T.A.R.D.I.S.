import { useEffect, useRef } from 'react';
import { BACKEND_READY_EVENT } from './backendWakeup';

type BackendReadyRetry = () => void | Promise<void>;

export function useBackendReadyRetry(retry: BackendReadyRetry) {
  const retryRef = useRef(retry);
  retryRef.current = retry;

  useEffect(() => {
    const handleBackendReady = () => {
      void retryRef.current();
    };

    window.addEventListener(BACKEND_READY_EVENT, handleBackendReady);
    return () => {
      window.removeEventListener(BACKEND_READY_EVENT, handleBackendReady);
    };
  }, []);
}
