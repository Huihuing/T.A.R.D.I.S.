import { API_URL } from './config';

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
}

export async function logoutSession(): Promise<void> {
    try {
        await fetch(`${API_URL}/api/auth/logout`, {
            method: 'POST',
            credentials: 'include'
        });
    } catch {
        // Local auth state is still cleared even if the server is unreachable.
    } finally {
        clearAuth();
    }
}
