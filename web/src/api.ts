import { Store } from './store'
import { type Channel, type Play, type Program } from './types'

/** Port of Api.kt. Endpoints and headers were recovered from JioTV 7.1.8; the Jio hosts answer browser (CORS) preflights. */

export const DEVICE_LIMIT = 'device_limit'

const APP_KEY = 'NzNiMDhlYzQyNjJm'
const APP_NAME = 'RJIL_JioTV'
const USER_GROUP = 'tvYR7NSNn7rymo3F'
const LOGIN = 'https://jiotvapi.media.jio.com/userservice/apis/'
const LISTS = 'https://jiotvapi.cdn.jio.com/apis/'
const PLAYBACK = 'https://jiotvapi.media.jio.com/playback/apis/'
const TOKENS = 'https://auth.media.jio.com/tokenservice/apis/'
const OS_VERSION = '11'

export class ApiError extends Error {}

type Headers = Record<string, string>

async function call(url: string, init: { method?: string; headers?: Headers; body?: string; timeoutMs?: number } = {}): Promise<{ code: number; text: string }> {
  const ctl = typeof AbortController !== 'undefined' ? new AbortController() : null
  const timer = ctl ? setTimeout(() => ctl.abort(), init.timeoutMs || 30000) : 0
  try {
    const r = await fetch(url, { method: init.method || 'GET', headers: init.headers, body: init.body, signal: ctl ? ctl.signal : undefined })
    return { code: r.status, text: await r.text() }
  } finally { if (timer) clearTimeout(timer) }
}

