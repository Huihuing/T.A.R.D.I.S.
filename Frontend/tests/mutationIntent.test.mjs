import test from 'node:test';
import assert from 'node:assert/strict';
import { mutationIntent } from '../src/mutationIntent.ts';

const storage = () => {
    const values = new Map();
    return { getItem: key => values.get(key) ?? null, setItem: (key, value) => values.set(key, value), removeItem: key => values.delete(key), clear: () => values.clear(), values };
};
globalThis.localStorage = storage();
globalThis.sessionStorage = storage();

test('same trade retries retain a stable key after an ambiguous server response', async () => {
    sessionStorage.clear();
    const first = await mutationIntent('/api/trade/buy', '{"symbol":"AAPL","amount":1}');
    first.settle(new Response('', { status: 503 }));
    const retry = await mutationIntent('/api/trade/buy', '{"symbol":"AAPL","amount":1}');
    assert.equal(retry.key, first.key);
    assert.match(first.key, /^[\da-f-]{36}$/);
    assert.ok([...sessionStorage.values.values()].every(value => !value.includes('AAPL')));
});

test('a confirmed response ends the intent so a later identical order receives a new key', async () => {
    sessionStorage.clear();
    const first = await mutationIntent('/buy', 'body');
    first.settle(new Response('', { status: 200 }));
    assert.notEqual((await mutationIntent('/buy', 'body')).key, first.key);
});

test('changing user, endpoint, or payload generates a distinct request intent', async () => {
    sessionStorage.clear();
    localStorage.setItem('username', 'one');
    const first = await mutationIntent('/buy', 'one-share');
    const changedPayload = await mutationIntent('/buy', 'two-shares');
    const changedEndpoint = await mutationIntent('/sell', 'two-shares');
    localStorage.setItem('username', 'two');
    const changedUser = await mutationIntent('/buy', 'two-shares');
    assert.equal(new Set([first.key, changedPayload.key, changedEndpoint.key, changedUser.key]).size, 4);
});

test('a late response cannot clear a newer request intent', async () => {
    sessionStorage.clear();
    const first = await mutationIntent('/buy', 'one-share');
    const second = await mutationIntent('/buy', 'two-shares');
    first.settle(new Response('', { status: 200 }));
    assert.equal((await mutationIntent('/buy', 'two-shares')).key, second.key);
});

test('an unresolved intent survives temporarily changing order inputs', async () => {
    sessionStorage.clear();
    const first = await mutationIntent('/buy', 'one-share');
    await mutationIntent('/buy', 'two-shares');
    assert.equal((await mutationIntent('/buy', 'one-share')).key, first.key);
});
