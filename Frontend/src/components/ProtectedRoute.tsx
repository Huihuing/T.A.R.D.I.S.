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

    const verifySession = async (backendReady = false) => {
      if (getStoredToken()) {
        if (active) setState('allowed');
        return;
      }

      const restored = await refreshAccessToken();
      if (!active) return;

      if (restored && getStoredToken()) {
        setState('allowed');
        return;
      }

      const wakeSnapshot = getBackendWakeSnapshot();
      if (backendReady || wakeSnapshot.status === 'ready') {
        setState('denied');
      } else {
        setState('checking');
      }
    };

    // Keep protected views mounted behind this gate while Render is waking.
    // A transient refresh failure during cold start must not be treated as an
    // expired login session. App-level refresh calls share the same single-
    // flight promise, so this does not create duplicate cookie rotations.
    void startBackendWakeup();
    void verifySession();

    const handleBackendReady = () => {
      void verifySession(true);
    };

    window.addEventListener(BACKEND_READY_EVENT, handleBackendReady);
    return () => {
      active = false;
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
