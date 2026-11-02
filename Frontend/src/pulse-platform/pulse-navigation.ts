/** PULSE World Shell v1.0.0. Navigation only: never handles tokens or World data. */
export type PulseWorld = 'tardis' | 'interactive_brain' | 'flashrevive';
export const PULSE_SHELL_VERSION = '1.0.0';
export const DEFAULT_PORTAL_ORIGIN = 'https://pulse-lab-huihuing.vercel.app';
export const PULSE_WORLDS = [
  { id: 'tardis', name: 'T.A.R.D.I.S.', pulse: 'MARKET', color: '#82d8ff', href: 'https://tardis-neon.vercel.app' },
  { id: 'interactive_brain', name: 'Interactive Brain', pulse: 'NEURAL', color: '#b79cff', href: 'https://interactive-brain-huihuing.vercel.app' },
  { id: 'flashrevive', name: 'FlashRevive', pulse: 'CREATIVE', color: '#ffad7c', href: 'https://flash-revive-studio-web.vercel.app' },
] as const;

/** Build-time configuration only. A URL from query strings must never be passed here. */
export function resolvePortalOrigin(raw?: string, allowLocal = false): string | null {
  if (raw === undefined || raw.trim() === '') return DEFAULT_PORTAL_ORIGIN;
  try {
    const u = new URL(raw.trim());
    const local = ['localhost', '127.0.0.1', '[::1]'].includes(u.hostname);
    if (u.username || u.password || u.search || u.hash || u.pathname !== '/') return null;
    if (u.protocol !== 'https:' && !(allowLocal && local && u.protocol === 'http:')) return null;
    if (local && !allowLocal) return null;
    return u.origin;
  } catch { return null; }
}

