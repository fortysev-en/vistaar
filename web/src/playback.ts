import Hls from 'hls.js'
import * as Api from './api'
import { Epg } from './catalog'
import { Platform } from './platform'
import { Store } from './store'
import { LANG_CODES, type Channel, type Play, type Program } from './types'

/** Stream lookup and playback (port of Streams.kt + the player/preview wiring). Jio HLS goes through hls.js; free streams use the TV's own player. */

export interface Resolved { cur: Program | null; nxt: Program | null; play: Play; at: number }

/** Looks up the guide and stream URL for [ch]. Free channels play their own URL. */
export async function resolve(ch: Channel): Promise<Resolved> {
  if (ch.url) return { cur: null, nxt: null, play: { url: ch.url, dash: ch.url.split('?')[0].slice(-4) === '.mpd', license: '' }, at: Date.now() }
  const epg = await Epg.load(ch.id)
  const cur = Epg.now(epg)
  return { cur, nxt: Epg.next(epg), play: await Api.streamUrl(ch, cur), at: Date.now() }
}

/** Jio puts a token in the master playlist URL and wants it on every later request; browsers can't replay the CDN cookie, but the raw token works as a query. */
export const tokenOf = (url: string) => { const m = /[?&]__hdnea__=([^&]+)/.exec(url); return m ? m[1] : '' }
export const withToken = (u: string, tok: string) => (tok && u.indexOf('__hdnea__') < 0 ? u + (u.indexOf('?') < 0 ? '?' : '&') + '__hdnea__=' + tok : u)

function b64ToBuf(b64: string): ArrayBuffer {
  const bin = atob(b64); const u = new Uint8Array(bin.length)
  for (let i = 0; i < bin.length; i++) u[i] = bin.charCodeAt(i)
  return u.buffer
}

export interface Level { index: number; height: number; bitrate: number }
export interface AudioOpt { index: number; name: string; key: string; selected: boolean }

export class Engine {
  hls: Hls | null = null
  gen = 0
  ch: Channel | null = null
  onFirstFrame: (() => void) | null = null
  onError: ((msg: string) => void) | null = null
  onTracks: (() => void) | null = null
  private audioApplied = ''

  constructor(public video: HTMLVideoElement, private preview = false) {
    video.addEventListener('playing', () => { if (this.onFirstFrame) this.onFirstFrame() })
    video.addEventListener('error', () => { if (this.ch && this.ch.url && this.onError) this.onError('media error ' + (video.error ? video.error.code : '')) })
  }

  load(ch: Channel, r: Resolved) {
    this.stop(); this.ch = ch; this.audioApplied = ''
    const g = this.gen
    const v = this.video
    v.muted = this.preview
    if (r.play.dash) { if (this.onError) this.onError('This channel uses DRM playback, which is not supported on this TV yet.'); return }
    if (ch.url) { // third-party stream: the TV's own player (no CORS needed, no Jio tokens ever sent)
      v.src = r.play.url; v.play().catch(() => {}); return
    }
    const tok = tokenOf(r.play.url)
    const Base = (Hls as any).DefaultConfig.loader
    const chId = ch.id
    class KeyLoader extends Base {
      load(context: any, config: any, callbacks: any) {
        if (!Platform.hasService || String(context.url).indexOf('.pkey') < 0) return super.load(context, config, callbacks)
        const t0 = performance.now()
        Platform.luna('fetchBytes', { url: context.url, headers: Api.authHeaders(chId, 0) }).then((res) => {
          if (!res.returnValue || res.status !== 200 || !res.base64) throw new Error('key HTTP ' + (res.status || res.errorText))
          const data = b64ToBuf(res.base64); const t1 = performance.now()
          callbacks.onSuccess({ url: context.url, data }, { aborted: false, loaded: data.byteLength, retry: 0, total: data.byteLength, chunkCount: 1, bwEstimate: 0, loading: { start: t0, first: t1, end: t1 }, parsing: { start: t1, end: t1 }, buffering: { start: 0, first: 0, end: 0 } }, context, null)
        }).catch((e) => callbacks.onError({ code: 0, text: String(e && e.message) }, context, null, {}))
      }
    }
    const hls = new Hls({
      loader: KeyLoader as any, enableWorker: false, startFragPrefetch: true,
      maxBufferLength: this.preview ? 8 : 30, backBufferLength: 10,
      xhrSetup: (xhr: XMLHttpRequest, u: string) => { if (tok && u.indexOf('__hdnea__') < 0 && u.indexOf('.pkey') < 0) xhr.open('GET', withToken(u, tok), true) },
    })
    this.hls = hls
    hls.on(Hls.Events.MANIFEST_PARSED, (_e, d) => {
      if (!this.preview) return
      let cap = -1
      d.levels.forEach((l, i) => { if (l.height && l.height <= 480 && (cap < 0 || l.height >= d.levels[cap].height)) cap = i })
      if (cap >= 0) hls.autoLevelCapping = cap
    })
    hls.on(Hls.Events.AUDIO_TRACKS_UPDATED, () => { this.applyAudio(); if (this.onTracks) this.onTracks() })
    hls.on(Hls.Events.LEVEL_SWITCHED, () => { if (this.onTracks) this.onTracks() })
    hls.on(Hls.Events.ERROR, (_e, d) => { if (d.fatal && g === this.gen && this.onError) this.onError(d.details + ((d as any).response ? ' ' + (d as any).response.code : '')) })
    hls.loadSource(r.play.url); hls.attachMedia(v)
    v.play().catch(() => {})
  }

