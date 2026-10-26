const PREFIX = 'tardis:mutation-intent:';

// Keep only a digest and a random key in tab storage, never request bodies
// (a transfer body contains a PIN). A lost response can then be retried safely
// even after navigation or a reload in the same tab.
export async function mutationIntent(endpoint: string, body: string) {
    const identity = `${localStorage.getItem('username') ?? ''}:${endpoint}:${body}`;
    const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(identity));
    const fingerprint = Array.from(new Uint8Array(digest), value => value.toString(16).padStart(2, '0')).join('');
    const storageKey = PREFIX + endpoint + ':' + fingerprint;
    let saved: { fingerprint: string; key: string } | undefined;
    try { saved = JSON.parse(sessionStorage.getItem(storageKey) ?? 'null') ?? undefined; } catch { /* Replace invalid local state. */ }
    const key = saved?.fingerprint === fingerprint ? saved.key : crypto.randomUUID();
    sessionStorage.setItem(storageKey, JSON.stringify({ fingerprint, key }));
    return {
        key,
        settle(response: Response) {
            // A server error or transport failure can follow a committed write.
            // Retain that intent until the server provides a definitive result.
            if (response.status < 500 && response.status !== 408 && response.status !== 429) {
                const latest = sessionStorage.getItem(storageKey);
                if (latest && JSON.parse(latest).key === key) sessionStorage.removeItem(storageKey);
            }
        }
    };
}
