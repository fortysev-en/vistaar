import { Catalog, Epg } from '../catalog'
import { clear, h, icon } from '../dom'
import { focused, setFocus } from '../nav/focus'
import * as TL from '../timeline'
import type { Channel, Program } from '../types'
import { App, makeHeader, type Name, type Screen } from './app'
import { Engine, resolve } from '../playback'
import { artBg, fmtTime, liveBadge, logoBox } from './widgets'

/**
 * Programme guide: a time grid. Channels down the left, a 3-hour window of programmes across, the focused programme described above.
 * Left/Right move between programmes (and slide the window at its edges), Up/Down between channels at the same time. Port of GuideActivity.
 */

const ROWS = 6 // rows on screen; the window over the channel list slides as focus moves
const ROW_STEP = 96 // 88 + 8
const STEP_MS = TL.STEP_MIN * 60_000
const DAYS = 7 // today and the next six

interface Blk { el: HTMLElement; p: Program | null; b: TL.Block | null }
interface Row { idx: number; ch: Channel; el: HTMLElement; tl: HTMLElement; programs: Program[] | null; blocks: Blk[]; pendingAt: number | null; day: number }

export function guideScreen(p?: { id?: string; from?: { name: Name; params?: any } }): Screen {
  const header = makeHeader('guide')
  let day = 0
  let winStart = TL.openStart(Date.now(), 0)
  let channels: Channel[] = []
  let top = 0 // index of the first visible channel
  let rows: Row[] = []
  let initial = false
  let selTimer = 0
  const info = new WeakMap<HTMLElement, { row: Row; i: number }>()

  // ---- selected programme
  const video = h('video', { muted: true, playsinline: true }) as HTMLVideoElement
  const art = h('div', { class: 'gart' }, video)
  let engine: Engine | null = null
  let dwell = 0
  let previewId = ''
  const live = liveBadge()
  const when = h('span')
  const title = h('div', { class: 'gtitle' })
  const desc = h('div', { class: 'gdesc' })
  const watch = h('div', { class: 'btn small', f: true }, icon('play', 22), 'Watch Live')
  const panel = h('div', { class: 'gpanel' }, art, h('div', { class: 'gtx' }, h('div', { class: 'gwhen' }, live, when), title, desc), watch)

  // ---- time bar
  const dayLabel = h('span')
  const prev = h('div', { class: 'gchev', f: true, onclick: () => setDay(day - 1) }, icon('left', 20))
  const next = h('div', { class: 'gchev', f: true, onclick: () => setDay(day + 1) }, icon('right', 20))
  const ticksEl = h('div', { class: 'gticks' })
  const nowPill = h('div', { class: 'gnowp' })
  const earlier = h('div', { class: 'gb', f: true, onclick: () => shift(-STEP_MS, null) }, icon('left', 20))
  const nowBtn = h('div', { class: 'gb', f: true, onclick: () => setDay(0, true) }, 'Now')
  const later = h('div', { class: 'gb', f: true, onclick: () => shift(STEP_MS, null) }, icon('right', 20))
  const bar = h('div', { class: 'gbar' }, h('div', { class: 'gday' }, prev, dayLabel, next), h('div', { class: 'gtime' }, ticksEl, nowPill, h('div', { class: 'gbtns' }, earlier, nowBtn, later)))

  // ---- grid
  const grid = h('div', { class: 'ggrid' })
  const empty = h('div', { class: 'empty hide' }, 'Checking which channels your plan includes…')
  const el = h('div', { class: 'screen' }, panel, bar, grid, empty, header.el)

  const dayNames = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat']
  const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']
  const dayText = (d: number) => { const t = new Date(TL.dayStart(Date.now(), d)); return dayNames[t.getDay()] + ' ' + t.getDate() }

  function paintBar() {
    const now = Date.now()
    const t = new Date(TL.dayStart(now, day))
    dayLabel.textContent = day === 0 ? 'Today · ' + dayText(0) : day === 1 ? 'Tomorrow · ' + dayText(1) : dayText(day) + ' ' + months[t.getMonth()]
    prev.style.opacity = day === 0 ? '.3' : ''; next.style.opacity = day === DAYS - 1 ? '.3' : ''
    clear(ticksEl)
    TL.ticks(winStart).forEach((k) => ticksEl.appendChild(h('span', { class: 'gtick mono', style: { left: k.x + 'px' } }, fmtTime(k.t).replace(' pm', '').replace(' am', ''))))
    const nx = day === 0 ? TL.nowX(winStart, now) : null
    if (nx === null) nowPill.style.display = 'none'
    else { const d = new Date(now); nowPill.style.display = ''; nowPill.style.left = Math.max(0, nx - 34) + 'px'; nowPill.textContent = (d.getHours() % 12 || 12) + ':' + ('0' + d.getMinutes()).slice(-2) }
  }

  // ---- selection panel
  function showSel(ch: Channel | null, pr: Program | null) {
    if (!ch) { title.textContent = ''; when.textContent = ''; desc.textContent = ''; live.style.visibility = 'hidden'; watch.className = 'btn small hide'; stopPreview(); return }
    preview(ch)
    const now = Date.now()
    const airing = !pr || (now >= pr.start && now < pr.end)
    art.style.background = artBg(ch.id)
    live.style.display = airing ? '' : 'none'; live.style.visibility = ''
    title.textContent = pr ? pr.title : Catalog.label(ch)
    when.textContent = pr ? Catalog.label(ch) + '  ·  ' + (airing ? 'Now  ·  ' : '') + fmtTime(pr.start) + ' – ' + fmtTime(pr.end) : Catalog.label(ch) + '  ·  ' + Catalog.numberOf(ch)
    desc.textContent = pr && pr.desc ? pr.desc : pr ? '' : 'No schedule is available for this channel right now.'
    watch.className = 'btn small' + (airing ? '' : ' hide')
    watch.onclick = () => play(ch)
  }
  /** Muted live picture of the channel in the art tile; restarts only when the channel changes. */
  function preview(ch: Channel) {
    if (previewId === ch.id) return
    previewId = ch.id; clearTimeout(dwell); video.classList.remove('on')
    if (engine) engine.stop()
    dwell = window.setTimeout(async () => {
      try {
        const r = await resolve(ch)
        if (previewId !== ch.id) return
        if (!engine) { engine = new Engine(video, true); engine.onFirstFrame = () => video.classList.add('on'); engine.onError = () => video.classList.remove('on') }
        engine.load(ch, r)
      } catch { /* the artwork stays */ }
    }, 800)
  }
  function stopPreview() { clearTimeout(dwell); previewId = ''; video.classList.remove('on'); if (engine) engine.stop() }
  function play(ch: Channel) { App.go('player', { id: ch.id, from: { name: 'guide', params: p } }) }

  // ---- rows
  function drawRows() {
    clear(grid); rows = []
    for (let i = 0; i < ROWS; i++) {
      const idx = top + i
      if (idx >= channels.length) break
      rows.push(makeRow(idx, i))
    }
  }
  function makeRow(idx: number, slot: number): Row {
    const ch = channels[idx]
    const tl = h('div', { class: 'gtl' })
    const cell = h('div', { class: 'gcell' }, logoBox(ch, 52), h('div', { class: 'gci' }, h('div', { class: 'gn mono' }, Catalog.numberOf(ch)), h('div', { class: 'gnm' }, ch.name)))
    const el = h('div', { class: 'grow', style: { top: slot * ROW_STEP + 'px' } }, cell, tl)
    grid.appendChild(el)
    const row: Row = { idx, ch, el, tl, programs: null, blocks: [], pendingAt: null, day }
    const d = day
    const hit = Epg.cached(ch.id, d)
    if (hit) { row.programs = hit; layoutRow(row) }
    else Epg.load(ch.id, d).then((l) => { if (rows.indexOf(row) >= 0 && row.day === d) { row.programs = l; layoutRow(row) } })
    return row
  }
  function layoutRow(row: Row) {
    const pr = row.programs; if (!pr) return
    const hadFocus = !!focused() && row.tl.contains(focused())
    clear(row.tl)
    const now = Date.now()
    const out: Blk[] = []
    pr.forEach((x) => { const b = TL.block(winStart, x.start, x.end); if (b) out.push(blockEl(row, x, b, now, out.length)) })
    if (!out.length) out.push(blockEl(row, null, null, now, 0))
    out.forEach((k) => row.tl.appendChild(k.el))
    row.blocks = out
    const nx = row.day === 0 ? TL.nowX(winStart, now) : null
    if (nx !== null) row.tl.appendChild(h('div', { class: 'gnow', style: { left: nx + 'px' } }))
    const at = row.pendingAt
    if (at !== null) { row.pendingAt = null; focusAt(row, at) } else if (hadFocus) focusAt(row, now)
  }
  function blockEl(row: Row, pr: Program | null, b: TL.Block | null, now: number, i: number): Blk {
    const airing = !!pr && now >= pr.start && now < pr.end
    const past = !!pr && pr.end <= now
    const w = b ? b.w : TL.GRID_W - TL.GAP
    const kids: Array<HTMLElement | null> = []
    if (airing && b) kids.push(h('div', { class: 'gtint', style: { width: Math.max(0, Math.min(b.w, Math.round((now - Math.max(pr!.start, winStart)) / 60000 * TL.PPM))) + 'px' } }))
    kids.push(h('div', { class: 'gt' }, (b && b.cutLeft ? '‹ ' : '') + (pr ? pr.title : 'No schedule available')))
    if (pr && w > 170) kids.push(h('div', { class: 'gs' }, fmtTime(pr.start) + ' – ' + fmtTime(pr.end)))
    const el = h('div', { class: 'gblk' + (airing ? ' air' : '') + (past ? ' past' : ''), f: true, style: { left: (b ? b.x : 0) + 'px', width: w + 'px' }, onclick: () => { if (airing || !pr) play(row.ch); else showSel(row.ch, pr) } }, kids)
    el.addEventListener('vfocus', () => { clearTimeout(selTimer); selTimer = window.setTimeout(() => showSel(row.ch, pr), 120) })
    info.set(el, { row, i })
    return { el, p: pr, b }
  }
  const centerTime = (k: Blk) => (k.b ? winStart + (k.b.x + k.b.w / 2) / TL.PPM * 60_000 : winStart + TL.SPAN_MIN * 30_000)

  /** Focuses the programme at time t in a row (or the nearest one); waits for the programmes if they are still loading. */
  function focusAt(row: Row, t: number) {
    if (!row.programs) { row.pendingAt = t; return }
    let best: Blk | null = null
    row.blocks.forEach((k) => { if (k.p && t >= k.p.start && t < k.p.end) best = best || k })
    if (!best) { let d = Infinity; row.blocks.forEach((k) => { const x = Math.abs(centerTime(k) - t); if (x < d) { d = x; best = k } }) }
    if (best) setFocus((best as Blk).el)
  }
  function focusRow(idx: number, t: number) {
    if (idx < top) top = idx; else if (idx >= top + ROWS) top = idx - ROWS + 1
    if (!rows.some((r) => r.idx === idx)) drawRows()
    const row = rows.filter((r) => r.idx === idx)[0]
    if (row) focusAt(row, t)
  }

  // ---- window and day
  function shift(delta: number, keep: { id: string; t: number } | null) {
    const nxt = TL.clampStart(winStart + delta, Date.now(), day)
    if (nxt === winStart) return
    winStart = nxt; paintBar()
    rows.forEach((r) => layoutRow(r))
    if (keep) { const i = channels.findIndex((c) => c.id === keep.id); if (i >= 0) focusRow(i, keep.t) }
  }
  function setDay(d: number, jump = false) {
    const nd = Math.max(0, Math.min(DAYS - 1, d))
    if (nd === day && !jump) return
    day = nd; winStart = TL.openStart(Date.now(), day); paintBar(); drawRows()
  }

  function refresh() {
    header.update()
    const l = Catalog.playableChannels()
    empty.className = 'empty' + (l.length ? ' hide' : '')
    if (l.length === channels.length && l.every((c, i) => c.id === channels[i].id)) return
    channels = l; drawRows()
    if (!initial && l.length) {
      initial = true
      const at = p && p.id ? l.findIndex((c) => c.id === p.id) : -1
      const rec = Catalog.recents().map((r) => l.findIndex((c) => c.id === r.id)).filter((i) => i >= 0)[0]
      focusRow(at >= 0 ? at : rec !== undefined ? rec : 0, Date.now())
    }
  }

  return {
    el,
    mount() { paintBar(); showSel(null, null); Catalog.onChange = refresh; Catalog.start(false); refresh() },
    unmount() { clearTimeout(selTimer); stopPreview(); if (engine) engine = null; Catalog.onChange = null },
    onBack() { if (p && p.from && p.from.name === 'player') { App.go('player', p.from.params); return true } return false },
    onKey(a) {
      const f = focused()
      const at = f ? info.get(f) : undefined
      if (a.type !== 'dir' || !at) return false
      const { row, i } = at
      const me = row.blocks[i]
      if (a.dir === 'left') { if (i > 0) setFocus(row.blocks[i - 1].el); else shift(-STEP_MS, { id: row.ch.id, t: (me.p ? me.p.start : winStart) - 1000 }); return true }
      if (a.dir === 'right') { if (i < row.blocks.length - 1) setFocus(row.blocks[i + 1].el); else shift(STEP_MS, { id: row.ch.id, t: (me.p ? me.p.end : TL.windowEnd(winStart)) + 1000 }); return true }
      const to = a.dir === 'down' ? row.idx + 1 : row.idx - 1
      if (to >= channels.length) return true // last channel: stay put
      if (to < 0) return false // first channel: up goes on to the time bar
      focusRow(to, centerTime(me)); return true
    },
  }
}
