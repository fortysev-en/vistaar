/** Everything persisted on the TV: session, favourites, choices. localStorage, with an in-memory fallback if it is unavailable. */
const mem: Record<string, string> = {}
let ls: Storage | null = null
try { ls = window.localStorage; ls.setItem('__t', '1'); ls.removeItem('__t') } catch { ls = null }

const get = (k: string): string => (ls ? ls.getItem(k) : mem[k]) || ''
const set = (k: string, v: string) => { if (ls) ls.setItem(k, v); else mem[k] = v }
const del = (k: string) => { if (ls) ls.removeItem(k); else delete mem[k] }

export const DEFAULT_PLAYLIST = 'https://iptv-org.github.io/iptv/countries/in.m3u'

function randomId(): string {
  const a = new Uint8Array(8)
  ;(window.crypto || (window as any).msCrypto).getRandomValues(a)
  return Array.from(a, (b) => ('0' + b.toString(16)).slice(-2)).join('')
}

export const Store = {
  get deviceId(): string { let d = get('deviceId'); if (!d) { d = randomId(); set('deviceId', d) } return d },
  get sso() { return get('sso') }, get auth() { return get('auth') }, get lb() { return get('lb') }, get refresh() { return get('refresh') },
  get uniqueId() { return get('uniqueId') }, get subscriberId() { return get('subscriberId') }, get uid() { return get('uid') },
  setAuth(a: string) { set('auth', a) },
  get loggedIn() { return this.sso !== '' },
  get freeOnly() { return get('freeOnly') === '1' },
  set freeOnly(v: boolean) { if (v) set('freeOnly', '1'); else del('freeOnly') },
  get hasAccess() { return this.loggedIn || this.freeOnly },
  save(s: { sso: string; auth: string; refresh: string; lb: string; uniqueId: string; subscriberId: string; uid: string }) {
    for (const k of Object.keys(s) as Array<keyof typeof s>) set(k, s[k])
  },
  clear() { del('sso'); del('auth'); del('refresh') },

  /** Chosen Jio language ids. `langsDone` with an empty list means "all languages". */
  get langs(): number[] { return get('langs').split(',').map(Number).filter((n) => n > 0) },
  get langsDone() { return get('langsDone') === '1' },
  setLangs(l: number[]) { set('langs', l.join(',')); set('langsDone', '1') },

  get playlistUrl() { return get('playlist') || DEFAULT_PLAYLIST },
  set playlistUrl(v: string) { set('playlist', v) },

  audioPref(id: string) { return get('aud.' + id) },
  setAudioPref(id: string, v: string) { set('aud.' + id, v) },
  clearAudioPrefs() { if (ls) Object.keys(ls).filter((k) => k.startsWith('aud.')).forEach((k) => ls!.removeItem(k)); else Object.keys(mem).filter((k) => k.startsWith('aud.')).forEach((k) => delete mem[k]) },

  // generic string/JSON helpers for the catalogue (favourites, recents, scan cache, "may not play")
  str: get, put: set, remove: del,
  json<T>(k: string, fallback: T): T { try { const v = get(k); return v ? (JSON.parse(v) as T) : fallback } catch { return fallback } },
  putJson(k: string, v: unknown) { set(k, JSON.stringify(v)) },
}
