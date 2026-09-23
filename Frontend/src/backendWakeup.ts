import { WS_URL } from './config';

export type BackendWakeStatus =
  | 'checking'
  | 'waking'
  | 'ready'
  | 'slow';

export type BackendWakeSnapshot = {
  status: BackendWakeStatus;
  attempts: number;
  startedAt: number;
  readyAt?: number;
  lastCheckedAt?: number;
};

type Listener = (snapshot: BackendWakeSnapshot) => void;

const READINESS_URL = `${WS_URL}/actuator/health/readiness`;
const REQUEST_TIMEOUT_MS = 8_000;
const RETRY_DELAY_MS = 4_000;
const SLOW_RETRY_DELAY_MS = 8_000;
const SLOW_AFTER_MS = 180_000;
const RECHECK_AFTER_READY_MS = 10 * 60_000;

let snapshot: BackendWakeSnapshot = {
  status: 'checking',
  attempts: 0,
  startedAt: Date.now()
};
let wakePromise: Promise<void> | null = null;
const listeners = new Set<Listener>();

function publish(next: Partial<BackendWakeSnapshot>) {
  snapshot = { ...snapshot, ...next };
  listeners.forEach(listener => listener(snapshot));
}

function wait(ms: number) {
  return new Promise<void>(resolve => window.setTimeout(resolve, ms));
}

async function probeReadiness(): Promise<boolean> {
  const controller = new AbortController();
  const timeout = window.setTimeout(
    () => controller.abort(),
    REQUEST_TIMEOUT_MS
  );

  try {
    const response = await fetch(READINESS_URL, {
      method: 'GET',
      cache: 'no-store',
      signal: controller.signal
    });

    if (!response.ok) return false;

    const payload = await response.json().catch(() => ({}));
    return payload?.status === 'UP';
  } catch {
    return false;
  } finally {
    window.clearTimeout(timeout);
  }
}

async function runWakeLoop() {
  while (true) {
    publish({
      attempts: snapshot.attempts + 1,
      lastCheckedAt: Date.now()
    });

    if (await probeReadiness()) {
      publish({
        status: 'ready',
        readyAt: Date.now(),
        lastCheckedAt: Date.now()
      });
      return;
    }

    const elapsed = Date.now() - snapshot.startedAt;
    const slow = elapsed >= SLOW_AFTER_MS;
    publish({ status: slow ? 'slow' : 'waking' });
    await wait(slow ? SLOW_RETRY_DELAY_MS : RETRY_DELAY_MS);
  }
}

export function startBackendWakeup(force = false): Promise<void> {
  if (wakePromise) return wakePromise;

  const now = Date.now();
  if (
    !force
    && snapshot.status === 'ready'
    && snapshot.readyAt
    && now - snapshot.readyAt < RECHECK_AFTER_READY_MS
  ) {
    return Promise.resolve();
  }

  snapshot = {
    status: 'checking',
    attempts: 0,
    startedAt: now
  };
  listeners.forEach(listener => listener(snapshot));

  wakePromise = runWakeLoop().finally(() => {
    wakePromise = null;
  });

  return wakePromise;
}

export function subscribeBackendWakeup(listener: Listener) {
  listeners.add(listener);
  listener(snapshot);
  return () => listeners.delete(listener);
}

export function getBackendWakeSnapshot() {
  return snapshot;
}

export function recheckBackendOnFocus() {
  const current = getBackendWakeSnapshot();
  const lastReadyAt = current.readyAt || 0;

  if (
    current.status !== 'ready'
    || Date.now() - lastReadyAt >= RECHECK_AFTER_READY_MS
  ) {
    void startBackendWakeup(true);
  }
}