/** Idempotent, framework-independent Shadow DOM shell; dispose on HMR. */
export function mountPulseNavigation(options: { world: PulseWorld; portalUrl?: string; allowLocal?: boolean }): () => void {
  if (typeof document === 'undefined') return () => {};
  const origin = resolvePortalOrigin(options.portalUrl, options.allowLocal === true);
  const world = PULSE_WORLDS.find(item => item.id === options.world);
  if (!origin || !world || document.querySelector('[data-pulse-world-shell]')) return () => {};
  const host = document.createElement('div');
  host.dataset.pulseWorldShell = options.world;
  host.dataset.version = PULSE_SHELL_VERSION;
  // Inline stacking/placement survives app CSS; all internal rules remain isolated.
  host.style.cssText = 'position:fixed;left:16px;bottom:max(16px,env(safe-area-inset-bottom));z-index:1000;max-width:calc(100vw - 32px)';
  const shadow = host.attachShadow({ mode: 'open' });
  const style = document.createElement('style');
  style.textContent = `
    :host{font:13px/1.5 system-ui,sans-serif;color:#f4f7fb;--accent:#9af7dc}
    *{box-sizing:border-box}button,a{font:inherit}button{cursor:pointer}
    button:focus-visible,a:focus-visible{outline:2px solid var(--accent);outline-offset:3px}
    .trigger{display:flex;align-items:center;gap:10px;min-height:44px;padding:7px 13px;color:#f4f7fb;border:1px solid #43515f;border-radius:24px;background:#0b111bea;box-shadow:0 4px 18px #0004}
    .trigger strong{letter-spacing:.04em;font-size:11px}.trigger small{font-size:9px;color:var(--accent)}
    .pico{position:relative;display:inline-flex;align-items:center;justify-content:center;gap:4px;width:26px;height:26px;flex:none;border:1px solid var(--accent);border-radius:45%;background:#20313a}
    .pico::before,.pico::after{content:'';width:3px;height:5px;border-radius:3px;background:var(--accent)}
    .panel{position:absolute;bottom:54px;left:0;width:min(310px,calc(100vw - 32px));max-height:min(460px,calc(100dvh - 100px));overflow:auto;padding:18px;border:1px solid #43515f;border-radius:14px;background:#0b111bf7;box-shadow:0 12px 42px #0006}
    .panel[hidden]{display:none}.title{margin:0 0 4px;font-size:14px}.hint{margin:0 0 12px;color:#b5c1cf;font-size:11px}
    nav{display:grid;gap:5px}a,.current{display:block;padding:9px 10px;border-radius:7px;color:#e8f0f8;text-decoration:none}a:hover{background:#ffffff12}a span{color:#99adbf;font-size:11px}.current{border:1px solid #ffffff18;color:var(--accent)}
    .separator{border-top:1px solid #ffffff20;margin-top:10px;padding-top:8px}.note{margin:12px 0 0;font-size:11px;line-height:1.6;color:#b5c1cf}
    @media(max-width:480px){.trigger{padding:8px;min-width:44px;justify-content:center}.trigger strong,.trigger small{display:none}}
  `;
  shadow.append(style);
  host.style.setProperty('--accent', world.color);
  const trigger = document.createElement('button');
  trigger.className = 'trigger'; trigger.type = 'button';
  trigger.setAttribute('aria-expanded', 'false');
  trigger.setAttribute('aria-controls', 'pulse-world-panel');
  trigger.setAttribute('aria-label', 'PICO · PULSE World 메뉴');
  const pico = document.createElement('span'); pico.className = 'pico'; pico.setAttribute('aria-hidden', 'true');
  const label = document.createElement('strong'); label.textContent = 'PULSE//LAB';
  const theme = document.createElement('small'); theme.textContent = world.pulse;
  trigger.append(pico, label, theme);
  const panel = document.createElement('section');
  panel.id = 'pulse-world-panel'; panel.className = 'panel'; panel.hidden = true;
  panel.setAttribute('aria-label', 'PULSE World 탐색');
  const title = document.createElement('h2'); title.className = 'title'; title.textContent = 'PICO / ' + world.pulse;
  const hint = document.createElement('p'); hint.className = 'hint'; hint.textContent = '현재 World: ' + world.name;
  const nav = document.createElement('nav'); nav.setAttribute('aria-label', 'World 전환');
  const link = (text: string, href: string) => {
    const a = document.createElement('a'); a.textContent = text + ' ↗';
    a.href = href; a.target = '_blank'; a.rel = 'noopener noreferrer'; return a;
  };
  nav.append(link('PULSE//LAB 홈', origin + '/'));
  for (const item of PULSE_WORLDS) {
    if (item.id === world.id) {
      const current = document.createElement('span'); current.className = 'current';
      current.setAttribute('aria-current', 'page'); current.textContent = item.name + ' · 현재'; nav.append(current);
    } else nav.append(link(item.name, item.href));
  }
  const account = document.createElement('div'); account.className = 'separator';
  account.append(link('PULSE//ID 로그인 · 계정 연결 관리', origin + '/identity'));
  const note = document.createElement('p'); note.className = 'note';
  note.textContent = '링크는 새 탭에서 열려 작업을 유지합니다. 사이트별 로그인과 데이터는 독립적이며, 계정 연결 상태는 PULSE//ID에서 확인합니다.';
  panel.append(title, hint, nav, account, note); shadow.append(trigger, panel);
  const close = (focus = false) => { panel.hidden = true; trigger.setAttribute('aria-expanded', 'false'); if (focus) trigger.focus(); };
  trigger.addEventListener('click', () => {
    panel.hidden = !panel.hidden; trigger.setAttribute('aria-expanded', String(!panel.hidden));
  });
  const escape = (event: KeyboardEvent) => { if (event.key === 'Escape' && !panel.hidden) { event.preventDefault(); close(true); } };
  const outside = (event: PointerEvent) => { if (!event.composedPath().includes(host)) close(); };
  shadow.addEventListener('keydown', escape as EventListener);
  document.addEventListener('pointerdown', outside);
  document.body.append(host);
  return () => { document.removeEventListener('pointerdown', outside); host.remove(); };
}
