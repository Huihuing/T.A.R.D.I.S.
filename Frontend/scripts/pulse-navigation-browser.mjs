import assert from 'node:assert/strict';
import { mkdirSync, writeFileSync } from 'node:fs';
import { chromium } from 'playwright';
const base=new URL(process.env.PULSE_NAV_TEST_URL||'http://127.0.0.1:5173');
const world=process.env.PULSE_NAV_WORLD;
const enabled=process.env.PULSE_AUTH_TEST_ENABLED==='true';
if(!['localhost','127.0.0.1'].includes(base.hostname)||world!=='tardis') throw Error('LOCAL_TARDIS_REQUIRED');
const portal='https://pulse-lab-huihuing.vercel.app';
const out='test-results/pulse-navigation';mkdirSync(out,{recursive:true});
const browser=await chromium.launch();const results=[];
try {
  for(const [name,viewport] of [['desktop',{width:1440,height:1000}],['mobile',{width:390,height:844}],['small-phone',{width:320,height:568}]]) {
    const context=await browser.newContext({viewport,reducedMotion:'reduce'});
    await context.route('**/*',async route=>{
      const u=new URL(route.request().url());
      if(u.origin===portal) return route.fulfill({status:200,contentType:'text/html',body:'<!doctype html><title>Routing fixture</title><h1>Central routing destination</h1>'});
      if(u.origin!==base.origin||u.pathname.startsWith('/api/')||u.pathname.startsWith('/actuator/')) return route.fulfill({status:503,contentType:'application/json',body:'{"error":"UI_TEST_BACKEND_OFFLINE"}'});
      return route.continue();
    });
    const page=await context.newPage();const errors=[];page.on('pageerror',e=>errors.push(e.message));
    await page.goto(base.origin,{waitUntil:'domcontentloaded'});
    await page.waitForFunction(()=>Boolean(document.querySelector('#root')?.childElementCount));
    assert.equal(await page.locator('[data-pulse-world-shell]').count(),0,'Floating PICO shell must not exist');
    await page.screenshot({path:out+'/'+enabled+'-'+name+'.png'});
    for(const [path,mode] of [['/login','login'],['/register','signup']]) {
      await page.goto(base.origin+path,{waitUntil:'domcontentloaded'});
      if(enabled) await page.waitForURL(portal+'/'+mode+'?world='+world);
      else { await page.locator('input[type="password"]').first().waitFor({state:'visible'});assert.equal(new URL(page.url()).origin,base.origin); }
      await page.goto(base.origin+path+'?nativeAuth=1',{waitUntil:'domcontentloaded'});
      await page.locator('input[type="password"]').first().waitFor({state:'visible'});
      assert.equal(new URL(page.url()).origin,base.origin,'Native escape must not redirect again');
    }
    assert.deepEqual(errors,[]);
    results.push({world,viewport:name,floatingShell:false,centralRoutingEnabled:enabled,nativeFallback:true,pass:true,scope:'UI routing only; central destination is an intercept fixture, not OIDC'});
    await context.close();
  }
} finally {await browser.close();}
writeFileSync(out+'/results-'+enabled+'.json',JSON.stringify(results,null,2));
console.log('PASS: no floating shell; native login/signup; opt-in fixed central routes; native escape.');
