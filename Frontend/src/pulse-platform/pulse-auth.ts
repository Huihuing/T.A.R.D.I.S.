/** PULSE authentication navigation. No DOM injection, tokens or storage access. */
export type PulseWorld = 'tardis' | 'interactive_brain' | 'flashrevive';
export type PulseAuthMode = 'login' | 'signup';
export const DEFAULT_PORTAL_ORIGIN = 'https://pulse-lab-huihuing.vercel.app';
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
export function pulseAuthDestination(options: {
  world: PulseWorld; mode: PulseAuthMode; enabled: boolean;
  portalUrl?: string; allowLocal?: boolean; search?: string; hash?: string;
}): string | null {
  if (options.enabled !== true || !['tardis', 'interactive_brain', 'flashrevive'].includes(options.world)
      || !['login', 'signup'].includes(options.mode)) return null;
  const query = new URLSearchParams(options.search ?? '');
  // Keep account proof, verification, recovery and incoming SSO in the owning app.
  // Only an ordinary auth entry (optionally Brain's fixed account return) is routed.
  for (const [key, value] of query) {
    if (key !== 'return' || value !== 'account') return null;
  }
  if (options.hash) return null;
  const origin = resolvePortalOrigin(options.portalUrl, options.allowLocal === true);
  if (!origin) return null;
  const url = new URL('/' + options.mode, origin);
  url.searchParams.set('world', options.world);
  return url.toString();
}
export function nativeAuthPath(world: PulseWorld, mode: PulseAuthMode): string {
  const path = world === 'flashrevive' ? '/' : mode === 'login' ? '/login' : world === 'tardis' ? '/register' : '/signup';
  return path + '?nativeAuth=1';
}
