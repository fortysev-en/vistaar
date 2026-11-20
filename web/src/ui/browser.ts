import { Catalog, Epg } from '../catalog'
import { clear, h, icon } from '../dom'
import { focused, focusFirst, setFocus } from '../nav/focus'
import { Engine, resolve } from '../playback'
import { Store } from '../store'
import { GENRES, type Channel, type Program } from '../types'
import { App, makeHeader, type Name, type Screen } from './app'
import { artBg, fmtTime, langName, liveBadge, logoBox, progressBar, VList } from './widgets'
import { Free } from '../free'

/** Jio TV, Free TV and HD: categories | channel list | live preview. One screen, three pools (port of LiveTvActivity / FreeTvActivity / HdTvActivity). */

type Kind = 'jio' | 'free' | 'hd'
interface Cat { key: string; label: string; count: number }
const memo: { [k: string]: { cat: string; byGenre: boolean } } = {}

export function browserScreen(kind: Kind): Screen {
  const name: Name = kind === 'jio' ? 'jiotv' : kind === 'free' ? 'freetv' : 'hd'
  const m = memo[kind] || (memo[kind] = { cat: '*', byGenre: Store.str('liveByGenre') !== '0' })
  let cats: Cat[] = []
  let cat = m.cat
  let byGenre = m.byGenre
  let preview: Channel | null = null
  let dwell = 0
  let selTimer = 0
  let engine: Engine | null = null

  const header = makeHeader(name)
  const catBox = h('div', { class: 'cats' + (kind === 'jio' ? '' : ' notabs') })
  const tabs = kind === 'jio' ? h('div', { class: 'tabs' }) : null
  const empty = h('div', { class: 'empty hide' })

  const list = new VList<Channel>({
    rowH: 104,
    render: (ch) => rowEl(ch),
    onFocus: (ch) => showPreview(ch),
    onOpen: (ch) => play(ch),
    onHold: (ch) => { Catalog.toggleFav(ch.id); list.refresh() },
  })

  // ---- preview panel
  const video = h('video', { muted: true, playsinline: true }) as HTMLVideoElement
  const pArt = h('div', { class: 'art' }, video, h('div', { class: 'shade' }), h('div', { class: 'tl' }, liveBadge()))
  const pLogo = h('div', { class: 'bl' })
  const pLang = h('div', { class: 'br' })
  pArt.appendChild(pLogo); pArt.appendChild(pLang)
  const pSub = h('div', { class: 'sub' }), pTitle = h('div', { class: 'title' }), pMeta = h('div', { class: 'meta' })
  const pTm = h('div', { class: 'tm hide' }), pDesc = h('div', { class: 'desc' })
  const watch = h('div', { class: 'btn small', f: true, onclick: () => preview && play(preview) }, icon('play', 22), 'Watch')
  const acts = h('div', { class: 'acts' }, watch, kind === 'jio' ? h('div', { class: 'btn small ghost', f: true, onclick: () => App.go('guide') }, 'Schedule') : null)
  const aside = h('div', { class: 'col-aside aside' }, pArt, pSub, pTitle, pMeta, pTm, pDesc, acts,
    h('div', { class: 'hint' }, 'OK to watch  ·  Hold OK to favourite  ·  ◀ categories'))

  const body = h('div', { class: 'body' },
    h('div', { class: 'col-cats' }, tabs, catBox),
    h('div', { class: 'col-list' }, list.el, empty),
    aside)
  const el = h('div', { class: 'screen' }, body, header.el)

  // ---- data
  function pool(): Channel[] {
    if (kind === 'jio') return Catalog.playableChannels()
    if (kind === 'free') return Catalog.freeChannels()
    return Catalog.playableChannels().filter((c) => c.hd).concat(Catalog.freeChannels().filter((c) => c.hd))
  }
  function catOf(c: Channel): string {
    if (kind === 'free') return Free.catName(c.lang)
    if (kind === 'hd') return c.url ? 'Free TV' : 'Jio TV'
    if (byGenre) return String(GENRES.some((g) => g[0] === c.cat) ? c.cat : 0)
    return String(c.lang)
  }
  function labelOf(key: string): string {
    if (kind === 'jio') return byGenre ? (GENRES.filter((g) => String(g[0]) === key)[0] || [0, 'Other'])[1] as string : langName(Number(key))
    return key
  }
  function orderOf(counts: { [k: string]: number }): string[] {
    const keys = Object.keys(counts)
    if (kind === 'free') return Free.cats.filter((c) => counts[c])
    if (kind === 'hd') return ['Jio TV', 'Free TV'].filter((c) => counts[c])
    if (byGenre) return GENRES.map((g) => String(g[0])).concat('0').filter((k) => counts[k])
    return keys.sort((a, b) => (a === '1' ? 0 : a === '6' ? 1 : 2) - (b === '1' ? 0 : b === '6' ? 1 : 2) || counts[b] - counts[a])
  }

  function refresh() {
    header.update()
    const all = pool()
    const counts: { [k: string]: number } = {}
    all.forEach((c) => { const k = catOf(c); counts[k] = (counts[k] || 0) + 1 })
    const next: Cat[] = [{ key: '*', label: 'All channels', count: all.length }].concat(orderOf(counts).map((k) => ({ key: k, label: labelOf(k), count: counts[k] })))
    const sameKeys = next.length === cats.length && next.every((c, i) => c.key === cats[i].key)
    cats = next
    if (!cats.some((c) => c.key === cat)) cat = '*'
    if (sameKeys && catBox.children.length) { for (let i = 0; i < cats.length; i++) { const n = catBox.children[i].querySelector('.n'); if (n) n.textContent = String(cats[i].count) } paintCats() }
    else drawCats()
    const shown = cat === '*' ? all : all.filter((c) => catOf(c) === cat)
    const wasEmpty = list.items.length === 0
    list.setItems(shown, (c) => c.id)
    empty.className = 'empty' + (shown.length ? ' hide' : '')
    empty.textContent = emptyText()
    if (!preview || !shown.some((c) => c.id === preview!.id)) { if (shown.length) showPreview(shown[0]) }
    if (wasEmpty && shown.length) list.focusIndex(0) // first fill: land on the first channel, like the Android app
  }
  function emptyText(): string {
    if (kind === 'free') return Free.loading ? 'Loading the free channels…' : Free.error ? 'Couldn’t load the free channels. ' + Free.error : 'No channels in this list.'
    if (kind === 'jio') return Catalog.scanning ? 'Checking which channels your plan includes…' : 'No channels found for your plan and languages.'
    return Catalog.scanning || Free.loading ? 'Loading HD channels…' : 'No HD channels found.'
  }

  function drawCats() {
    clear(catBox)
    cats.forEach((c) => {
      const row = h('div', { class: 'cat' + (c.key === cat ? ' on' : ''), f: true, 'data-key': c.key, onclick: () => select(c.key) }, h('span', null, c.label), h('span', { class: 'n' }, c.count))
      row.addEventListener('vfocus', () => { ensureVisible(catBox, row); clearTimeout(selTimer); selTimer = window.setTimeout(() => { if (row.classList.contains('focus')) select(c.key) }, 220) })
      catBox.appendChild(row)
    })
    if (tabs) {
      clear(tabs)
      const tab = (label: string, genre: boolean) => h('div', { class: 'tab' + (byGenre === genre ? ' on' : ''), f: true, onclick: () => { if (byGenre !== genre) { byGenre = genre; m.byGenre = genre; Store.put('liveByGenre', genre ? '1' : '0'); cat = '*'; m.cat = cat; refresh(); const t = tabs.children[genre ? 0 : 1] as HTMLElement; setFocus(t) } } }, label)
      tabs.appendChild(tab('Genre', true)); tabs.appendChild(tab('Language', false))
    }
  }
  function paintCats() { Array.prototype.forEach.call(catBox.children, (r: HTMLElement) => { if (r.getAttribute('data-key') === cat) r.classList.add('on'); else r.classList.remove('on') }) }
  function ensureVisible(box: HTMLElement, row: HTMLElement) {
    const t = row.offsetTop, b = t + row.offsetHeight
    if (t < box.scrollTop) box.scrollTop = t - 8; else if (b > box.scrollTop + box.clientHeight) box.scrollTop = b - box.clientHeight + 8
  }
  function select(key: string) {
    if (cat === key) return
    cat = key; m.cat = key; paintCats()
    const all = pool()
    list.setItems(key === '*' ? all : all.filter((c) => catOf(c) === key))
    empty.className = 'empty' + (list.items.length ? ' hide' : '')
    if (list.items.length) showPreview(list.items[0])
  }

  // ---- rows
  function rowEl(ch: Channel): HTMLElement {
    const prog = h('div', { class: 'pr' }, kind === 'jio' ? langName(ch.lang) : langName(ch.lang) + (ch.hd ? ' · HD' : ''))
    const bar = progressBar(0); bar.style.visibility = 'hidden'
    const star = h('span', { class: 'st a' }, icon('star', 22)); star.style.display = Catalog.isFav(ch.id) ? '' : 'none'
    const bad = Catalog.isBad(ch.id)
    const row = h('div', null,
      h('span', { class: 'num s' }, Catalog.numberOf(ch)),
      logoBox(ch, 56),
      h('div', { class: 'info' }, h('div', { class: 'nmr' }, h('div', { class: 'nm p' }, bad ? ch.name + '  ·  may not play' : ch.name), star), prog, kind === 'jio' ? bar : null),
      liveBadge())
    row.querySelector('.logo')!.setAttribute('style', 'margin-left:0')
    if (!ch.url) Epg.load(ch.id).then((l) => { const p = Epg.now(l); if (!p) return; prog.textContent = p.title; bar.style.visibility = ''; (bar.firstChild as HTMLElement).style.width = frac(p) * 100 + '%' })
    return row
  }
  const frac = (p: Program) => Math.max(0, Math.min(1, (Date.now() - p.start) / Math.max(1, p.end - p.start)))

  // ---- preview
  function showPreview(ch: Channel) {
    if (preview && preview.id === ch.id) return
    preview = ch
    pArt.style.background = artBg(ch.id)
    clear(pLogo); pLogo.appendChild(logoBox(ch, 64))
    pLang.textContent = langName(ch.lang) + (ch.hd ? ' · HD' : '')
    pSub.textContent = ch.name + ' · ' + Catalog.numberOf(ch)
    pTitle.textContent = ch.name
    pMeta.textContent = ch.url ? (kind === 'hd' ? 'Free TV' : Free.catName(ch.lang)) + (ch.hd ? ' · HD' : '') + ' · a free stream, availability varies' : ''
    pDesc.textContent = ch.url ? 'A free stream from the community iptv-org playlist. Availability varies, and some channels may be offline.' : ''
    pTm.className = 'tm hide'
    if (!ch.url) Epg.load(ch.id).then((l) => {
      if (!preview || preview.id !== ch.id) return
      const p = Epg.now(l); if (!p) return
      pTitle.textContent = p.title; pMeta.textContent = 'Live · until ' + fmtTime(p.end); pDesc.textContent = p.desc
      clear(pTm); pTm.className = 'tm'; pTm.appendChild(h('span', null, fmtTime(p.start))); pTm.appendChild(progressBar(frac(p))); pTm.appendChild(h('span', null, fmtTime(p.end)))
    })
    schedulePreview(ch)
  }
  function schedulePreview(ch: Channel) {
    clearTimeout(dwell); video.classList.remove('on')
    if (engine) engine.stop()
    dwell = window.setTimeout(async () => {
      try {
        const r = await resolve(ch)
        if (!preview || preview.id !== ch.id) return
        if (!engine) { engine = new Engine(video, true); engine.onFirstFrame = () => video.classList.add('on'); engine.onError = () => video.classList.remove('on') }
        engine.load(ch, r)
      } catch { /* artwork stays */ }
    }, 800)
  }

  function play(ch: Channel) {
    if (engine) engine.stop()
    App.go('player', { id: ch.id, list: kind, from: { name, params: undefined } })
  }

  function focusCat() {
    const sel = Array.prototype.filter.call(catBox.children, (r: HTMLElement) => r.getAttribute('data-key') === cat)[0] as HTMLElement | undefined
    if (sel) setFocus(sel)
  }

  return {
    el,
    mount() { Catalog.onChange = refresh; Catalog.start(false); refresh(); if (list.items.length) list.focusIndex(0); else focusFirst() },
    unmount() { clearTimeout(dwell); clearTimeout(selTimer); if (engine) { engine.stop(); engine = null } Catalog.onChange = null },
    onKey(a) {
      const f = focused()
      if (a.type === 'dir' && f && list.contains(f)) {
        if (a.dir === 'up' || a.dir === 'down') return list.step(a.dir)
        if (a.dir === 'left') { focusCat(); return true }
      }
      if (a.type === 'dir' && a.dir === 'right' && f && f.classList.contains('cat') && list.items.length) { list.focusIndex(0); return true } // categories open onto the first channel
      return false
    },
  }
}
