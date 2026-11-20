import type { Channel } from './types'

/** Port of Free.kt's parser: iptv-org style M3U -> channels + categories (most populated first). */

const MASK = (1n << 64n) - 1n
/** FNV-1a 64 over UTF-16 units, as in the Android app, so a channel keeps its id across playlist refreshes. */
function fnv(s: string): bigint {
  let h = 0xcbf29ce484222325n
  for (let i = 0; i < s.length; i++) { h ^= BigInt(s.charCodeAt(i)); h = (h * 0x100000001b3n) & MASK }
  return h
}

const attr = (line: string, k: string) => {
  const m = new RegExp(k + '="([^"]*)"').exec(line)
  return m ? m[1].trim() : ''
}
const QUALITY = /\s*\((\d{3,4})p\)\s*$/
const TAGS = /\s*\[[^\]]*]/g

export interface Parsed { channels: Channel[]; cats: string[] }

/** Returns null when the text has no playable channels. YouTube and non-http entries are skipped (the player can't play them). */
export function parseM3u(text: string): Parsed | null {
  interface Raw { id: string; name: string; cat: string; hd: boolean; logo: string; url: string; ua: string; ref: string }
  const raw: Raw[] = []
  const seen = new Set<string>()
  let info: string | null = null, ua = '', ref = ''
  for (const l of text.split(/\r?\n/)) {
    const line = l.trim()
    if (line.startsWith('#EXTINF')) { info = line; ua = ''; ref = ''; continue }
    if (line.startsWith('#EXTVLCOPT:')) {
      const kv = line.replace('#EXTVLCOPT:', '')
      const k = kv.split('=')[0].trim()
      const v = kv.substring(kv.lastIndexOf(k + '=') + k.length + 1).trim() // some lists repeat the key
      if (k === 'http-user-agent') ua = v; else if (k === 'http-referrer') ref = v
      continue
    }
    if (line === '' || line.startsWith('#')) continue
    if (info === null) continue
    const i = info; info = null
    if (!/^https?:/i.test(line) || line.includes('youtube.com') || line.includes('youtu.be')) continue
    let name = i.substring(i.indexOf(',', i.lastIndexOf('"') + 1) + 1).replace(TAGS, '').trim()
    const q = parseInt((QUALITY.exec(name) || [])[1] || '0', 10) || 0
    name = name.replace(QUALITY, '').trim()
    if (!name) continue
    const id = 'f' + (fnv(attr(i, 'tvg-id') + '|' + name) >> 1n).toString(16)
    if (seen.has(id)) continue
    seen.add(id)
    const cat = attr(i, 'group-title').split(';')[0].trim() || 'General'
    raw.push({ id, name, cat, hd: q >= 720, logo: attr(i, 'tvg-logo'), url: line, ua, ref })
  }
  if (!raw.length) return null
  const counts = new Map<string, number>()
  raw.forEach((r) => counts.set(r.cat, (counts.get(r.cat) || 0) + 1))
  const cats = Array.from(counts.entries()).sort((a, b) => b[1] - a[1]).map((e) => e[0])
  const channels = raw.map((r, n): Channel => ({ id: r.id, name: r.name, order: n, lang: -(cats.indexOf(r.cat) + 1), hd: r.hd, logo: r.logo, url: r.url, ua: r.ua, ref: r.ref, cat: 0, premium: false }))
  return { channels, cats }
}

/** ISO 639-3 file names of iptv-org's per-language lists, by Jio language id. */
export const LANG_FILES: Record<number, string> = {
  1: 'hin', 2: 'mar', 3: 'pan', 4: 'urd', 5: 'ben', 6: 'eng', 7: 'mal', 8: 'tam', 9: 'guj', 10: 'ori', 11: 'tel', 12: 'bho', 13: 'kan', 14: 'asm', 15: 'nep', 16: 'fra',
}

// ------------------------------------------------------------------ state (port of the Free object in Free.kt)
import { text as fetchText } from './api'
import { Store } from './store'

