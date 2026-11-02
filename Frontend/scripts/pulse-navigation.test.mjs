import test from 'node:test';
import assert from 'node:assert/strict';
import { pulseAuthDestination as destination, resolvePortalOrigin, nativeAuthPath } from '../src/pulse-platform/pulse-auth.ts';
const base={world:'tardis',mode:'login',enabled:true};
test('central routing is opt-in; no live login is disabled by default',()=>{
  for(const enabled of [false,undefined,null,'true',1]) assert.equal(destination({...base,enabled}),null);
});
test('six fixed World and mode destinations, no user-provided return URL',()=>{
  for(const world of ['tardis','interactive_brain','flashrevive']) for(const mode of ['login','signup']) assert.equal(destination({...base,world,mode}),'https://pulse-lab-huihuing.vercel.app/'+mode+'?world='+world);
  assert.equal(destination({...base,world:'https://attacker.invalid'}),null);
  assert.equal(destination({...base,mode:'//attacker.invalid'}),null);
});
test('clean reviewed HTTPS origin; local HTTP requires explicit development mode',()=>{
  for(const url of ['javascript:alert(1)','//evil.invalid','http://example.org','https://u:p@example.org','https://example.org/path','https://example.org/?code=a','https://example.org/#token','https://localhost','invalid']) assert.equal(resolvePortalOrigin(url),null);
  assert.equal(resolvePortalOrigin('https://pulse.example/'),'https://pulse.example');
  assert.equal(resolvePortalOrigin('http://localhost:3000',true),'http://localhost:3000');
  assert.equal(resolvePortalOrigin('http://localhost:3000'),null);
  assert.equal(resolvePortalOrigin('http://example.org',true),null);
});
test('native escape, linking, reset, OAuth callback and unknown query stay local',()=>{
  for(const search of ['?nativeAuth=1','?pulseChallenge=example','?challenge=example','?verifyEmailToken=example','?passwordResetToken=example','?code=example&state=example','?registered=1','?reset=1','?returnTo=https://evil.invalid','?return=evil','?return=account&nativeAuth=1']) assert.equal(destination({...base,search}),null);
  assert.equal(destination({...base,hash:'#pulse_sso=example'}),null);
  assert.ok(destination({...base,search:'?return=account'}));
});
test('native fallback routes cannot loop back to the portal',()=>{
  for(const world of ['tardis','interactive_brain','flashrevive']) for(const mode of ['login','signup']) {
    const url=new URL(nativeAuthPath(world,mode),'https://native.example');
    assert.equal(destination({...base,world,mode,search:url.search}),null);
  }
});
