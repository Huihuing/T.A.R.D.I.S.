import { API_URL } from './config';
import { createQuoteClient } from './quoteClient';

export const marketQuotes = createQuoteClient(API_URL);

export function quoteTime(quote?: { t?: number; fetchedAt?: number; receivedAt: number }): string {
    if (!quote) return '시세 미수신';
    const timestamp = Number.isFinite(quote.t) && quote.t! > 0 ? quote.t! * 1000 : quote.fetchedAt ?? quote.receivedAt;
    return new Date(timestamp).toLocaleString('ko-KR', { timeZone: 'Asia/Seoul' });
}
