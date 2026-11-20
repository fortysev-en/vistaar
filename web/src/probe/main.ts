import Hls from 'hls.js'
import * as Api from '../api'
import { Store } from '../store'
import type { Channel, Play } from '../types'
import { focusFirst, install, setFocus, focused } from '../nav/focus'

/**
 * Vistaar webOS compatibility test. A tester signs in with their own Jio account; the app then tries each way of playing a Jio
 * channel on THIS TV and shows a pass/fail report. It prints no tokens. At the end it signs out so the TV stops using a device slot.
 */

const app = document.getElementById('app')!
const SERVICE = 'luna://com.vistaar.probe.webos.service/'
const $ = <T extends HTMLElement>(id: string) => document.getElementById(id) as T

// ------------------------------------------------------------------ helpers
const sleep = (ms: number) => new Promise<void>((r) => setTimeout(r, ms))

function luna(method: string, params: unknown, timeoutMs = 20000): Promise<any> {
  return new Promise((resolve, reject) => {
    const B = (window as any).PalmServiceBridge
    if (!B) return reject(new Error('PalmServiceBridge not available (not running on webOS?)'))
    const bridge = new B()
    const t = setTimeout(() => reject(new Error('service call timed out')), timeoutMs)
    bridge.onservicecallback = (msg: string) => { clearTimeout(t); try { resolve(JSON.parse(msg)) } catch (e) { reject(e) } }
    bridge.call(SERVICE + method, JSON.stringify(params))
  })
}

function b64ToBuf(b64: string): ArrayBuffer {
  const bin = atob(b64); const u = new Uint8Array(bin.length)
  for (let i = 0; i < bin.length; i++) u[i] = bin.charCodeAt(i)
  return u.buffer
}

const results: Array<{ id: string; label: string; status: 'pass' | 'fail' | 'info'; detail: string }> = []
function report(id: string, label: string, status: 'pass' | 'fail' | 'info', detail: string) {
  results.push({ id, label, status, detail })
  const log = $('log')
  const cls = status === 'pass' ? 'ok' : status === 'fail' ? 'bad' : 'dim'
  const mark = status === 'pass' ? 'PASS' : status === 'fail' ? 'FAIL' : 'INFO'
  log.insertAdjacentHTML('beforeend', `<div><span class="${cls}">${mark}</span> ${label} <span class="dim">${detail.replace(/</g, '&lt;')}</span></div>`)
}

// ------------------------------------------------------------------ sign-in screens
function pad(onKey: (k: string) => void): string {
  const keys = ['1', '2', '3', '4', '5', '6', '7', '8', '9', 'Del', '0', 'Clear']
  return `<div class="pad">${keys.map((k) => `<div class="btn" data-f data-k="${k}">${k}</div>`).join('')}</div>`
}

function showLogin(step: 'phone' | 'otp', phone = '', error = '') {
  let value = ''
  const max = step === 'phone' ? 10 : 6
  app.innerHTML = `<h1>Vistaar compatibility test</h1>
    <div class="sub">This short test checks how Jio channels play on this TV. Sign in with your own Jio number; nothing is saved or shared, and the app signs out when it finishes (about a minute).</div>
    <div class="row"><div class="col"><div class="dim" style="font-size:22px;margin-bottom:8px">${step === 'phone' ? 'Mobile number' : 'Code sent to +91 ' + phone}</div>
    <div class="field" id="val">&nbsp;</div><div class="err" id="err">${error}</div>
    ${pad(() => {})}<div class="pad"><div class="btn wide" data-f data-k="go">${step === 'phone' ? 'Send code' : 'Sign in and start test'}</div></div></div></div>`
  const paint = () => { $('val').textContent = value || ' ' }
  app.onclick = (ev) => {
    const el = (ev.target as HTMLElement).closest('[data-k]') as HTMLElement | null
    if (!el) return
    const k = el.getAttribute('data-k')!
    if (k === 'Del') value = value.slice(0, -1)
    else if (k === 'Clear') value = ''
    else if (k === 'go') { if (value.length === max) submit(); return }
    else if (value.length < max) value += k
    paint()
  }
  const submit = async () => {
    $('err').textContent = step === 'phone' ? 'Sending code…' : 'Signing in…'
    try {
      if (step === 'phone') {
        const err = await Api.sendOtp(value)
        if (err) return showLogin('phone', '', err)
        return showLogin('otp', value)
      }
      const err = await Api.verifyOtp(phone, value)
      if (err === Api.DEVICE_LIMIT) return showLimit(phone)
      if (err) return showLogin('otp', phone, err)
      runTests()
    } catch (e) { $('err').textContent = 'Network error: ' + (e as Error).message }
  }
  document.onkeydown = (e) => { // number keys type digits too
    if (e.keyCode >= 48 && e.keyCode <= 57 && value.length < max) { value += String(e.keyCode - 48); paint(); e.preventDefault() }
    else if (e.keyCode >= 96 && e.keyCode <= 105 && value.length < max) { value += String(e.keyCode - 96); paint(); e.preventDefault() }
  }
  focusFirst('[data-k="5"]')
}