function uuid(): string {
  const b = new Uint8Array(16); crypto.getRandomValues(b)
  b[6] = (b[6] & 0x0f) | 0x40; b[8] = (b[8] & 0x3f) | 0x80
  const h = Array.from(b, (x) => ('0' + x.toString(16)).slice(-2)).join('')
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`
}

/** Plain GET with no Jio headers, for third-party playlists. */
export async function text(url: string): Promise<string> {
  const { code, text: t } = await call(url)
  if (code < 200 || code > 299) throw new ApiError('HTTP ' + code)
  return t
}

const loginHeaders = (): Headers => ({
  'Content-Type': 'application/json', os: 'android', devicetype: 'phone', appName: APP_NAME, appkey: APP_KEY,
  deviceId: Store.deviceId, versionCode: '424', 'x-platform': 'androidmobile', osVersion: OS_VERSION,
})
const num = (phone: string) => btoa('+91' + phone)

/** Returns null on success, otherwise a message for the user. */
export async function sendOtp(phone: string): Promise<string | null> {
  const { code, text: t } = await call(LOGIN + 'v1/loginotp/send', { method: 'POST', headers: loginHeaders(), body: JSON.stringify({ number: num(phone) }) })
  return code >= 200 && code <= 299 ? null : `Send OTP failed (${code}): ${t.slice(0, 160)}`
}

interface Pending { temp: string; lb: string; unique: string; sub: string; uid: string }
let pending: Pending | null = null

/** Returns null on success, [DEVICE_LIMIT] when the account is on too many devices, or a message. */
export async function verifyOtp(phone: string, otp: string): Promise<string | null> {
  const body = JSON.stringify({
    number: num(phone), otp,
    deviceInfo: { consumptionDeviceName: 'LG webOS TV', info: { type: 'android', platform: { name: 'webos' }, androidId: Store.deviceId } },
  })
  const { code, text: t } = await call(LOGIN + 'v2/loginotp/verify', { method: 'POST', headers: loginHeaders(), body })
  if (code < 200 || code > 299) return `Verify failed (${code}): ${t.slice(0, 160)}`
  const root = JSON.parse(t)
  const d = root.data || root // either wrapped in {data} or flat
  const user = d.sessionAttributes && d.sessionAttributes.user
  if (d.tempToken) { // device limit: keep what the app keeps so "log out other devices" can finish this sign-in
    pending = { temp: d.tempToken, lb: d.lbCookie || '', unique: (user && user.unique) || '', sub: (user && user.subscriberId) || '', uid: (user && user.uid) || '' }
    return DEVICE_LIMIT
  }
  if (!d.ssoToken || !user) return 'Unexpected login response: ' + t.slice(0, 160)
  Store.save({ sso: d.ssoToken, auth: d.authToken || '', refresh: d.refreshToken || '', lb: d.lbCookie || '', uniqueId: user.unique || '', subscriberId: user.subscriberId || '', uid: user.uid || '' })
  return null
}

/** Device limit: logs the account out of every device (the JioTV app's own step) and returns fresh tokens. null = signed in. */
export async function expireAllUsers(): Promise<string | null> {
  const p = pending
  if (!p) return 'That sign-in expired. Go back and request a new code.'
  const { code, text: t } = await call(TOKENS + 'v2/expireallusers', {
    method: 'POST', headers: { 'Content-Type': 'application/json', 'x-platform': 'androidmobile', temptoken: p.temp },
    body: JSON.stringify({ appName: APP_NAME, deviceId: Store.deviceId }),
  })
  let root: any
  try { root = JSON.parse(t) } catch { return `Unexpected reply from Jio (${code}).` }
  const d = root.data || root
  if (code < 200 || code > 299 || !d.ssoToken || !d.authToken) return String(root.message || `Could not log out the other devices (${code}).`).slice(0, 120)
  pending = null
  if (!p.unique || !p.sub) return 'Other devices were logged out. Send a new code to sign in.'
  Store.save({ sso: d.ssoToken, auth: d.authToken, refresh: d.refreshToken || '', lb: p.lb, uniqueId: p.unique, subscriberId: p.sub, uid: p.uid })
  return null
}

/** Tells Jio this TV is signed out so it stops counting against the device limit. Best effort. */
export async function logoutRemote(): Promise<void> {
  if (!Store.refresh) return
  try {
    await call(TOKENS + 'v1/logout', {
      method: 'POST', timeoutMs: 6000,
      headers: { 'Content-Type': 'application/json', accesstoken: Store.auth, ssotoken: Store.sso, devicetype: 'phone', versionCode: '424', os: 'android', uniqueId: Store.uniqueId },
      body: JSON.stringify({ appName: APP_NAME, deviceId: Store.deviceId, refreshToken: Store.refresh }),
    })
  } catch { /* offline or slow: sign out locally anyway */ }
}

/** Headers the Android app sends on playback calls and on every HLS segment request. */
export function authHeaders(channelId = '0', srno = 0): Headers {
  return {
    uniqueId: Store.uniqueId, ssotoken: Store.sso, accesstoken: Store.auth, subscriberId: Store.subscriberId, deviceId: Store.deviceId, userId: Store.uid,
    os: 'android', devicetype: 'phone', appkey: APP_KEY, appName: APP_NAME, versioncode: '424', versionCode: '424', osVersion: OS_VERSION,
    'x-platform': 'android', usergroup: USER_GROUP, lbcookie: Store.lb || '1', channelid: channelId, srno: String(srno), crmid: Store.subscriberId,
  }
}

const listHeaders: Headers = {
  usergroup: USER_GROUP, usertype: 'jio', appkey: APP_KEY, devicetype: 'phone', os: 'android', deviceId: '55a8a2b6299fb995',
  appversioncode: '404', appversion: '7.1.7.P.20260225.1', lbcookie: '1',
}

async function get(url: string): Promise<string> {
  const { code, text: t } = await call(url, { headers: listHeaders })
  if (code < 200 || code > 299) throw new ApiError(`HTTP ${code}: ${t.slice(0, 120)}`)
  return t
}

export async function channels(): Promise<Channel[]> {
  const t = await get(LISTS + 'v3.1/getMobileChannelList/get/?langId=6&os=android&devicetype=phone&usertype=jio&version=404')
  const arr = JSON.parse(t).result
  if (!Array.isArray(arr)) throw new ApiError('No channel list in response')
  return arr.filter((x: any) => !x.isHidden && x.channel_id).map((x: any): Channel => ({
    id: String(x.channel_id), name: String(x.channel_name), order: x.channel_order == null ? 1e9 : x.channel_order, lang: x.channelLanguageId | 0,
    hd: !!x.isHD, logo: x.logoUrl ? 'https://jiotv.catchup.cdn.jio.com/dare_images/images/' + x.logoUrl : '', url: '', ua: '', ref: '', cat: x.channelCategoryId | 0, premium: !!x.is_premium,
  })).sort((a: Channel, b: Channel) => a.order - b.order)
}

/** Today's programmes by start time; empty if the guide is unavailable (playback works without it). */
export async function epg(channelId: string): Promise<Program[]> {
  try {
    const arr = JSON.parse(await get(LISTS + `v1.3/getepg/get?offset=0&channel_id=${channelId}&langId=6`)).epg
    const ms = (v: number) => (v < 1e11 ? v * 1000 : v)
    return (arr || []).map((x: any): Program => ({ srno: Number(x.srno) || 0, title: String(x.showname || ''), showtime: String(x.showtime || ''), start: ms(Number(x.startEpoch)), end: ms(Number(x.endEpoch)), desc: String(x.description || '') }))
      .sort((a: Program, b: Program) => a.start - b.start)
  } catch { return [] }
}

export async function nowPlaying(channelId: string): Promise<Program | null> {
  const now = Date.now()
  return (await epg(channelId)).find((p) => now >= p.start && now <= p.end) || null
}

const sdkHeaders = (channelId: string): Headers => ({
  appkey: APP_KEY, devicetype: 'phone', os: 'android', deviceId: Store.deviceId, versionCode: '424', osVersion: OS_VERSION, isott: 'false',
  dm: 'LG webOS TV', 'x-platform': 'android_mobile', uniqueId: Store.uniqueId, usergroup: USER_GROUP, languageId: '6', userId: Store.subscriberId,
  sid: uuid(), crmid: Store.subscriberId, channel_id: channelId, langid: '', camid: '', accesstoken: Store.auth, ssotoken: Store.sso,
  subscriberId: Store.subscriberId, lbcookie: Store.lb || '1',
})

let refreshing: Promise<boolean> | null = null
/** HTTP 419 = access token expired: trade the refresh token for a new one. [stale] is the token the failed call used, so concurrent callers refresh once. */
function refreshAuth(stale: string): Promise<boolean> {
  if (Store.auth !== stale) return Promise.resolve(true)
  if (!Store.refresh) return Promise.resolve(false)
  if (refreshing) return refreshing
  refreshing = (async () => {
    try {
      const { code, text: t } = await call(TOKENS + 'v2/refreshtoken', {
        method: 'POST', body: JSON.stringify({ appName: APP_NAME, deviceId: Store.deviceId, refreshToken: Store.refresh }),
        headers: { 'Content-Type': 'application/json', accesstoken: Store.auth, devicetype: 'phone', versionCode: '424', os: 'android', uniqueId: Store.uniqueId },
      })
      if (code < 200 || code > 299) return false
      const root = JSON.parse(t)
      const auth = (root.data || root).authToken
      if (!auth) return false
      Store.setAuth(auth)
      return true
    } catch { return false } finally { refreshing = null }
  })()
  return refreshing
}

/** The real app calls this after sign-in/launch; it registers the session server-side before any playback. */
export async function beginSession(): Promise<void> {
  // that host's CORS preflight only allows a fixed list of headers, so send just those
  const h = authHeaders(); const allowed = ['uniqueId', 'ssotoken', 'accesstoken', 'deviceId', 'os', 'devicetype', 'appkey', 'appName', 'versioncode', 'x-platform', 'usergroup', 'lbcookie', 'channelid', 'srno']
  const hdr: Headers = {}; allowed.forEach((k) => { hdr[k] = h[k] })
  const go = async () => (await call('https://tv.media.jio.com/apis/v3.0/beginsession/begin?langId=6', { headers: hdr })).code
  const stale = Store.auth
  let code = await go()
  if (code === 419 && (await refreshAuth(stale))) code = await go()
}

export async function streamUrl(ch: Channel, p: Program | null): Promise<Play> {
  const srno = p ? p.srno : 0
  const body = new URLSearchParams({
    stream_type: 'Seek', channel_id: ch.id, programId: String(srno), showtime: (p ? p.showtime : '000000').replace(/:/g, ''),
    srno: '20' + String(srno).slice(0, -6), begin: '', end: '',
  }).toString()
  const go = () => call(PLAYBACK + 'v1.1/geturl?langId=6', { method: 'POST', headers: { ...sdkHeaders(ch.id), 'Content-Type': 'application/x-www-form-urlencoded' }, body })
  const stale = Store.auth
  let r = await go()
  if (r.code === 419 && (await refreshAuth(stale))) r = await go()
  if (r.code < 200 || r.code > 299) throw new ApiError(`Playback HTTP ${r.code}: ${r.text.slice(0, 120)}`)
  const j = JSON.parse(r.text)
  const mpd = j.mpd
  const url: string = j.result || (mpd && mpd.result) || ''
  if (!url) throw new ApiError(`No stream URL (code ${j.code}: ${j.message})`)
  const dash = url.split('?')[0].endsWith('.mpd')
  const license: string = (mpd && mpd.key) || ''
  if (dash && !license) throw new ApiError('DASH stream without licence URL')
  return { url, dash, license }
}
