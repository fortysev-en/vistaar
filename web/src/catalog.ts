import * as Api from './api'
import { Free } from './free'
import { Store } from './store'
import { arrange } from './arrange'
import { LANGUAGES, type Channel, type Program } from './types'

/** Port of Catalog.kt + Epg: the channel list, "which channels does this plan include", favourites, recents, guide cache. Main-thread API. */

const SIX_H = 6 * 3600_000

// ------------------------------------------------------------------ guide cache
const epgCache = new Map<string, { at: number; list: Program[] }>()
const epgWait = new Map<string, Array<(l: Program[]) => void>>()

export const Epg = {
  cached(id: string, offset = 0): Program[] | null { const c = epgCache.get(id + ':' + offset); return c && Date.now() - c.at < 120_000 ? c.list : null },
  /** A day's programmes (offset 0 = today); free channels have no guide. Concurrent callers share one request. */
  load(id: string, offset = 0): Promise<Program[]> {
    // Free channels have no guide of their own; borrow the one of the same channel on the Jio plan, if there is one.
    if (id[0] === 'f') { const t = Catalog.twinOf(id); return t ? this.load(t, offset) : Promise.resolve([]) }
    const hit = this.cached(id, offset)
    if (hit) return Promise.resolve(hit)
    const key = id + ':' + offset
    return new Promise((res) => {
      const w = epgWait.get(key)
      if (w) { w.push(res); return }
      epgWait.set(key, [res])
      Api.epg(id, offset).then((list) => { epgCache.set(key, { at: Date.now(), list }); const ws = epgWait.get(key) || []; epgWait.delete(key); ws.forEach((f) => f(list)) })
    })
  },
  now(list: Program[]): Program | null { const t = Date.now(); return list.find((p) => t >= p.start && t <= p.end) || null },
  next(list: Program[]): Program | null { const c = this.now(list); return c ? list.find((p) => p.start >= c.end) || null : null },
}

// ------------------------------------------------------------------ catalogue
let gen = 0
let pending = false
let listener: (() => void) | null = null
const favs = new Set<string>()
let favsLoaded = false

function fire() { pending = false; if (listener) listener() }
/** Throttled (not debounced): during a scan results arrive constantly and a debounce would never fire until the end. */
function changed() { if (!pending) { pending = true; setTimeout(fire, 700) } }

const twinKey = (n: string) => n.toLowerCase().replace(/\(.*?\)/g, '').replace(/[^a-z0-9]/g, '').replace(/hd$/, '')
let twinFor = -1
const twinIdx = new Map<string, string>()
const RECHECK_MS = 10 * 60_000

