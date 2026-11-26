import { Catalog } from '../catalog'
import { clear, h } from '../dom'
import { focused, focusFirst, setFocus } from '../nav/focus'
import type { Channel } from '../types'
import { App, makeHeader, menu, type Screen } from './app'
import { beginMove } from './reorder'
import { logoBox } from './widgets'

/** Favourites: a grid of large logo cards. OK watches; holding OK offers Move, Rename and Remove. Port of FavoritesActivity. */
const COLUMNS = 6

export function favouritesScreen(params?: { focus?: string }): Screen {
  const header = makeHeader('favs')
  const count = h('span', { class: 'fcount' })
  const grid = h('div', { class: 'fgrid' })
  const box = h('div', { class: 'fbox' }, grid)
  const empty = h('div', { class: 'empty fempty hide' }, 'No favourites yet. Hold OK on any channel to save it here.')
  const el = h('div', { class: 'screen' },
    h('div', { class: 'ftitle' }, h('h1', null, 'Favourites'), count, h('span', { class: 'fhint' }, 'OK to watch  ·  Hold OK for options')),
    box, empty, header.el)
  let items: Channel[] = []
  let sig = ''
  const byId = (id: string) => items.filter((c) => c.id === id)[0]
  const cardOf = (id: string) => Array.prototype.filter.call(grid.children, (c: HTMLElement) => c.getAttribute('data-id') === id)[0] as HTMLElement | undefined

  /** Scrolls so the focused card's row is fully visible. */
  function reveal(card: HTMLElement) {
    const top = card.offsetTop, bottom = top + card.offsetHeight
    if (top - 12 < box.scrollTop) box.scrollTop = Math.max(0, top - 12); else if (bottom + 24 > box.scrollTop + box.clientHeight) box.scrollTop = bottom + 24 - box.clientHeight
  }
  const play = (c: Channel) => App.go('player', { id: c.id, list: 'favs', chs: items.slice(), from: { name: 'favs', params: { focus: c.id } } })
  function store() {
    items = Array.prototype.map.call(grid.children, (c: HTMLElement) => byId(c.getAttribute('data-id') || '')) as Channel[]
    Catalog.saveFavOrder(items.map((c) => c.id)); sig = signature(items)
    const f = focused(); if (f) reveal(f)
  }
  function options(c: Channel, card: HTMLElement) {
    menu(h('div', { class: 'mhead' }, logoBox(c, 72), h('span', null, Catalog.label(c))), [
      { label: 'Move', act: () => { setFocus(card); beginMove(card, 'x', 0, store, COLUMNS) } },
      { label: 'Rename', act: () => App.go('rename', { id: c.id }) },
      { label: 'Remove from favourites', bad: true, act: () => { const at = items.indexOf(c); Catalog.toggleFav(c.id); render(at) } },
    ])
  }
  function cardEl(c: Channel): HTMLElement {
    const card = h('div', { class: 'fcard', f: true, 'data-id': c.id, onclick: () => play(c), onvhold: () => options(c, card) },
      h('div', { class: 'finner' }, logoBox(c, 168), h('div', { class: 'fname' }, Catalog.label(c))))
    card.addEventListener('vfocus', () => reveal(card))
    return card
  }
  const signature = (list: Channel[]) => list.map((c) => c.id + ':' + Catalog.label(c)).join('|')

  /** Redraws only when the list or a name changed, and keeps focus on the same channel (or the same place after a removal). */
  function render(focusAt = -1) {
    header.update()
    if (grid.querySelector('.moving')) return // the cards are being arranged by hand
    const next = Catalog.favourites()
    const nextSig = signature(next)
    count.textContent = next.length ? next.length + (next.length === 1 ? ' channel' : ' channels') : ''
    empty.className = 'empty fempty' + (next.length ? ' hide' : '')
    if (nextSig === sig) return
    const f = focused(), had = f && grid.contains(f) ? f.getAttribute('data-id') : null
    const first = !items.length
    items = next; sig = nextSig
    clear(grid)
    items.forEach((c) => grid.appendChild(cardEl(c)))
    const target = focusAt >= 0 ? grid.children[Math.min(focusAt, items.length - 1)] as HTMLElement | undefined
      : had ? cardOf(had) : first && params && params.focus ? cardOf(params.focus) : first ? grid.children[0] as HTMLElement | undefined : undefined
    if (target) setFocus(target); else if (!items.length) focusFirst()
  }

  return {
    el,
    mount() { Catalog.onChange = () => render(); Catalog.start(false); render(); const c = (params && params.focus && cardOf(params.focus)) || grid.children[0] as HTMLElement | undefined; if (c) setFocus(c); else focusFirst() },
    unmount() { Catalog.onChange = null },
    onBack() { App.go('home'); return true },
  }
}
