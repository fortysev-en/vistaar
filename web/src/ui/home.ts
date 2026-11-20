import { Catalog, Epg } from '../catalog'
import { clear, h, icon } from '../dom'
import { focused, focusFirst, setFocus } from '../nav/focus'
import { Store } from '../store'
import type { Channel } from '../types'
import { App, makeHeader, type Screen } from './app'
import { artBg, card, fmtTime, langName, liveBadge, logoBox, progressBar } from './widgets'
import { Free } from '../free'

/** Home: a hero for what you watched last, then rows (recent, favourites, free channels, languages). Port of HomeActivity. */
export function homeScreen(): Screen {
  const header = makeHeader('home')
  const jio = Store.loggedIn
  let heroCh: Channel | null = null
  let sig = ''

  const backdrop = h('div', { class: 'homebg' })
  const shade1 = h('div', { class: 'homebg', style: { background: 'linear-gradient(90deg,rgba(7,8,10,.94),rgba(7,8,10,.7),rgba(7,8,10,0))' } })
  const shade2 = h('div', { class: 'homebg', style: { background: 'linear-gradient(180deg,rgba(7,8,10,.55),rgba(7,8,10,0) 25%,rgba(7,8,10,0) 60%,#07080a)' } })
  const hero = h('div', { class: 'hero' })
  const rows = h('div', { style: { position: 'absolute', left: '0', top: '790px', width: '1920px' } })
  const col = h('div', { class: 'hcol', style: { paddingTop: '0' } }, hero, rows)
  const scroll = h('div', { class: 'hscroll' }, col)
  const loading = h('div', { class: 'empty', style: { left: '96px', top: '260px', fontSize: '28px' } }, 'Loading your channels…')
  const el = h('div', { class: 'screen' }, backdrop, shade1, shade2, scroll, loading, header.el)

  const frac = (s: number, e: number) => Math.max(0, Math.min(1, (Date.now() - s) / Math.max(1, e - s)))
  const homePool = (): Channel[] => { const p = Catalog.playableChannels(); return p.length ? p : Catalog.scanning ? [] : Catalog.freeChannels() }
  const play = (c: Channel) => App.go('player', { id: c.id, list: 'jio', from: { name: 'home' } })

  function bindHero(ch: Channel) {
    heroCh = ch
    backdrop.style.background = artBg(ch.id)
    clear(hero)
    const title = h('div', { class: 'big' }, ch.name), sub = h('div', { class: 'hs' }, langName(ch.lang) + (ch.hd ? ' · HD' : ''))
    const until = h('div', { class: 'until' }, liveBadge(), h('span', null, 'Live now')), desc = h('div', { class: 'hd' }), time = h('div', { class: 'ht hide' })
    const favIc = h('span')
    const paintFav = () => { clear(favIc); favIc.appendChild(icon(Catalog.isFav(ch.id) ? 'star' : 'starO', 28, Catalog.isFav(ch.id) ? 'var(--accent)' : undefined)) }
    paintFav()
    const watch = h('div', { class: 'btn', f: true, 'data-watch': true, onclick: () => play(ch) }, icon('play', 24), 'Watch Live')
    const guide = h('div', { class: 'btn', f: true, onclick: () => App.go('guide') }, icon('guide', 24), 'Guide')
    const fav = h('div', { class: 'btn round', f: true, onclick: () => { Catalog.toggleFav(ch.id); paintFav(); sig = ''; render() } }, favIc)
    hero.appendChild(h('div', { class: 'who' }, logoBox(ch, 56), h('span', null, ch.name), h('span', { class: 'nmb' }, Catalog.numberOf(ch))))
    hero.appendChild(title); hero.appendChild(sub); hero.appendChild(until); hero.appendChild(desc); hero.appendChild(time)
    hero.appendChild(h('div', { class: 'hb' }, watch, jio ? guide : null, fav))
    if (ch.url && !Catalog.twinOf(ch.id)) desc.textContent = 'Free channel: it has no programme guide.'
    Epg.load(ch.id).then((l) => {
      if (heroCh !== ch) return
      const p = Epg.now(l); if (!p) return
      title.textContent = p.title
      const nx = Epg.next(l); sub.textContent = nx ? 'Up next: ' + nx.title + ' at ' + fmtTime(nx.start) : langName(ch.lang) + (ch.hd ? ' · HD' : '')
      const left = Math.max(0, Math.round((p.end - Date.now()) / 60000))
      until.lastChild!.textContent = 'Until ' + fmtTime(p.end) + '  ·  ' + left + ' min left'
      desc.textContent = p.desc
      clear(time); time.className = 'ht'; time.appendChild(h('span', null, fmtTime(p.start))); time.appendChild(progressBar(frac(p.start, p.end))); time.appendChild(h('span', null, fmtTime(p.end)))
    })
  }

  function strip(title: string, list: Channel[], tall = false): HTMLElement | null {
    if (!list.length) return null
    const st = h('div', { class: 'strip' })
    list.forEach((c) => {
      const cd = card(c, c.url ? Free.catName(c.lang) : langName(c.lang), () => play(c), () => { Catalog.toggleFav(c.id); sig = ''; render() }, tall)
      cd.addEventListener('vfocus', () => reveal(cd, st))
      st.appendChild(cd)
    })
    return h('div', { class: 'sec' }, h('h2', null, title), st)
  }

  /** Keep the focused card on screen: scroll the strip sideways and the page up/down. */
  function reveal(cd: HTMLElement, st: HTMLElement) {
    const x = cd.offsetLeft
    st.style.transition = 'transform .2s'; st.style.transform = `translateX(${-Math.max(0, x - 96 - 400)}px)`
    const sec = st.parentElement as HTMLElement
    const y = sec.offsetTop + 790
    col.style.transform = `translateY(${-Math.max(0, y - 150)}px)`
    setHeader(true)
  }
  function setHeader(scrolled: boolean) { header.el.style.opacity = scrolled ? '0' : '1'; header.el.style.pointerEvents = scrolled ? 'none' : '' }

  function render() {
    header.update()
    const pool = homePool()
    loading.style.display = pool.length ? 'none' : ''
    hero.style.display = pool.length ? '' : 'none'
    if (!pool.length) { clear(rows); return }
    const target = Catalog.recents()[0] || Catalog.favourites()[0] || pool[0]
    if (!heroCh || heroCh.id !== target.id) bindHero(target)
    const recents = Catalog.recents(), favs = Catalog.favourites(), free = Catalog.freeChannels()
    const byLang: { [k: number]: Channel[] } = {}
    pool.forEach((c) => { (byLang[c.lang] = byLang[c.lang] || []).push(c) })
    const order = Object.keys(byLang).map(Number).sort((a, b) => (a === 1 ? 0 : a === 6 ? 1 : 2) - (b === 1 ? 0 : b === 6 ? 1 : 2) || byLang[b].length - byLang[a].length).slice(0, 3)
    const specs: Array<[string, Channel[], boolean]> = [
      [recents.length ? 'Recently watched' : 'Channels for you', recents.length ? recents : pool.slice(0, 12), false],
      ['Your favourites', favs, true],
      ['Free TV  ·  ' + free.length + ' channels', free.slice(0, 14), true],
    ]
    order.forEach((l) => specs.push([langName(l) + '  ·  ' + byLang[l].length + ' channels', byLang[l].slice(0, 14), true]))
    const next = specs.map((s) => s[0] + ':' + s[1].map((c) => c.id + (Catalog.isFav(c.id) ? '*' : '')).join(',')).join('|')
    if (next === sig) return
    sig = next
    const keepFocus = focused() && rows.contains(focused())
    clear(rows)
    specs.forEach((s, i) => { const sct = strip(s[0], s[1], i > 0 || s[1] === pool.slice(0, 12)); if (sct) rows.appendChild(sct) })
    if (!keepFocus && !focused()) focusFirst('[data-watch]')
  }

  return {
    el,
    mount() { Catalog.onChange = render; Catalog.start(false); render(); focusFirst('[data-watch]') },
    unmount() { Catalog.onChange = null },
    onBack() {
      const f = focused()
      if (f && rows.contains(f)) { col.style.transform = 'translateY(0)'; setHeader(false); focusFirst('[data-watch]'); return true }
      return false
    },
    onKey(a) {
      // moving up out of the first row returns to the hero and the header
      if (a.type === 'dir' && a.dir === 'up') { const f = focused(); if (f && hero.contains(f)) { setHeader(false); col.style.transform = 'translateY(0)' } }
      if (a.type === 'dir' && a.dir === 'up') { const f = focused(); if (f && rows.contains(f)) { const first = rows.querySelector('.card'); if (first && f.parentElement === (first as HTMLElement).parentElement) { col.style.transform = 'translateY(0)'; setHeader(false); focusFirst('[data-watch]'); return true } } }
      if (a.type === 'dir' && a.dir === 'down') { const f = focused(); if (f && hero.contains(f)) { const first = rows.querySelector('.card') as HTMLElement | null; if (first) { setFocus(first); return true } } }
      return false
    },
  }
}
