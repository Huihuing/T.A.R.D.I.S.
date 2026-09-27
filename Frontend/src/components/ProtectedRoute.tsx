import { useEffect, useState, type ReactNode } from 'react';
import { Navigate } from 'react-router-dom';
import {
  getStoredToken,
  refreshAccessToken
} from '../auth';
import {
  BACKEND_READY_EVENT,
  getBackendWakeSnapshot,
  startBackendWakeup
} from '../backendWakeup';

type SessionGateState = 'checking' | 'allowed' | 'denied';

const TRANSIENT_REFRESH_RETRY_MS = 4_000;

function hasStoredSessionIdentity() {
  const username = localStorage.getItem('username');
  return Boolean(username && username !== 'Guest');
}

export default function ProtectedRoute({ children }: { children: ReactNode }) {
  const [state, setState] = useState<SessionGateState>(() =>
    getStoredToken() ? 'allowed' : 'checking'
  );

  useEffect(() => {
    if (getStoredToken()) {
      setState('allowed');
      return;
    }

    let active = true;
    let retryTimer: number | null = null;

    const clearRetry = () => {
      if (retryTimer !== null) {
        window.clearTimeout(retryTimer);
        retryTimer = null;
      }
    };

    const scheduleTransientRetry = () => {
      if (!active || retryTimer !== null) return;

      retryTimer = window.setTimeout(() => {
        retryTimer = null;
        void verifySession(true);
      }, TRANSIENT_REFRESH_RETRY_MS);
    };

    const verifySession = async (backendReady = false) => {
      if (getStoredToken()) {
        clearRetry();
        if (active) setState('allowed');
        return;
      }

      const restored = await refreshAccessToken();
      if (!active) return;

      if (restored && getStoredToken()) {
        clearRetry();
        setState('allowed');
        return;
      }

      const wakeSnapshot = getBackendWakeSnapshot();
      const ready = backendReady || wakeSnapshot.status === 'ready';

      if (!ready) {
        setState('checking');
        return;
      }

      // refreshAccessToken() clears username only on an explicit 401. A 429,
      // 5xx or network failure intentionally leaves the local identity intact.
      // Keep the protected view behind the gate and retry those transient
      // failures instead of misclassifying them as an expired login session.
      if (hasStoredSessionIdentity()) {
        setState('checking');
        scheduleTransientRetry();
        return;
      }

      clearRetry();
      setState('denied');
    };

    // Keep protected views mounted behind this gate while Render is waking.
    // App-level refresh calls share the same single-flight promise, so this
    // does not create duplicate refresh-cookie rotations.
    void startBackendWakeup();
    void verifySession();

    const handleBackendReady = () => {
      void verifySession(true);
    };

    window.addEventListener(BACKEND_READY_EVENT, handleBackendReady);
    return () => {
      active = false;
      clearRetry();
      window.removeEventListener(
        BACKEND_READY_EVENT,
        handleBackendReady
      );
    };
  }, []);

  if (state === 'denied') {
    return <Navigate to="/login" replace />;
  }

  if (state === 'checking') {
    return (
      <div className="min-h-screen bg-[#0b1120] text-slate-400 flex items-center justify-center p-6 text-center">
        로그인 세션과 백엔드 연결을 확인하는 중...
      </div>
    );
  }

  return <>{children}</>;
}
