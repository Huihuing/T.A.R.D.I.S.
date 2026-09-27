import { API_URL } from './config';

let refreshInFlight: Promise<boolean> | null = null;

const EXPLICIT_LOGOUT_KEY = 'tardis:explicit-logout';
const LOGOUT_REQUEST_TIMEOUT_MS = 8_000;

function hasExplicitLogoutIntent(): boolean {
    return localStorage.getItem(EXPLICIT_LOGOUT_KEY) === '1';
}

function markExplicitLogout(): void {
    localStorage.setItem(EXPLICIT_LOGOUT_KEY, '1');
}

export function clearAuth(): void {
    sessionStorage.removeItem('token');
    localStorage.removeItem('token');
    localStorage.removeItem('username');
    localStorage.removeItem('needsPinSetup');
}

function decodeJwtPayload(token: string): { exp?: number } | null {
    try {
        const parts = token.split('.');
        if (parts.length !== 3) {
            return null;
        }

        const normalized = parts[1]
            .replace(/-/g, '+')
            .replace(/_/g, '/')
            .padEnd(Math.ceil(parts[1].length / 4) * 4, '=');

        return JSON.parse(atob(normalized));
    } catch {
        return null;
    }
}

function isTokenNearExpiry(
    token: string,
    thresholdMs = 60_000,
    nowMs = Date.now()
): boolean {
    const payload = decodeJwtPayload(token);
    if (!payload || typeof payload.exp !== 'number') {
        return true;
    }
    return payload.exp * 1000 <= nowMs + thresholdMs;
}

export function isTokenExpired(
    token: string,
    nowMs = Date.now()
): boolean {
    const payload = decodeJwtPayload(token);
    if (!payload || typeof payload.exp !== 'number') {
        return true;
    }

    return payload.exp * 1000 <= nowMs;
}

export function storeAccessToken(token: string): void {
    sessionStorage.setItem('token', token);
    localStorage.removeItem('token');
    // A successful interactive login or session refresh establishes a new
    // authenticated session, so an older explicit-logout guard no longer
    // applies.
    localStorage.removeItem(EXPLICIT_LOGOUT_KEY);
}

export function getStoredToken(): string | null {
    let token = sessionStorage.getItem('token');

    // One-time migration for users who logged in before access tokens
    // were moved out of persistent localStorage.
    if (!token) {
        const legacyToken = localStorage.getItem('token');
        if (legacyToken) {
            token = legacyToken;
            sessionStorage.setItem('token', legacyToken);
            localStorage.removeItem('token');
        }
    }

    if (!token) {
        return null;
    }

    if (isTokenExpired(token)) {
        sessionStorage.removeItem('token');
        return null;
    }

    return token;
}

export function getAuthHeaders(
    includeJson = true
): Record<string, string> {
    const headers: Record<string, string> = {};
    if (includeJson) {
        headers['Content-Type'] = 'application/json';
    }

    const token = getStoredToken();
    if (token) {
        headers.Authorization = `Bearer ${token}`;
    }

    return headers;
}

async function performAccessTokenRefresh(): Promise<boolean> {
    // Explicit logout is a user decision. If the backend was asleep and the
    // logout POST could not clear its HttpOnly refresh cookie, never use that
    // stale cookie to silently restore the session later.
    if (hasExplicitLogoutIntent()) {
        return false;
    }

    try {
        const res = await fetch(`${API_URL}/api/auth/refresh`, {
            method: 'POST',
            credentials: 'include'
        });
        const data = await res.json().catch(() => ({}));

        if (res.ok && data.token && data.username) {
            storeAccessToken(data.token);
            localStorage.setItem('username', data.username);

            if (data.needsPinSetup) {
                localStorage.setItem('needsPinSetup', 'true');
            } else {
                localStorage.removeItem('needsPinSetup');
            }

            return true;
        }

        // A transient server/rate-limit failure must not sign the user out.
        // Only an authentication failure means the refresh session is unusable.
        if (res.status === 401) {
            clearAuth();
        }
        return false;
    } catch {
        return false;
    }
}

export async function refreshAccessToken(): Promise<boolean> {
    if (hasExplicitLogoutIntent()) {
        return false;
    }

    if (refreshInFlight) {
        return refreshInFlight;
    }

    const run = async () => {
        // The refresh cookie is shared by tabs while access tokens live in
        // per-tab sessionStorage. Serialize cookie rotation across tabs so one
        // stale request cannot race another tab's successful rotation.
        if ('locks' in navigator && navigator.locks) {
            return navigator.locks.request(
                'tardis-refresh-token',
                { mode: 'exclusive' },
                () => performAccessTokenRefresh()
            );
        }

        return performAccessTokenRefresh();
    };

    refreshInFlight = run().finally(() => {
        refreshInFlight = null;
    });

    return refreshInFlight;
}

export async function authFetch(
    input: RequestInfo | URL,
    init: RequestInit = {}
): Promise<Response> {
    let token = getStoredToken();

    if (!token || isTokenNearExpiry(token)) {
        await refreshAccessToken();
        token = getStoredToken();
    }

    const headers = new Headers(init.headers);
    if (token) {
        headers.set('Authorization', `Bearer ${token}`);
    }

    let response = await fetch(input, {
        ...init,
        headers
    });

    // If the token expired between request creation and server validation,
    // refresh once and retry. Business-level 401 responses are not retried
    // while the current JWT is still valid.
    if (response.status === 401 && (!token || isTokenExpired(token))) {
        const refreshed = await refreshAccessToken();
        const nextToken = getStoredToken();

        if (refreshed && nextToken) {
            const retryHeaders = new Headers(init.headers);
            retryHeaders.set(
                'Authorization',
                `Bearer ${nextToken}`
            );
            response = await fetch(input, {
                ...init,
                headers: retryHeaders
            });
        }
    }

    return response;
}

export async function logoutSession(): Promise<void> {
    // Mark the intent before touching the network so App's backend-ready/focus
    // recovery cannot race this logout and silently restore the same refresh
    // session while Render is still waking.
    markExplicitLogout();
    clearAuth();

    const controller = new AbortController();
    const timeout = window.setTimeout(
        () => controller.abort(),
        LOGOUT_REQUEST_TIMEOUT_MS
    );

    try {
        await fetch(`${API_URL}/api/auth/logout`, {
            method: 'POST',
            credentials: 'include',
            signal: controller.signal
        });
    } catch {
        // Local logout remains authoritative. The explicit-logout marker blocks
        // any stale server refresh cookie from re-authenticating this browser.
    } finally {
        window.clearTimeout(timeout);
    }
}