export const Catalog = {
  all: [] as Channel[],
  playable: new Set<string>(),
  scanned: 0,
  scanning: false,
  error: '' as string,
  set onChange(f: (() => void) | null) { listener = f },

  visible(c: Channel): boolean { const l = Store.langs; return !l.length || !LANGUAGES[c.lang] || l.indexOf(c.lang) >= 0 },
  /** Channels the plan includes, limited to the chosen languages. */
  playableChannels(): Channel[] { return this.all.filter((c) => this.playable.has(c.id) && this.visible(c)) },
  freeChannels(): Channel[] { return Free.shown },
  allFor(c: Channel): Channel[] { return c.url ? this.freeChannels() : this.playableChannels() },

  /** The playable Jio channel with the same name as free channel [id] ("StarPlus (India) HD" is "Star Plus HD"), or null. */
  twinOf(id: string): string | null {
    const f = Free.shown.find((c) => c.id === id)
    if (!f) return null
    if (twinFor !== this.playable.size) { twinIdx.clear(); this.playableChannels().forEach((c) => { const k = twinKey(c.name); if (!twinIdx.has(k)) twinIdx.set(k, c.id) }); twinFor = this.playable.size }
    const k = twinKey(f.name)
    return k.length >= 4 ? twinIdx.get(k) || null : null
  },

  isFav(id: string) { return favs.has(id) },
  favCount() { return favs.size },
  /** Saved channels in the viewer's own order (ones never moved keep Jio first, then free, after the arranged ones). */
  favourites(): Channel[] { return arrange(this.playableChannels().filter((c) => favs.has(c.id)).concat(Free.shown.filter((c) => favs.has(c.id))), this.favOrder(), (c) => c.id) },
  favOrder(): string[] { return Store.str('favOrder').split(',').filter(Boolean) },
  /** [ids] is the order on screen; saved ids that are hidden right now (another language, signed out) are kept behind them. */
  saveFavOrder(ids: string[]) { Store.put('favOrder', ids.concat(this.favOrder().filter((x) => ids.indexOf(x) < 0)).join(',')) },
  /** Names the viewer gave to favourites; shown everywhere in place of the channel's own name. */
  names(): { [id: string]: string } { return Store.json<{ [id: string]: string }>('favNames', {}) },
  label(c: Channel): string { return (favs.has(c.id) && this.names()[c.id]) || c.name },
  /** A blank [name], or the channel's own, goes back to the original. */
  rename(c: Channel, name: string) { const n = this.names(), t = name.trim(); if (!t || t === c.name) delete n[c.id]; else n[c.id] = t; Store.putJson('favNames', n) },
  toggleFav(id: string): boolean {
    if (!favs.delete(id)) favs.add(id)
    else { const n = this.names(); delete n[id]; Store.putJson('favNames', n); Store.put('favOrder', this.favOrder().filter((x) => x !== id).join(',')) } // a removed favourite forgets its name and place
    Store.put('favs', Array.from(favs).join(','))
    return favs.has(id)
  },
  clearFavs() { favs.clear(); Store.remove('favs'); Store.remove('favNames'); Store.remove('favOrder') },

  recentIds(): string[] { return Store.str('recent').split(',').filter(Boolean) },
  pushRecent(id: string) { Store.put('recent', [id].concat(this.recentIds().filter((x) => x !== id)).slice(0, 12).join(',')) },
  recents(): Channel[] {
    const out: Channel[] = []
    for (const id of this.recentIds()) {
      const c = id[0] === 'f' ? Free.shown.find((x) => x.id === id) : this.all.find((x) => x.id === id && this.playable.has(id))
      if (c) out.push(c)
    }
    return out
  },
  clearRecents() { Store.remove('recent') },

  /** Number shown for a channel: its position among the channels its list includes. */
  numberOf(c: Channel): number { return this.allFor(c).findIndex((x) => x.id === c.id) + 1 },

  /** Channels that listed fine but failed to play twice in a row; remembered for 6 hours. */
  bad(): { [id: string]: number } {
    const m = Store.json<{ [id: string]: number }>('bad', {}); const now = Date.now(); const out: { [id: string]: number } = {}
    for (const k of Object.keys(m)) if (now - m[k] < SIX_H) out[k] = m[k]
    return out
  },
  isBad(id: string) { return !!this.bad()[id] },
  markBad(id: string) { const b = this.bad(); b[id] = Date.now(); Store.putJson('bad', b) },
  clearBad(id: string) { const b = this.bad(); if (b[id]) { delete b[id]; Store.putJson('bad', b) } },

  reset() { gen++; this.all = []; this.playable.clear(); this.scanning = false; this.scanned = 0; this.error = ''; Store.remove('ok'); Store.remove('okAt'); Store.remove('chlist') },

  /** Loads the lists and tests every channel (8 at a time); a fresh cache of playable ids skips the test. Free-only mode loads just the free list. */
  start(force: boolean) {
    if (!favsLoaded) { favsLoaded = true; Store.str('favs').split(',').filter(Boolean).forEach((x) => favs.add(x)) }
    Free.load(force, changed); Free.loadLangs(changed)
    if (!Store.loggedIn) { changed(); return }
    if (!force && (this.scanning || this.all.length)) { changed(); return }
    const g = ++gen
    this.scanning = true; this.error = ''; this.scanned = 0; this.playable.clear(); if (force) this.all = []
    // Show the last known list at once (favourites, Home, Guide) while the network refresh runs; a rescan replaces it.
    const saved = force ? [] : Store.json<Channel[]>('chlist', [])
    const savedOk = Store.str('ok').split(',').filter(Boolean)
    const seeded = saved.length > 0 && savedOk.length > 0
    if (seeded) { this.all = saved; savedOk.forEach((x) => this.playable.add(x)) }
    changed()
    ;(async () => {
      try {
        try { await Api.beginSession() } catch { /* playback may still work */ }
        const list = await Api.channels()
        Store.putJson('chlist', list)
        const cached = Store.str('ok').split(',').filter(Boolean)
        const fresh = !force && cached.length > 0 && Date.now() - Number(Store.str('okAt') || 0) < SIX_H
        if (g !== gen) return
        this.all = list
        if (fresh) {
          cached.forEach((x) => this.playable.add(x)); this.scanning = false; changed()
          // The plan may have changed since (a new channel pack): quietly re-test only what the cached scan refused.
          const have = new Set(cached), miss = list.filter((c) => !have.has(c.id))
          if (miss.length && Date.now() - Number(Store.str('okAt') || 0) > RECHECK_MS) this.scan(miss, g, true)
        } else this.scan(list, g)
      } catch (e) {
        if (g !== gen) return
        this.scanning = false
        // Offline or Jio down: the saved list stays usable; only report an error when there is nothing to show.
        if (!seeded) this.error = 'Channel list failed: ' + (e as Error).message
        changed()
      }
    })()
  },

  /** Favourites, recents and the chosen languages go first so what the user cares about shows up straight away. */
  scan(all: Channel[], g: number, quiet = false) {
    const first = new Set(Array.from(favs).concat(this.recentIds())); const langs = Store.langs
    const rank = (c: Channel) => (first.has(c.id) ? 0 : !langs.length || langs.indexOf(c.lang) >= 0 ? 1 : 2)
    const queue = all.slice().sort((a, b) => rank(a) - rank(b))
    let next = 0, running = 0, done = 0
    const found = new Set<string>()
    const finish = () => {
      this.scanning = false
      if (!quiet) Array.from(this.playable).forEach((id) => { if (!found.has(id)) this.playable.delete(id) }) // drop channels the saved list had but the rescan refused
      Store.put('ok', Array.from(this.playable).join(','))
      if (!quiet) Store.put('okAt', String(Date.now()))
      changed()
    }
    const pump = () => {
      if (g !== gen) return
      while (running < 8 && next < queue.length) {
        const ch = queue[next++]; running++
        probe(ch).then((ok) => {
          running--
          if (g !== gen) return
          if (ok) { this.playable.add(ch.id); found.add(ch.id) }
          if (!quiet) this.scanned++
          if (++done === queue.length) finish(); else { if (ok || !quiet) changed(); pump() }
        })
      }
    }
    pump()
  },
}

/** A channel is playable when the stream-URL call returns a URL. Some channels need a programme id: retry those, unless the plan refused (3012). */
async function probe(ch: Channel): Promise<boolean> {
  try { await Api.streamUrl(ch, null); return true } catch (e) {
    if (String((e as Error).message).indexOf('3012') >= 0) return false
    try { await Api.streamUrl(ch, await Api.nowPlaying(ch.id)); return true } catch { return false }
  }
}
