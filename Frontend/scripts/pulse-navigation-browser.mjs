import assert from 'node:assert/strict';
import { mkdirSync, writeFileSync } from 'node:fs';
import { chromium } from 'playwright';
const base = new URL(process.env.PULSE_NAV_TEST_URL || 'http://127.0.0.1:5173');
const world = process.env.PULSE_NAV_WORLD;
if(!['localhost','127.0.0.1'].includes(base.hostname)||!['tardis','interactive_brain','flashrevive'].includes(world)) throw Error('LOCAL_NATIVE_APP_REQUIRED');
mkdirSync('test-results/pulse-navigation',{recursive:true});
const browser=await chromium.launch(); const results=[];
try {
  for(const [name,viewport] of [['desktop',{width:1440,height:1000}],['mobile',{width:390,height:844}]]) {
    const context=await browser.newContext({viewport,reducedMotion:'reduce'});
    // UI-only, backend-offline resilience. Real SSO is verified separately.
    await context.route('**/*',async route=>{
      const url=new URL(route.request().url());
      if(url.origin!==base.origin||url.pathname.startsWith('/api/')||url.pathname.startsWith('/actuator/')) await route.fulfill({status:503,contentType:'application/json',body:'{"error":"UI_TEST_BACKEND_OFFLINE"}'});
      else await route.continue();
    });
    const page=await context.newPage(); const errors=[];
    page.on('pageerror',e=>errors.push(e.message));
    await page.goto(base.origin,{waitUntil:'domcontentloaded'});
    const shell=page.locator('[data-pulse-world-shell]'); await shell.waitFor({timeout:60000});
    assert.equal(await shell.count(),1); assert.equal(await shell.getAttribute('data-pulse-world-shell'),world);
    const trigger=shell.getByRole('button',{name:'PICO · PULSE World 메뉴'});
    await trigger.focus(); await page.keyboard.press('Enter');
    assert.equal(await trigger.getAttribute('aria-expanded'),'true');
    const panel=shell.getByRole('region',{name:'PULSE World 탐색'}); await panel.waitFor({state:'visible'});
    const anchors=shell.locator('a'); assert.equal(await anchors.count(),4);
    assert.equal(await shell.locator('[aria-current="page"]').count(),1);
    for(const a of await anchors.all()) { assert.equal(new URL(await a.getAttribute('href')).protocol,'https:'); assert.equal(await a.getAttribute('target'),'_blank'); assert.match(await a.getAttribute('rel'),/noopener/); }
    await page.keyboard.press('Tab'); assert.equal(await anchors.first().evaluate(el=>el.getRootNode().activeElement===el),true);
    await page.keyboard.press('Escape'); assert.equal(await trigger.getAttribute('aria-expanded'),'false');
    assert.equal(await trigger.evaluate(el=>el.getRootNode().activeElement===el),true);
    for(let i=0;i<5;i++) await trigger.click();
    assert.equal(await trigger.getAttribute('aria-expanded'),'true');
    const box=await panel.boundingBox(); assert.ok(box&&box.x>=0&&box.y>=0&&box.x+box.width<=viewport.width+1&&box.y+box.height<=viewport.height+1);
    await page.screenshot({path:'test-results/pulse-navigation/'+name+'.png'});
    await page.evaluate(()=>document.dispatchEvent(new PointerEvent('pointerdown',{bubbles:true})));
    assert.equal(await trigger.getAttribute('aria-expanded'),'false'); assert.deepEqual(errors,[]);
    results.push({world,viewport:name,pass:true,scope:'native UI; backend intentionally offline'}); await context.close();
  }
} finally { await browser.close(); }
writeFileSync('test-results/pulse-navigation/results.json',JSON.stringify(results,null,2));
console.log('PULSE NAV PASS: desktop/mobile native UI, keyboard/focus, safe external links and viewport fit.');
