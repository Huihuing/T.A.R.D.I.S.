import { useEffect, useState } from 'react';
import { marketQuotes } from './marketData';
import { QuoteError } from './quoteClient';
import type { MarketQuote } from './quoteClient';

export function useMarketQuotes(symbols: string[]) {
    const key = [...new Set(symbols)].sort().join(',');
    const [quotes, setQuotes] = useState<Record<string, MarketQuote>>({});
    const [errors, setErrors] = useState<Record<string, string>>({});
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        let disposed = false;
        let controller: AbortController | undefined;
        let timer: ReturnType<typeof setTimeout> | undefined;
        let running = false;
        const poll = async () => {
            if (disposed || document.hidden || running) return;
            running = true;
            controller = new AbortController();
            const signal = controller.signal;
            let earliestRetry = Number.POSITIVE_INFINITY;
            let successes = 0;
            const results = await Promise.all(key.split(',').filter(Boolean).map(async symbol => {
                try {
                    const quote = await marketQuotes.get(symbol, signal);
                    successes += 1;
                    return { symbol, quote, error: quote.stale ? '제공사 갱신 실패 · 이전 시세' : undefined };
                }
                catch (error) {
                    if (error instanceof QuoteError) earliestRetry = Math.min(earliestRetry, error.retryAt - Date.now());
                    return { symbol, error: error instanceof Error ? error.message : '시세 조회 실패' };
                }
            }));
            running = false;
            if (disposed) return;
            if (signal.aborted) {
                if (!document.hidden) void poll();
                return;
            }
            if (!signal.aborted) {
                setQuotes(previous => {
                    const next = { ...previous };
                    for (const result of results) if (result.quote) next[result.symbol] = result.quote;
                    return next;
                });
                setErrors(Object.fromEntries(results.filter(result => result.error).map(result => [result.symbol, result.error!])));
                setLoading(false);
            }
            const nextDelay = successes > 0 ? 30_000 : Math.max(30_000, Number.isFinite(earliestRetry) ? earliestRetry : 30_000);
            if (!document.hidden) timer = setTimeout(poll, nextDelay);
        };
        const onVisibility = () => {
            clearTimeout(timer);
            if (document.hidden) controller?.abort();
            else void poll();
        };
        document.addEventListener('visibilitychange', onVisibility);
        void poll();
        return () => {
            disposed = true;
            clearTimeout(timer);
            controller?.abort();
            document.removeEventListener('visibilitychange', onVisibility);
        };
    }, [key]);

    return { quotes, errors, loading };
}
