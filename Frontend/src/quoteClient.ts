export interface MarketQuote {
    c: number;
    d: number;
    dp: number;
    t?: number;
    h?: number;
    l?: number;
    o?: number;
    pc?: number;
    stale?: boolean;
    fetchedAt?: number;
    receivedAt: number;
}

export class QuoteError extends Error {
    retryAt: number;

    constructor(message: string, retryAt = 0) {
        super(message);
        this.name = 'QuoteError';
        this.retryAt = retryAt;
    }
}

const abortError = () => new DOMException('시세 조회가 취소되었습니다.', 'AbortError');

// Display quotes only. Trade execution always obtains its own validated price
// on the server; this cache must never determine an execution price.
export function createQuoteClient(baseUrl: string, options: {
    fetcher?: typeof fetch;
    now?: () => number;
    ttlMs?: number;
    concurrency?: number;
} = {}) {
    const fetcher = options.fetcher ?? ((...args) => fetch(...args));
    const now = options.now ?? Date.now;
    const ttlMs = options.ttlMs ?? 30_000;
    const concurrency = options.concurrency ?? 4;
    const cache = new Map<string, MarketQuote>();
    const failures = new Map<string, { count: number; retryAt: number }>();
    const pending = new Map<string, { controller: AbortController; promise: Promise<MarketQuote>; subscribers: number }>();
    const queue: (() => void)[] = [];
    let active = 0;
    let rateLimitedUntil = 0;

    const acquire = (signal: AbortSignal) => new Promise<void>((resolve, reject) => {
        const start = () => {
            signal.removeEventListener('abort', cancel);
            if (signal.aborted) { reject(abortError()); return; }
            active += 1;
            resolve();
        };
        const cancel = () => {
            const index = queue.indexOf(start);
            if (index >= 0) queue.splice(index, 1);
            reject(abortError());
        };
        if (signal.aborted) { reject(abortError()); return; }
        if (active < concurrency) start();
        else { queue.push(start); signal.addEventListener('abort', cancel, { once: true }); }
    });

    const load = async (symbol: string, signal: AbortSignal) => {
        await acquire(signal);
        try {
            if (now() < rateLimitedUntil) throw new QuoteError('시세 요청 한도 대기 중', rateLimitedUntil);
            const response = await fetcher(`${baseUrl}/api/stock/quote?symbol=${encodeURIComponent(symbol)}`, { signal });
            if (!response.ok) {
                if (response.status === 429) {
                    const header = response.headers.get('Retry-After');
                    const seconds = header === null ? NaN : Number(header);
                    const retryAt = Number.isFinite(seconds)
                        ? now() + Math.max(1, seconds) * 1000
                        : Math.max(now() + 30_000, Date.parse(header ?? '') || 0);
                    rateLimitedUntil = retryAt;
                    throw new QuoteError('시세 요청 한도 초과', retryAt);
                }
                throw new QuoteError(`시세 조회 실패 (${response.status})`);
            }
            const data = await response.json();
            if (typeof data?.c !== 'number' || !Number.isFinite(data.c) || data.c <= 0
                || typeof data.d !== 'number' || !Number.isFinite(data.d)
                || typeof data.dp !== 'number' || !Number.isFinite(data.dp)) {
                throw new QuoteError('유효한 시세가 없습니다.');
            }
            const quote: MarketQuote = { c: data.c, d: data.d, dp: data.dp, t: data.t, h: data.h, l: data.l, o: data.o, pc: data.pc, stale: data.stale === true, fetchedAt: data.fetchedAt, receivedAt: now() };
            cache.set(symbol, quote);
            failures.delete(symbol);
            return quote;
        } catch (error) {
            if (signal.aborted) throw abortError();
            const count = (failures.get(symbol)?.count ?? 0) + 1;
            const retryAt = Math.max(now() + Math.min(300_000, 30_000 * 2 ** (count - 1)), error instanceof QuoteError ? error.retryAt : 0);
            failures.set(symbol, { count, retryAt });
            throw new QuoteError(error instanceof Error ? error.message : '시세 연결 실패', retryAt);
        } finally {
            active -= 1;
            queue.shift()?.();
        }
    };

    return {
        get(symbolInput: string, signal?: AbortSignal): Promise<MarketQuote> {
            const symbol = symbolInput.trim().toUpperCase();
            if (signal?.aborted) return Promise.reject(abortError());
            const cached = cache.get(symbol);
            if (cached && now() - cached.receivedAt < ttlMs) return Promise.resolve(cached);
            const retryAt = Math.max(rateLimitedUntil, failures.get(symbol)?.retryAt ?? 0);
            if (now() < retryAt) return Promise.reject(new QuoteError('시세 재조회 대기 중', retryAt));
            let entry = pending.get(symbol);
            if (!entry || entry.controller.signal.aborted) {
                const controller = new AbortController();
                entry = { controller, subscribers: 0, promise: load(symbol, controller.signal) };
                const current = entry;
                entry.promise = entry.promise.finally(() => {
                    if (pending.get(symbol) === current) pending.delete(symbol);
                });
                pending.set(symbol, entry);
            }
            const current = entry;
            current.subscribers += 1;
            return new Promise((resolve, reject) => {
                let done = false;
                const finish = () => {
                    if (done) return false;
                    done = true;
                    signal?.removeEventListener('abort', cancel);
                    current.subscribers -= 1;
                    return true;
                };
                const cancel = () => {
                    if (!finish()) return;
                    reject(abortError());
                    if (current.subscribers === 0) current.controller.abort();
                };
                signal?.addEventListener('abort', cancel, { once: true });
                current.promise.then(value => { if (finish()) resolve(value); }, error => { if (finish()) reject(error); });
            });
        }
    };
}
