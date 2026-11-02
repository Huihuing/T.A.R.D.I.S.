import test from 'node:test';
import assert from 'node:assert/strict';
import { resolvePortalOrigin, PULSE_WORLDS, mountPulseNavigation } from '../src/pulse-platform/pulse-navigation.ts';
test('three distinct HTTPS Worlds', () => {
  assert.deepEqual(PULSE_WORLDS.map(w=>w.id), ['tardis','interactive_brain','flashrevive']);
  assert.equal(new Set(PULSE_WORLDS.map(w=>w.href)).size,3);
  for(const w of PULSE_WORLDS) assert.equal(new URL(w.href).protocol,'https:');
});
test('safe build-time portal origin and fail-closed invalid configuration', () => {
  assert.equal(resolvePortalOrigin(),'https://pulse-lab-huihuing.vercel.app');
  assert.equal(resolvePortalOrigin('https://pulse.example/'),'https://pulse.example');
  for(const bad of ['javascript:alert(1)','//example.org','http://example.org','https://user:pass@example.org','https://example.org/path','https://example.org/?ticket=x','https://example.org/#token','https://localhost','not a URL']) assert.equal(resolvePortalOrigin(bad),null);
  assert.equal(resolvePortalOrigin('http://localhost:3000',true),'http://localhost:3000');
  assert.equal(resolvePortalOrigin('http://localhost:3000'),null);
  assert.equal(resolvePortalOrigin('http://example.org',true),null);
});
test('SSR safe without tokens, storage or DOM',()=>assert.doesNotThrow(mountPulseNavigation({world:'tardis'})));
