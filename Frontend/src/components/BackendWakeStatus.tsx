import { useEffect, useMemo, useState } from 'react';
import {
  CheckCircle2,
  Loader2,
  Server,
  TriangleAlert
} from 'lucide-react';
import {
  getBackendWakeSnapshot,
  recheckBackendOnFocus,
  startBackendWakeup,
  subscribeBackendWakeup,
  type BackendWakeSnapshot
} from '../backendWakeup';

function formatElapsed(ms: number) {
  const totalSeconds = Math.max(0, Math.floor(ms / 1000));
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}:${String(seconds).padStart(2, '0')}`;
}

export default function BackendWakeStatus() {
  const [snapshot, setSnapshot] = useState<BackendWakeSnapshot>(
    getBackendWakeSnapshot()
  );
  const [now, setNow] = useState(Date.now());

  useEffect(() => {
    const unsubscribe = subscribeBackendWakeup(setSnapshot);
    void startBackendWakeup();

    const timer = window.setInterval(() => {
      setNow(Date.now());
    }, 1_000);

    const handleFocus = () => {
      recheckBackendOnFocus();
    };
    window.addEventListener('focus', handleFocus);

    return () => {
      unsubscribe();
      window.clearInterval(timer);
      window.removeEventListener('focus', handleFocus);
    };
  }, []);

  const elapsed = useMemo(
    () => formatElapsed(now - snapshot.startedAt),
    [now, snapshot.startedAt]
  );

  const readyVisible = snapshot.status === 'ready'
    && snapshot.readyAt
    && now - snapshot.readyAt < 3_500;

  if (snapshot.status === 'ready' && !readyVisible) {
    return null;
  }

  const slow = snapshot.status === 'slow';
  const ready = snapshot.status === 'ready';

  return (
    <div className="pointer-events-none fixed left-1/2 top-3 z-[300] w-[calc(100%-1.5rem)] max-w-xl -translate-x-1/2 px-1 sm:top-4">
      <div
        className={
          'pointer-events-auto flex items-start gap-3 rounded-2xl border px-4 py-3 shadow-2xl backdrop-blur-xl '
          + (ready
            ? 'border-emerald-400/30 bg-emerald-950/90 text-emerald-100'
            : slow
              ? 'border-amber-400/30 bg-amber-950/90 text-amber-100'
              : 'border-sky-400/20 bg-slate-950/95 text-slate-100')
        }
        role="status"
        aria-live="polite"
      >
        <div className="mt-0.5 shrink-0">
          {ready ? (
            <CheckCircle2 className="h-5 w-5 text-emerald-300" />
          ) : slow ? (
            <TriangleAlert className="h-5 w-5 text-amber-300" />
          ) : (
            <Loader2 className="h-5 w-5 animate-spin text-sky-300" />
          )}
        </div>

        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
            <span className="font-black">
              {ready
                ? '백엔드 서버 연결 완료'
                : slow
                  ? '백엔드 서버 연결이 지연되고 있습니다'
                  : '백엔드 서버를 시작하는 중입니다'}
            </span>
            {!ready && (
              <span className="rounded-full bg-white/5 px-2 py-0.5 text-xs font-bold text-slate-300">
                {elapsed}
              </span>
            )}
          </div>

          <p className="mt-1 text-xs leading-relaxed text-slate-300">
            {ready
              ? 'API 연결이 준비되었습니다. 화면 데이터가 정상적으로 갱신됩니다.'
              : slow
                ? '무료 서버가 깨어나는 데 평소보다 오래 걸리고 있습니다. 자동으로 계속 확인합니다.'
                : '무료 서버가 잠들어 있었다면 수 분 걸릴 수 있습니다. 화면은 그대로 사용할 수 있고 준비되면 자동 연결됩니다.'}
          </p>

          {!ready && snapshot.attempts > 0 && (
            <div className="mt-2 flex items-center gap-1.5 text-[11px] text-slate-500">
              <Server className="h-3.5 w-3.5" />
              readiness 자동 확인 {snapshot.attempts}회
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
