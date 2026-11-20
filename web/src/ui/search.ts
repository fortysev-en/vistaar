import { Catalog } from '../catalog'
import { clear, h, icon } from '../dom'
import { focusFirst } from '../nav/focus'
import { Store } from '../store'
import { Free } from '../free'
import type { Channel } from '../types'
import { App, makeHeader, type Screen } from './app'
import { keyboard } from './keyboard'
import { card, langName } from './widgets'

/** Search with an on-screen keyboard (works with a plain remote). Port of SearchActivity. */
export function searchScreen(): Screen {
  const header = makeHeader('search')
  let query = ''
  const qText = h('span'), hint = h('span', { class: 'ph', style: { marginLeft: '12px' } }, 'Channels, shows, sports')
  const box = h('div', { class: 'qbox' }, icon('search', 30), h('span', { style: { width: '16px' } }), qText, h('span', { class: 'caret' }), hint)
  const recentRow = h('div', { style: { display: 'flex', marginTop: '12px', height: '60px' } })
  const heading = h('div', { style: { fontSize: '30px', fontWeight: '600' } })
  const none = h('div', { style: { fontSize: '28px', color: 'var(--muted)', marginTop: '24px' } })
  const grid = h('div', { class: 'gridin', style: { marginTop: '16px' } })
  const right = h('div', { style: { position: 'absolute', left: '780px', top: '160px', right: '88px', bottom: '40px', overflow: 'hidden' } }, heading, none, grid)
  const left = h('div', { style: { position: 'absolute', left: '96px', top: '160px', width: '612px' } }, box,
    h('div', { style: { marginTop: '24px' } }, keyboard(type)),
    h('div', { style: { fontSize: '18px', color: 'var(--muted)', letterSpacing: '.08em', marginTop: '28px' } }, 'RECENT'), recentRow)
  const el = h('div', { class: 'screen' }, left, right, header.el)

  function type(k: string) {
    if (k === 'del') query = query.slice(0, -1); else if (k === 'clear') query = ''; else if (query.length < 40) query += k
    update()
  }
  const recentQueries = () => Store.str('queries').split('|').filter((q) => q.trim())
  function open(c: Channel) {
    if (query.trim()) Store.put('queries', [query.trim()].concat(recentQueries().filter((q) => q !== query.trim())).slice(0, 5).join('|'))
    App.go('player', { id: c.id, from: { name: 'search' } })
  }
  function update() {
    qText.textContent = query; hint.style.display = query ? 'none' : ''
    const pool = Catalog.playableChannels(); const q = query.trim().toLowerCase()
    const hits = !q ? (Catalog.favourites().length ? Catalog.favourites() : Catalog.recents().length ? Catalog.recents() : pool.slice(0, 9)) : pool.concat(Free.shown).filter((c) => c.name.toLowerCase().indexOf(q) >= 0)
    heading.textContent = !q ? 'Suggested for tonight' : 'Channels  ·  ' + hits.length
    none.textContent = q && !hits.length ? (Catalog.scanning ? 'No match yet. Still checking which channels your plan includes.' : `No matches for “${query.trim()}”. Try a channel name.`) : ''
    clear(grid)
    hits.slice(0, 30).forEach((c) => grid.appendChild(card(c, c.url ? Free.catName(c.lang) : langName(c.lang), () => open(c), () => { Catalog.toggleFav(c.id) })))
    ;(grid as HTMLElement).style.width = '1000px'
    clear(recentRow)
    recentQueries().forEach((r) => recentRow.appendChild(h('div', { class: 'btn small ghost', f: true, style: { marginRight: '12px', height: '52px', borderRadius: '30px' }, onclick: () => { query = r; update() } }, r)))
  }

  return {
    el,
    mount() { Catalog.onChange = () => { header.update(); update() }; Catalog.start(false); update(); focusFirst('.kb .key') },
    unmount() { Catalog.onChange = null },
    onKey(a) {
      if (a.type === 'char') { type(a.ch); return true }
      if (a.type === 'digit') { type(a.digit); return true }
      if (a.type === 'del') { type('del'); return true }
      return false
    },
  }
}
