import { useEffect, type ReactNode } from 'react';
import { useLocation } from 'react-router-dom';
import { nativeAuthPath, pulseAuthDestination, type PulseAuthMode } from './pulse-auth';

/** Uses the existing auth routes, not a second floating login control. */
export default function PulseAuthEntry({ mode, children }: { mode: PulseAuthMode; children: ReactNode }) {
  const location = useLocation();
  const destination = pulseAuthDestination({
    world: 'tardis', mode,
    enabled: import.meta.env.VITE_PULSE_AUTH_ENABLED === 'true',
    portalUrl: import.meta.env.VITE_PULSE_LAB_URL,
    allowLocal: import.meta.env.DEV,
    search: location.search, hash: location.hash,
  });
  useEffect(() => { if (destination) window.location.replace(destination); }, [destination]);
  if (!destination) return children;
  return (
    <main style={{ minHeight: '100dvh', padding: 'clamp(24px,6vw,80px)', background: '#0b1120', color: '#e2e8f0' }}>
      <h1>PULSE//ID {mode === 'signup' ? '회원가입' : '로그인'}</h1>
      <p role="status">중앙 계정 페이지로 이동합니다.</p>
      <p><a href={destination}>중앙 계정 페이지 열기</a></p>
      <p><a href={nativeAuthPath('tardis', mode)}>기존 T.A.R.D.I.S. 계정 화면 사용</a></p>
    </main>
  );
}