const SIX_H = 6 * 3600_000
function hash(s: string): string { let x = 0; for (let i = 0; i < s.length; i++) x = (x * 31 + s.charCodeAt(i)) | 0; return (x >>> 0).toString(16) }

async function cachedText(url: string, force: boolean): Promise<string> {
  const k = 'fm:' + hash(url)
  const at = Number(Store.str(k + ':t') || 0)
  const hit = Store.str(k)
  if (!force && hit && Date.now() - at < SIX_H) return hit
  try {
    const t = await fetchText(url)
    if (t.trim().indexOf('#EXTM3U') !== 0) throw new Error('Not a playlist')
    try { Store.put(k, t); Store.put(k + ':t', String(Date.now())) } catch { /* storage full: still use it this session */ }
    return t
  } catch (e) { if (hit) return hit; throw e } // a stale list beats no list
}

export const Free = {
  channels: [] as Channel[],
  cats: [] as string[],
  loading: false,
  error: '' as string,
  langIds: null as Set<string> | null,
  langKey: '',

  catName(lang: number): string { return this.cats[-lang - 1] || 'Free' },

  /** Free channels limited to the chosen languages; falls back to all if the language lists are missing or match nothing (custom playlists). */
  get shown(): Channel[] {
    const ids = this.langIds
    if (!ids || !this.channels.some((c) => ids.has(c.id))) return this.channels
    return this.channels.filter((c) => ids.has(c.id))
  },

  async load(force: boolean, done: () => void): Promise<void> {
    if (this.loading || (!force && this.channels.length)) return
    this.loading = true; this.error = ''; done()
    try {
      const p = parseM3u(await cachedText(Store.playlistUrl, force))
      if (p) { this.channels = p.channels; this.cats = p.cats } else this.error = 'The playlist had no playable channels'
    } catch (e) { this.error = (e as Error).message || 'Could not load the free channels' }
    this.loading = false; done()
  },

  /** Switch playlist: fetch first and keep it only if it parses, so a bad link never wipes the list. Returns an error message or null. */
  async switchTo(url: string): Promise<string | null> {
    try {
      const t = await fetchText(url)
      if (t.trim().indexOf('#EXTM3U') !== 0) return 'That link is not an M3U playlist'
      const p = parseM3u(t)
      if (!p) return 'The playlist had no playable channels'
      try { Store.put('fm:' + hash(url), t); Store.put('fm:' + hash(url) + ':t', String(Date.now())) } catch { /* ignore */ }
      Store.playlistUrl = url; this.channels = p.channels; this.cats = p.cats; this.error = ''
      return null
    } catch (e) { return (e as Error).message || 'Could not load that playlist' }
  },

  /** Fetch iptv-org's per-language lists for the chosen languages (ids only are cached). Cheap to call repeatedly. */
  async loadLangs(done: () => void): Promise<void> {
    const codes = Store.langs.map((l) => LANG_FILES[l]).filter(Boolean).sort()
    const key = codes.join(',')
    if (key === this.langKey) return
    this.langKey = key
    if (!codes.length) { this.langIds = null; done(); return }
    const ids = new Set<string>(); let ok = false
    for (const c of codes) {
      try {
        const ck = 'fl:' + c
        const cached = Store.str(ck), at = Number(Store.str(ck + ':t') || 0)
        let list: string[]
        if (cached && Date.now() - at < SIX_H) list = cached.split(',')
        else {
          const p = parseM3u(await fetchText('https://iptv-org.github.io/iptv/languages/' + c + '.m3u'))
          list = p ? p.channels.map((x) => x.id) : []
          try { Store.put(ck, list.join(',')); Store.put(ck + ':t', String(Date.now())) } catch { /* ignore */ }
        }
        list.forEach((i) => ids.add(i)); ok = true
      } catch { /* try the others */ }
    }
    if (this.langKey !== key) return
    this.langIds = ok ? ids : null
    if (!ok) this.langKey = ''
    done()
  },
}
