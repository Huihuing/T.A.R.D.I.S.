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
    const home=async()=>{
      await page.goto(base.origin,{waitUntil:'domcontentloaded'});
      if(name==='desktop') {
        for(const label of ['로그인','회원가입']) {
          const button=page.getByRole('button',{name:label,exact:true});
          await button.waitFor({state:'visible'});await button.scrollIntoViewIfNeeded();
          assert.equal(await button.evaluate(el=>{
            const b=el.getBoundingClientRect();const top=document.elementFromPoint(b.x+b.width/2,b.y+b.height/2);
            return top===el||Boolean(top&&el.contains(top));
          }),true,'Native '+label+' must be the actual pointer target, not a covering badge');
        }
      } else await page.locator('button').filter({has:page.locator('svg.lucide-menu')}).waitFor({state:'visible'});
      assert.equal(await page.locator('[data-pulse-world-shell]').count(),0);
    };
    await home();
    await page.screenshot({path:out+'/'+enabled+'-'+name+'.png'});
    for(const [path,mode,label] of [['/login','login','로그인'],['/register','signup','회원가입']]) {
      if(name==='desktop') {await home();await page.getByRole('button',{name:label,exact:true}).click();}
      else await page.goto(base.origin+path,{waitUntil:'domcontentloaded'});
      if(enabled) await page.waitForURL(portal+'/'+mode+'?world='+world);
      else {await page.locator('input[type="password"]').first().waitFor({state:'visible'});assert.equal(new URL(page.url()).origin,base.origin);}
      await page.goto(base.origin+path+'?nativeAuth=1',{waitUntil:'domcontentloaded'});
      await page.locator('input[type="password"]').first().waitFor({state:'visible'});
      assert.equal(new URL(page.url()).origin,base.origin,'Native escape must not redirect again');
      if(mode==='login') await page.screenshot({path:out+'/' + enabled+'-native-login-'+name+'.png'});
    }
    assert.deepEqual(errors,[]);
    results.push({world,viewport:name,floatingShell:false,centralRoutingEnabled:enabled,nativeFallback:true,desktopButtonsUncoveredAndClicked:name==='desktop',pass:true,scope:'UI routing only; central destination is an intercept fixture, not OIDC'});
    await context.close();
  }
} finally {await browser.close();}
writeFileSync(out+'/results-'+enabled+'.json',JSON.stringify(results,null,2));
console.log('PASS: real home controls unobscured; no floating shell; native login/signup; opt-in fixed central routes; native escape.');
