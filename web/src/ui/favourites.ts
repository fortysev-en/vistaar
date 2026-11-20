import { Catalog } from '../catalog'
import { clear, h, icon } from '../dom'
import { focusFirst, setFocus } from '../nav/focus'
import { Store } from '../store'
import { Free } from '../free'
import { App, makeHeader, type Screen } from './app'
import { card, langName } from './widgets'

/** Favourites: a grid of saved channels (Jio and free). Hold OK removes one. Port of FavoritesActivity. */
export function favouritesScreen(): Screen {
  const header = makeHeader('favs')
  const title = h('div', { style: { position: 'absolute', left: '96px', top: '150px', fontSize: '36px', fontWeight: '600' } })
  const grid = h('div', { class: 'gridin' })
  const wrap = h('div', { class: 'grid', style: { top: '220px' } }, grid)
  const empty = h('div', { class: 'center hide', style: { position: 'absolute', left: '0', top: '0', width: '1920px', height: '1080px' } },
    icon('starO', 80, 'var(--muted)'), h('h2', null, 'No favourites yet'),
    h('p', null, 'Hold OK on any channel to save it here.'),
    h('div', { class: 'btn', f: true, style: { marginTop: '32px' }, onclick: () => App.go(Store.loggedIn ? 'jiotv' : 'freetv') }, Store.loggedIn ? 'Browse Jio TV' : 'Browse Free TV'))
  const el = h('div', { class: 'screen' }, title, wrap, empty, header.el)

  function render(keep = -1) {
    header.update()
    const list = Catalog.favourites()
    title.textContent = 'Favourites  ·  ' + list.length
    empty.className = 'center' + (list.length ? ' hide' : ''); wrap.style.display = list.length ? '' : 'none'
    clear(grid)
    list.forEach((c, i) => grid.appendChild(card(c, c.url ? Free.catName(c.lang) : langName(c.lang),
      () => App.go('player', { id: c.id, from: { name: 'favs' } }),
      () => { Catalog.toggleFav(c.id); render(i) }, true)))
    if (keep >= 0) { const cards = grid.children; if (cards.length) setFocus(cards[Math.min(keep, cards.length - 1)] as HTMLElement) }
  }

  return {
    el,
    mount() { Catalog.onChange = () => render(); Catalog.start(false); render(); focusFirst(wrap.style.display === 'none' ? undefined : '.card') },
    unmount() { Catalog.onChange = null },
  }
}