function showLimit(phone: string) {
  app.innerHTML = `<h1>Device limit reached</h1><div class="sub">Your Jio account is on the maximum number of devices. Logging out the other devices lets this test run; they will need to sign in again.</div>
    <div class="pad"><div class="btn wide" data-f data-k="x">Log out other devices and continue</div><div class="btn wide" data-f data-k="c">Cancel</div></div><div class="err" id="err"></div>`
  app.onclick = async (ev) => {
    const k = ((ev.target as HTMLElement).closest('[data-k]') as HTMLElement | null)?.getAttribute('data-k')
    if (k === 'c') return showLogin('phone')
    if (k === 'x') { $('err').textContent = 'Logging out other devices…'; const err = await Api.expireAllUsers(); if (err) return showLogin('phone', '', err); runTests() }
  }
  focusFirst()
}

// ------------------------------------------------------------------ the tests
interface Found { ch: Channel; play: Play; token: string }

function tokenOf(url: string): string { const m = /[?&]__hdnea__=([^&]+)/.exec(url); return m ? m[1] : '' }
const addToken = (u: string, tok: string) => (tok && u.indexOf('__hdnea__') < 0 ? u + (u.indexOf('?') < 0 ? '?' : '&') + '__hdnea__=' + tok : u)

/** Classify an HLS channel: keyed (AES-128 key), clear (plain segments) or unknown. */
async function classify(play: Play): Promise<'keyed' | 'clear' | 'unknown'> {
  const tok = tokenOf(play.url)
  const master = await (await fetch(play.url)).text()
  const first = (master.split('\n').find((l) => l && l[0] !== '#') || '').trim()
  if (!first) return 'unknown'
  const v = await (await fetch(addToken(new URL(first, play.url).toString(), tok))).text()
  if (/#EXT-X-KEY:METHOD=(?!NONE)/.test(v)) return 'keyed'
  return /#EXTINF/.test(v) ? 'clear' : 'unknown'
}

async function playWithHls(video: HTMLVideoElement, play: Play, ch: Channel, viaService: boolean, ms: number) {
  const tok = tokenOf(play.url)
  const Base = (Hls as any).DefaultConfig.loader
  class KeyViaService extends Base {
    load(context: any, config: any, callbacks: any) {
      if (!viaService || String(context.url).indexOf('.pkey') < 0) return super.load(context, config, callbacks)
      const t0 = performance.now()
      luna('fetchBytes', { url: context.url, headers: Api.authHeaders(ch.id, 0) }).then((r) => {
        if (!r.returnValue || r.status !== 200 || !r.base64) throw new Error('key via service: ' + (r.errorText || 'HTTP ' + r.status))
        const data = b64ToBuf(r.base64); const t1 = performance.now()
        callbacks.onSuccess({ url: context.url, data }, { aborted: false, loaded: data.byteLength, retry: 0, total: data.byteLength, chunkCount: 1, bwEstimate: 0, loading: { start: t0, first: t1, end: t1 }, parsing: { start: t1, end: t1 }, buffering: { start: 0, first: 0, end: 0 } }, context, null)
      }).catch((e) => callbacks.onError({ code: 0, text: String(e && e.message) }, context, null, {}))
    }
  }
  const errs: string[] = []; let frags = 0
  const hls = new Hls({ loader: KeyViaService as any, xhrSetup: (xhr: XMLHttpRequest, u: string) => { if (tok && u.indexOf('__hdnea__') < 0 && u.indexOf('.pkey') < 0) xhr.open('GET', addToken(u, tok), true) } })
  hls.on(Hls.Events.FRAG_LOADED, () => { frags++ })
  hls.on(Hls.Events.ERROR, (_e, d) => { errs.push(d.details + ((d as any).response ? '(' + (d as any).response.code + ')' : '') + (d.fatal ? '!' : '')) })
  hls.loadSource(play.url); hls.attachMedia(video)
  video.muted = true; video.play().catch(() => {})
  await sleep(ms)
  const out = { frags, t: Math.round(video.currentTime * 10) / 10, w: video.videoWidth, h: video.videoHeight, errs: errs.slice(0, 3).join(',') }
  hls.destroy(); video.removeAttribute('src'); video.load()
  return out
}

async function runTests() {
  app.onclick = null; document.onkeydown = null
  app.innerHTML = `<h1>Running the test…</h1><div class="sub">Keep this screen open. When it says DONE, take a photo of the screen and send it to the developer.</div>
    <div class="row"><div class="col"><div id="log"></div></div><div><video id="v" muted playsinline></video><div class="box" id="sum"></div></div></div>`
  const video = $<HTMLVideoElement>('v')

  // T0: environment
  const ua = navigator.userAgent
  const chrome = (/Chrome\/([\d.]+)/.exec(ua) || [])[1] || '?'
  const web0s = (/Web0S|webOS/i.test(ua) ? 'webOS' : 'not webOS') + ((/webOS\.TV-(\d+)/.exec(ua) || [])[1] ? ' TV-' + (/webOS\.TV-(\d+)/.exec(ua) || [])[1] : '')
  let sys = ''
  try { const di = (window as any).webOSSystem && (window as any).webOSSystem.deviceInfo; if (di) sys = JSON.stringify(typeof di === 'string' ? JSON.parse(di) : di).slice(0, 160) } catch { /* ignore */ }
  report('env', 'Device', 'info', `${web0s}, Chromium ${chrome}, screen ${screen.width}x${screen.height} ${sys}`)
  report('mse', 'Playback support', Hls.isSupported() ? 'pass' : 'fail', `MSE=${!!(window as any).MediaSource} nativeHLS=${video.canPlayType('application/vnd.apple.mpegurl') || 'no'}`)
  try {
    const ks = await (navigator as any).requestMediaKeySystemAccess('com.widevine.alpha', [{ initDataTypes: ['cenc'], videoCapabilities: [{ contentType: 'video/mp4; codecs="avc1.42E01E"' }] }])
    report('wv', 'Widevine DRM', 'pass', ks.keySystem)
  } catch (e) { report('wv', 'Widevine DRM', 'fail', String((e as Error).message).slice(0, 80)) }

  // T-svc: the helper service
  let svcOk = false
  try { const r = await luna('ping', {}); svcOk = !!r.returnValue; report('svc', 'Helper service starts', svcOk ? 'pass' : 'fail', svcOk ? `node ${r.node} ${r.platform}` : String(r.errorText || JSON.stringify(r)).slice(0, 100)) }
  catch (e) { report('svc', 'Helper service starts', 'fail', (e as Error).message) }

  // find a clear and a keyed HLS channel
  let clear: Found | null = null, keyed: Found | null = null
  try {
    await Api.beginSession().catch(() => {})
    const list = await Api.channels()
    report('api', 'Jio channel list from the app', 'pass', `${list.length} channels`)
    for (const ch of list.slice(0, 120)) {
      if (clear && keyed) break
      try {
        const play = await Api.streamUrl(ch, null)
        if (play.dash) continue
        const kind = await classify(play)
        if (kind === 'clear' && !clear) clear = { ch, play, token: tokenOf(play.url) }
        if (kind === 'keyed' && !keyed) keyed = { ch, play, token: tokenOf(play.url) }
      } catch { /* channel not on this plan */ }
    }
    report('find', 'Found test channels', clear && keyed ? 'pass' : 'fail', `plain=${clear ? clear.ch.name : 'none'} | encrypted=${keyed ? keyed.ch.name : 'none'}`)
  } catch (e) { report('api', 'Jio channel list from the app', 'fail', (e as Error).message) }

  // T2: plain HLS through hls.js
  if (clear) {
    try { const r = await playWithHls(video, clear.play, clear.ch, false, 12000); report('plain', 'Plain channel plays (hls.js)', r.frags > 1 && r.t > 2 ? 'pass' : 'fail', `segments=${r.frags} time=${r.t}s ${r.w}x${r.h} ${r.errs}`) }
    catch (e) { report('plain', 'Plain channel plays (hls.js)', 'fail', (e as Error).message) }
  }

  if (keyed) {
    // T3: key fetched straight from the app (expected to fail)
    try {
      const keyUrl = await findKeyUrl(keyed)
      try {
        const r = await fetch(keyUrl, { headers: { accesstoken: Store.auth, uniqueid: Store.uniqueId, deviceid: Store.deviceId } })
        report('key-direct', 'Key fetch straight from the app', r.ok ? 'pass' : 'info', 'HTTP ' + r.status + (r.ok ? ' (service not needed!)' : ' (expected: blocked)'))
      } catch (e) { report('key-direct', 'Key fetch straight from the app', 'info', 'blocked by the server (expected): ' + (e as Error).message.slice(0, 60)) }
      // T4: key via the helper service
      if (svcOk) {
        try {
          const r = await luna('fetchBytes', { url: keyUrl, headers: Api.authHeaders(keyed.ch.id, 0) })
          report('key-svc', 'Key fetch via helper service', r.returnValue && r.status === 200 && r.length === 16 ? 'pass' : 'fail', `HTTP ${r.status} bytes=${r.length} ${r.errorText || ''}`)
        } catch (e) { report('key-svc', 'Key fetch via helper service', 'fail', (e as Error).message) }
        // T5: encrypted channel playing with the key from the service
        try { const r = await playWithHls(video, keyed.play, keyed.ch, true, 14000); report('keyed', 'Encrypted channel plays (hls.js + service)', r.frags > 1 && r.t > 2 ? 'pass' : 'fail', `segments=${r.frags} time=${r.t}s ${r.w}x${r.h} ${r.errs}`) }
        catch (e) { report('keyed', 'Encrypted channel plays (hls.js + service)', 'fail', (e as Error).message) }
      }
    } catch (e) { report('key-direct', 'Key fetch', 'fail', (e as Error).message) }

    // T6: the TV's built-in HLS player on the encrypted channel (information only)
    try {
      video.src = keyed.play.url; video.muted = true; await video.play().catch(() => {}); await sleep(10000)
      report('native', 'Built-in HLS player, encrypted channel', video.currentTime > 2 ? 'pass' : 'info', `time=${Math.round(video.currentTime * 10) / 10}s ${video.videoWidth}x${video.videoHeight}`)
      video.removeAttribute('src'); video.load()
    } catch (e) { report('native', 'Built-in HLS player, encrypted channel', 'info', (e as Error).message) }
  }

  // sign out so this TV stops using a Jio device slot
  await Api.logoutRemote(); Store.clear()
  report('out', 'Signed out of Jio', 'info', 'device slot released')

  const get = (id: string) => results.find((r) => r.id === id)
  const code = ['env', 'mse', 'wv', 'svc', 'plain', 'key-direct', 'key-svc', 'keyed', 'native'].map((id) => { const r = get(id); return id + '=' + (r ? (r.status === 'pass' ? 'P' : r.status === 'fail' ? 'F' : 'I') : '-') }).join(' ')
  $('sum').innerHTML = `<b>DONE</b><br>${code}<br><span class="dim">Chromium ${chrome} · ${web0s}</span>`
}

/** The playlist's key URL, read the same way hls.js does. */
async function findKeyUrl(f: Found): Promise<string> {
  const master = await (await fetch(f.play.url)).text()
  const first = new URL((master.split('\n').find((l) => l && l[0] !== '#') || '').trim(), f.play.url).toString()
  const v = await (await fetch(addToken(first, f.token))).text()
  const m = /#EXT-X-KEY:[^\n]*URI="([^"]+)"/.exec(v)
  if (!m) throw new Error('no key URI in playlist')
  return new URL(m[1], first).toString()
}

// ------------------------------------------------------------------ start
install({ onBack: () => { if (focused()) return false; return false } })
if (location.hash === '#run' && Store.loggedIn) runTests() // developer shortcut: only when already signed in
else showLogin('phone')
setFocus(focused())
