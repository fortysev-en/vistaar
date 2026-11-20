import { Catalog } from '../catalog'
import { clear, h, icon } from '../dom'
import { focused, focusFirst, setFocus } from '../nav/focus'
import { Engine, resolve, type Resolved } from '../playback'
import { Store } from '../store'
import type { Channel, Program } from '../types'
import { App, type Name, type Screen } from './app'
import { fmtTime, langName, liveBadge, logoBox, progressBar } from './widgets'

/** Full-screen playback: info overlay, channel zapping with neighbour prefetch, quality/audio sheet, error card. Port of PlayerActivity. */

interface Params { id: string; list?: 'jio' | 'free' | 'hd'; from?: { name: Name; params?: any } }

export function playerScreen(p: Params): Screen {
  const want = p.id
  let channels: Channel[] = (want[0] === 'f' ? Catalog.freeChannels() : Catalog.playableChannels()).filter((c) => p.list !== 'hd' || c.hd)
  let index = channels.findIndex((c) => c.id === want)
  const leave = () => App.go(p.from ? p.from.name : 'home', p.from ? p.from.params : undefined)
  if (index < 0) { setTimeout(leave, 0); return { el: h('div', { class: 'screen player' }) } }

  const video = h('video', { playsinline: true }) as HTMLVideoElement
  const engine = new Engine(video, false)
  const ready = new Map<string, Resolved>()
  let gen = 0, retries = 0, lastId = '', program: Program | null = null, upcoming: Program | null = null
  let hideTimer = 0, tickTimer = 0, aspect = 0
  const ASPECTS: Array<[string, string]> = [['contain', 'Fit'], ['cover', 'Zoom'], ['fill', 'Stretch']]
  let panelOpen = false, errorShown = false

  // ---- overlay
  const logoSlot = h('span'), chName = h('div', { class: 'chn' }), chNum = h('div', { class: 'chnum' })
  const star = h('span', { class: 'a', style: { marginLeft: '16px', color: 'var(--accent)' } }, icon('star', 26))
  const ptitle = h('div', { class: 'ptitle' }), pmeta = h('span'), tStart = h('span'), tEnd = h('span')
  const bar = progressBar(0)
  const timeRow = h('div', { class: 'ptime' }, tStart, bar, tEnd)
  const ctl = (ic: string, label: string | null, click: () => void) => h('div', { class: 'btn' + (label ? '' : ' round'), f: true, onclick: click }, icon(ic, 24), label)
  const upB = ctl('up', null, () => tune(index - 1)), downB = ctl('down', null, () => tune(index + 1))
  const favB = ctl('starO', 'Favourite', () => { Catalog.toggleFav(channels[index].id); labels(); bump() })
  const qualB = ctl('quality', 'Quality', () => openQuality())
  const audB = ctl('audio', 'Audio', () => openAudio())
  const aspB = ctl('aspect', 'Fit', () => { aspect = (aspect + 1) % ASPECTS.length; video.style.objectFit = ASPECTS[aspect][0]; labels(); bump() })
  const guideB = ctl('guide', 'Guide', () => { const c = channels[index]; App.go('guide', { id: c.id, from: { name: 'player', params: p } }) })
  const controls = h('div', { class: 'ctl' }, upB, downB, favB, qualB, audB, aspB, guideB)
  const hud = h('div', { class: 'hud hide' },
    h('div', { class: 'grad' }),
    h('div', { class: 'btn small close', f: true, onclick: () => leave() }, icon('left', 22), 'Back to channels'),
    h('div', { class: 'info' },
      h('div', { class: 'ch' }, logoSlot, h('div', null, chName, chNum), star),
      ptitle, h('div', { class: 'pmeta' }, liveBadge(), pmeta), timeRow, controls,
      h('div', { class: 'tip' }, '▲ ▼ change channel   ·   OK controls   ·   Back to leave')))

  // ---- panel + error
  const panelTitle = h('h3'), panelList = h('div')
  const panel = h('div', { class: 'panel hide' }, panelTitle, panelList)
  const errTitle = h('h2'), errMsg = h('p')
  const retryB = h('div', { class: 'btn', f: true, onclick: () => { hideError(); retries = 0; tune(index) } }, 'Try again')
  const errCard = h('div', { class: 'errcard hide' }, h('div', { class: 'wi' }, icon('warning', 56)), errTitle, errMsg,
    h('div', { class: 'bs' }, retryB, h('div', { class: 'btn', f: true, onclick: () => leave() }, 'Back to channels')))
  const loading = h('div', { style: { position: 'absolute', right: '96px', top: '70px', fontSize: '22px', color: 'var(--text2)' } })

  const el = h('div', { class: 'screen player' }, video, hud, loading, panel, errCard)

  engine.onFirstFrame = () => { loading.textContent = ''; Catalog.clearBad(channels[index].id); retries = 0 }
  engine.onTracks = () => labels()
  engine.onError = (msg) => {
    ready.delete(channels[index].id)
    if (retries++ < 2) { setMeta('Reconnecting…'); reveal(true); tune(index) }
    else { Catalog.markBad(channels[index].id); showError('Playback error', 'The stream stopped (' + String(msg).slice(0, 60) + '). Check your connection and try again.') }
  }

  // ---- hud
  const hudOn = () => !hud.classList.contains('hide')
  function reveal(sticky: boolean) {
    clearTimeout(hideTimer); hud.classList.remove('hide'); hud.style.opacity = '1'
    clearInterval(tickTimer); tickTimer = window.setInterval(progress, 15000)
    if (!sticky) hideTimer = window.setTimeout(hideHud, 7000)
  }
  function hideHud() { if (!panelOpen && !errorShown) { hud.classList.add('hide'); setFocus(null) } }
  function bump() { if (hudOn() && !panelOpen) { clearTimeout(hideTimer); hideTimer = window.setTimeout(hideHud, 7000) } }
  function progress() { const pr = program; if (!pr) return; (bar.firstChild as HTMLElement).style.width = Math.max(0, Math.min(1, (Date.now() - pr.start) / Math.max(1, pr.end - pr.start))) * 100 + '%' }
  function setMeta(s: string) { pmeta.textContent = s }
  function labels() {
    const ch = channels[index]
    const fav = Catalog.isFav(ch.id)
    favB.lastChild!.textContent = fav ? 'Favourited' : 'Favourite'
    favB.firstChild!.parentNode!.replaceChild(icon(fav ? 'star' : 'starO', 24), favB.firstChild!)
    star.style.display = fav ? '' : 'none'
    const hh = engine.levelHeight
    qualB.lastChild!.textContent = 'Quality: ' + (engine.autoLevel || !hh ? 'Auto' : hh + 'p')
    aspB.lastChild!.textContent = ASPECTS[aspect][1]
    ;(guideB as HTMLElement).style.display = ch.url ? 'none' : ''
    qualB.style.display = audB.style.display = ch.url ? 'none' : ''
  }
  function showLoading() {
    const ch = channels[index]
    clear(logoSlot); logoSlot.appendChild(logoBox(ch, 64)); chName.textContent = ch.name; chNum.textContent = String(Catalog.numberOf(ch))
    ptitle.textContent = ch.name; setMeta('Loading…'); tStart.textContent = tEnd.textContent = ''; (bar.firstChild as HTMLElement).style.width = '0%'
    labels(); reveal(true); loading.textContent = 'Loading…'
  }
  function showInfo(focusControls = false) {
    const ch = channels[index]
    clear(logoSlot); logoSlot.appendChild(logoBox(ch, 64)); chName.textContent = ch.name; chNum.textContent = String(Catalog.numberOf(ch))
    ptitle.textContent = program ? program.title : ch.name
    setMeta([langName(ch.lang), ch.hd ? 'HD' : '', program ? 'Until ' + fmtTime(program.end) : '', upcoming ? 'Next: ' + upcoming.title : ''].filter(Boolean).join('  ·  '))
    tStart.textContent = program ? fmtTime(program.start) : ''; tEnd.textContent = program ? fmtTime(program.end) : ''
    timeRow.style.visibility = program ? '' : 'hidden'; progress(); labels(); reveal(false)
    if (focusControls) setFocus(ch.url ? favB : qualB)
  }

  // ---- panel (quality / audio)
  function openPanel(title: string, items: Array<{ label: string; sub: string; checked: boolean }>, pick: (i: number) => void) {
    panelTitle.textContent = title; clear(panelList)
    items.forEach((it, i) => panelList.appendChild(h('div', { class: 'popt', f: true, onclick: () => pick(i) }, h('div', { class: 'l' }, h('div', { class: 'a1 p' }, it.label), it.sub ? h('div', { class: 'a2 s' }, it.sub) : null), it.checked ? icon('check', 26) : null)))
    clearTimeout(hideTimer); panelOpen = true; panel.classList.remove('hide')
    const sel = Math.max(0, items.findIndex((x) => x.checked))
    setFocus(panelList.children[sel] as HTMLElement)
  }
  function closePanel() { panelOpen = false; panel.classList.add('hide'); if (hudOn()) { setFocus(qualB); bump() } }
  function openQuality() {
    const lv = engine.levels()
    if (!lv.length) { setMeta('No quality options on this channel'); reveal(false); return }
    const auto = engine.autoLevel
    const items = [{ label: 'Auto', sub: 'Adapts to your connection', checked: auto }].concat(lv.map((l) => ({ label: l.height ? l.height + 'p' : (l.bitrate / 1e6).toFixed(1) + ' Mbps', sub: l.height ? (l.bitrate / 1e6).toFixed(1) + ' Mbps' : '', checked: !auto && engine.hls!.currentLevel === l.index })))
    openPanel('Quality', items, (i) => { engine.setLevel(i === 0 ? -1 : lv[i - 1].index); closePanel(); labels() })
  }
  function openAudio() {
    const au = engine.audios()
    if (au.length < 1) { setMeta('No alternative audio tracks on this channel'); reveal(false); return }
    openPanel('Audio', au.map((a) => ({ label: a.name, sub: '', checked: a.selected })), (i) => { const a = au[i]; if (a.key) Store.setAudioPref(channels[index].id, a.key); engine.setAudio(a.index); closePanel() })
  }

  function showError(t: string, m: string) {
    errorShown = true; errTitle.textContent = t; errMsg.textContent = m; errCard.classList.remove('hide'); clearTimeout(hideTimer); hud.classList.add('hide'); panel.classList.add('hide'); panelOpen = false
    setFocus(retryB)
  }
  function hideError() { errorShown = false; errCard.classList.add('hide') }

  // ---- playback
  async function tune(i: number) {
    index = (i + channels.length) % channels.length
    const ch = channels[index]; const g = ++gen
    if (ch.id !== lastId) { retries = 0; aspect = aspect } // new channel: fresh retry budget
    hideError(); lastId = ch.id; program = null; upcoming = null
    showLoading()
    try {
      const cached = ready.get(ch.id); ready.delete(ch.id)
      const r = cached && Date.now() - cached.at < 90_000 ? cached : await resolve(ch)
      if (g !== gen) return
      program = r.cur; upcoming = r.nxt; Catalog.pushRecent(ch.id)
      engine.load(ch, r)
      showInfo()
      prefetch(index + 1); prefetch(index - 1)
    } catch (e) {
      if (g !== gen) return
      const msg = String((e as Error).message || '')
      showError('Channel unavailable', (ch.url ? `${ch.name} can't be played right now. Free streams go offline often, so try another channel.`
        : msg.indexOf('419') >= 0 ? 'Your Jio session has expired. Sign out and sign in again from Settings.'
        : `${ch.name} can't be played right now. It may not be part of your plan, or the broadcaster is off air.`) + '\n(' + msg.slice(0, 90) + ')')
    }
  }
  /** Warms the next/previous Jio channel so a channel change doesn't wait on two round trips. */
  function prefetch(i: number) {
    const ch = channels[(i + channels.length) % channels.length]
    const hit = ready.get(ch.id)
    if (ch.url || (hit && Date.now() - hit.at < 60_000)) return
    ready.forEach((v, k) => { if (Date.now() - v.at > 90_000) ready.delete(k) })
    resolve(ch).then((r) => ready.set(ch.id, r)).catch(() => {})
  }

  tune(index)

  return {
    el,
    mount() { video.style.objectFit = ASPECTS[aspect][0] },
    unmount() { gen++; engine.stop(); clearTimeout(hideTimer); clearInterval(tickTimer) },
    onBack() {
      if (panelOpen) { closePanel(); return true }
      if (errorShown) { leave(); return true }
      if (hudOn()) { clearTimeout(hideTimer); hideHud(); return true }
      leave(); return true
    },
    onKey(a) {
      if (panelOpen || errorShown) return false // let the sheet / error buttons handle focus
      bump()
      if (a.type === 'chUp' || (a.type === 'dir' && a.dir === 'up')) { tune(index - 1); return true }
      if (a.type === 'chDown' || (a.type === 'dir' && a.dir === 'down')) { tune(index + 1); return true }
      if (a.type === 'ok') { if (!hudOn() || !focused() || !el.contains(focused())) { showInfo(true); return true } return false }
      if (a.type === 'dir' && !hudOn()) { showInfo(true); return true }
      return false
    },
  }
}
