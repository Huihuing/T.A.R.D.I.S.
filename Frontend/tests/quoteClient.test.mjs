import test from 'node:test';
import assert from 'node:assert/strict';
import { createQuoteClient, QuoteError } from '../src/quoteClient.ts';

const quoteResponse = (c = 100) => Response.json({ c, d: 1, dp: 1, t: 1000 });
const tick = () => new Promise(resolve => setImmediate(resolve));

test('deduplicates simultaneous consumers and reuses display quotes within the 30 second TTL', async () => {
    let now = 1000;
    let requests = 0;
    const client = createQuoteClient('', { now: () => now, fetcher: async () => { requests++; return quoteResponse(); } });
    await Promise.all(Array.from({ length: 12 }, () => client.get('aapl')));
    assert.equal(requests, 1);
    now += 29_999;
    await client.get('AAPL');
    assert.equal(requests, 1);
    now++;
    await client.get('AAPL');
    assert.equal(requests, 2);
});

test('limits outgoing quote requests to four at a time', async () => {
    let active = 0;
    let maximum = 0;
    const release = [];
    const client = createQuoteClient('', { fetcher: async () => {
        active++;
        maximum = Math.max(maximum, active);
        await new Promise(resolve => release.push(resolve));
        active--;
        return quoteResponse();
    } });
    const requests = Array.from({ length: 9 }, (_, i) => client.get(`S${i}`));
    await tick();
    assert.equal(active, 4);
    while (release.length) { release.shift()(); await tick(); }
    await Promise.all(requests);
    assert.equal(maximum, 4);
});

test('a rate limit stops queued requests and honors Retry-After across symbols', async () => {
    let now = 1000;
    let requests = 0;
    const client = createQuoteClient('', { concurrency: 1, now: () => now, fetcher: async () => {
        requests++;
        return requests === 1 ? new Response('', { status: 429, headers: { 'Retry-After': '90' } }) : quoteResponse();
    } });
    const results = await Promise.allSettled([client.get('AAPL'), client.get('MSFT'), client.get('NVDA')]);
    assert.equal(requests, 1);
    assert.ok(results.every(result => result.status === 'rejected' && result.reason.retryAt >= 91_000));
    now = 91_000;
    await client.get('AAPL');
    assert.equal(requests, 2);
});

test('uses exponential backoff after server errors without returning an expired cached quote', async () => {
    let now = 0;
    let fail = false;
    let requests = 0;
    const client = createQuoteClient('', { now: () => now, fetcher: async () => { requests++; return fail ? new Response('', { status: 503 }) : quoteResponse(); } });
    const displayed = await client.get('AAPL');
    now = 30_000;
    fail = true;
    await assert.rejects(client.get('AAPL'), error => error.retryAt === 60_000);
    assert.equal(displayed.c, 100);
    await assert.rejects(client.get('AAPL'), QuoteError);
    assert.equal(requests, 2);
    now = 60_000;
    await assert.rejects(client.get('AAPL'), error => error.retryAt === 120_000);
    assert.equal(requests, 3);
});

test('rejects zero, negative, non-finite, missing, and nonnumeric quote prices', async () => {
    for (const price of [0, -1, NaN, Infinity, undefined, '100']) {
        const client = createQuoteClient('', { fetcher: async () => Response.json({ c: price, d: 1, dp: 1 }) });
        await assert.rejects(client.get('AAPL'), QuoteError);
    }
});

test('an aborted consumer does not cancel another consumer of the same quote', async () => {
    let release;
    let underlyingSignal;
    const client = createQuoteClient('', { fetcher: async (_url, init) => {
        underlyingSignal = init.signal;
        await new Promise(resolve => { release = resolve; });
        return quoteResponse();
    } });
    const controller = new AbortController();
    const first = client.get('AAPL', controller.signal);
    const second = client.get('AAPL');
    await tick();
    controller.abort();
    await assert.rejects(first, { name: 'AbortError' });
    assert.equal(underlyingSignal.aborted, false);
    release();
    assert.equal((await second).c, 100);
});

test('aborting all consumers cancels active and queued network work without backoff', async () => {
    let calls = 0;
    let underlyingSignal;
    const client = createQuoteClient('', { concurrency: 1, fetcher: async (_url, init) => {
        calls++;
        underlyingSignal = init.signal;
        await new Promise((_resolve, reject) => init.signal.addEventListener('abort', () => reject(new DOMException('', 'AbortError')), { once: true }));
        return quoteResponse();
    } });
    const controller = new AbortController();
    const requests = [client.get('AAPL', controller.signal), client.get('MSFT', controller.signal)];
    const settled = Promise.allSettled(requests);
    await tick();
    controller.abort();
    const results = await settled;
    assert.equal(underlyingSignal.aborted, true);
    assert.equal(calls, 1);
    assert.ok(results.every(result => result.status === 'rejected' && result.reason.name === 'AbortError'));
});

test('a pre-aborted request never reaches the network', async () => {
    let calls = 0;
    const controller = new AbortController();
    controller.abort();
    const client = createQuoteClient('', { fetcher: async () => { calls++; return quoteResponse(); } });
    await assert.rejects(client.get('AAPL', controller.signal), { name: 'AbortError' });
    assert.equal(calls, 0);
});

test('retains provider and fetch timestamps and the server stale fallback flag for display', async () => {
    const client = createQuoteClient('', { now: () => 9_000, fetcher: async () => Response.json({ c: 100, d: 1, dp: 1, t: 123, fetchedAt: 1_000, stale: true, h: 110, l: 90, o: 95, pc: 99 }) });
    const quote = await client.get('AAPL');
    assert.equal(quote.stale, true);
    assert.equal(quote.t, 123);
    assert.equal(quote.fetchedAt, 1_000);
    assert.equal(quote.receivedAt, 9_000);
    assert.equal(quote.h, 110);
});
