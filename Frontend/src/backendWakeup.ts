import { getStoredToken, refreshAccessToken } from './auth';
import { API_URL, WS_URL } from './config';

export const BACKEND_READY_EVENT = 'tardis:backend-ready';

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
const BACKEND_RETRY_TIMEOUT_MS = 4 * 60_000;
const RETRYABLE_GATEWAY_STATUSES = new Set([502, 503, 504]);
const nativeFetch = window.fetch.bind(window);

let snapshot: BackendWakeSnapshot = {
  status: 'checking',
  attempts: 0,
  startedAt: Date.now()
};
let wakePromise: Promise<void> | null = null;
let backendAwareFetchInstalled = false;
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
    const response = await nativeFetch(READINESS_URL, {
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
      const readyAt = Date.now();
      publish({
        status: 'ready',
        readyAt,
        lastCheckedAt: readyAt
      });
      window.dispatchEvent(new CustomEvent(BACKEND_READY_EVENT, {
        detail: {
          readyAt,
          attempts: snapshot.attempts
        }
      }));
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

function requestMethod(
  input: RequestInfo | URL,
  init?: RequestInit
): string {
  if (init?.method) return init.method.toUpperCase();
  if (input instanceof Request) return input.method.toUpperCase();
  return 'GET';
}

function isBackendApiRead(
  input: RequestInfo | URL,
  init?: RequestInit
): boolean {
  const method = requestMethod(input, init);
  if (method !== 'GET' && method !== 'HEAD') return false;

  try {
    const rawUrl = input instanceof Request ? input.url : input.toString();
    const url = new URL(rawUrl, window.location.href);
    const apiBase = new URL(API_URL || window.location.origin, window.location.href);

    return url.origin === apiBase.origin
      && url.pathname.startsWith('/api/');
  } catch {
    return false;
  }
}

function waitForBackendReady(
  timeoutMs = BACKEND_RETRY_TIMEOUT_MS
): Promise<boolean> {
  if (snapshot.status === 'ready') return Promise.resolve(true);

  return new Promise(resolve => {
    let settled = false;
    let timeout = 0;
    let unsubscribe: () => void = () => {};

    const finish = (ready: boolean) => {
      if (settled) return;
      settled = true;
      if (timeout) window.clearTimeout(timeout);
      unsubscribe();
      resolve(ready);
    };

    unsubscribe = subscribeBackendWakeup(current => {
      if (current.status === 'ready') finish(true);
    });
    timeout = window.setTimeout(() => finish(false), timeoutMs);

    // subscribeBackendWakeup() immediately publishes the current snapshot.
    // If that synchronous callback already settled the promise, clean up the
    // listener returned after the callback finished.
    if (settled) unsubscribe();
  });
}

async function retryAfterBackendWake(
  input: RequestInfo | URL,
  init?: RequestInit
): Promise<Response | null> {
  void startBackendWakeup(true);
  const ready = await waitForBackendReady();
  if (!ready) return null;
  return nativeFetch(input, init);
}

function latestAuthHeaders(
  input: RequestInfo | URL,
  init?: RequestInit
): Headers | null {
  const token = getStoredToken();
  if (!token) return null;

  const headers = new Headers(
    input instanceof Request ? input.headers : undefined
  );
  if (init?.headers) {
    new Headers(init.headers).forEach((value, key) => {
      headers.set(key, value);
    });
  }
  headers.set('Authorization', `Bearer ${token}`);
  return headers;
}

async function retryAfterAuthRefresh(
  input: RequestInfo | URL,
  init?: RequestInit
): Promise<Response | null> {
  const refreshed = await refreshAccessToken();
  if (!refreshed) return null;

  const headers = latestAuthHeaders(input, init);
  if (!headers) return null;

  return nativeFetch(input, {
    ...init,
    headers
  });
}

async function recoverBackendRead(
  response: Response,
  input: RequestInfo | URL,
  init?: RequestInit
): Promise<Response> {
  if (response.status === 401) {
    return await retryAfterAuthRefresh(input, init) ?? response;
  }

  if (!RETRYABLE_GATEWAY_STATUSES.has(response.status)) {
    return response;
  }

  const retried = await retryAfterBackendWake(input, init);
  if (!retried) return response;

  if (retried.status === 401) {
    return await retryAfterAuthRefresh(input, init) ?? retried;
  }

  return retried;
}

export function installBackendAwareFetch() {
  if (!import.meta.env.PROD || backendAwareFetchInstalled) return;
  backendAwareFetchInstalled = true;

  window.fetch = async (
    input: RequestInfo | URL,
    init?: RequestInit
  ): Promise<Response> => {
    if (!isBackendApiRead(input, init)) {
      return nativeFetch(input, init);
    }

    try {
      const response = await nativeFetch(input, init);
      return recoverBackendRead(response, input, init);
    } catch (error) {
      const retried = await retryAfterBackendWake(input, init);
      if (!retried) throw error;

      if (retried.status === 401) {
        return await retryAfterAuthRefresh(input, init) ?? retried;
      }

      return retried;
    }
  };
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