  stop() {
    this.gen++
    if (this.hls) { try { this.hls.destroy() } catch { /* ignore */ } this.hls = null }
    const v = this.video
    v.pause(); v.removeAttribute('src'); try { v.load() } catch { /* ignore */ }
  }

  // ---- quality
  levels(): Level[] {
    if (!this.hls) return []
    const seen: { [h: string]: boolean } = {}; const out: Level[] = []
    this.hls.levels.forEach((l, i) => { const k = String(l.height || l.bitrate); if (!seen[k]) { seen[k] = true; out.push({ index: i, height: l.height || 0, bitrate: l.bitrate || 0 }) } })
    return out.sort((a, b) => b.height - a.height || b.bitrate - a.bitrate)
  }
  /** -1 = Auto. */
  get fixedLevel(): number { return this.hls ? this.hls.currentLevel === this.hls.loadLevel && !(this.hls as any).autoLevelEnabled ? this.hls.currentLevel : -1 : -1 }
  setLevel(i: number) { if (this.hls) this.hls.currentLevel = i }
  get autoLevel(): boolean { return this.hls ? !!(this.hls as any).autoLevelEnabled : true }
  get levelHeight(): number { return this.hls && this.hls.currentLevel >= 0 && this.hls.levels[this.hls.currentLevel] ? this.hls.levels[this.hls.currentLevel].height : 0 }

  // ---- audio
  audios(): AudioOpt[] {
    if (!this.hls) return []
    const sel = this.hls.audioTrack
    return this.hls.audioTracks.map((t: any, i: number) => ({ index: i, name: langName(t.lang) || t.name || 'Track ' + (i + 1), key: t.lang || t.name || '', selected: i === sel }))
  }
  setAudio(i: number) { if (this.hls) this.hls.audioTrack = i }

  /** Re-selects the audio language the user last chose on this channel; with no choice, prefers the channel's own language, then the chosen ones. */
  private applyAudio() {
    const ch = this.ch; if (!ch || !this.hls || this.audioApplied === ch.id) return
    const opts = this.audios(); if (!opts.length) return
    this.audioApplied = ch.id
    const saved = Store.audioPref(ch.id)
    let pick = saved ? opts.find((o) => o.key === saved) : undefined
    if (!pick && ch.lang > 0) {
      const prefer = [LANG_CODES[ch.lang]].concat(Store.langs.map((l) => LANG_CODES[l])).filter(Boolean)
      for (const code of prefer) { pick = opts.find((o) => o.key.toLowerCase().indexOf(code) === 0); if (pick) break }
    }
    if (pick && !pick.selected) this.setAudio(pick.index)
  }
}

const LANG_NAMES: { [k: string]: string } = { hi: 'Hindi', en: 'English', ta: 'Tamil', te: 'Telugu', ml: 'Malayalam', kn: 'Kannada', mr: 'Marathi', bn: 'Bengali', gu: 'Gujarati', pa: 'Punjabi', ur: 'Urdu', or: 'Odia', as: 'Assamese', ne: 'Nepali', fr: 'French', bho: 'Bhojpuri' }
export function langName(code: string | undefined): string { if (!code) return ''; const c = code.toLowerCase().split('-')[0]; return LANG_NAMES[c] || (code.length <= 3 ? code.toUpperCase() : code) }
