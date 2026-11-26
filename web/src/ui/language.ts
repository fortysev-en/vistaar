import { h, icon, brandMark, clear } from '../dom'
import { focusFirst, setFocus } from '../nav/focus'
import { Store } from '../store'
import { GENRES, LANGUAGES } from '../types'
import { App, type Screen } from './app'
import { artBg } from './widgets'

/**
 * Language and category choice, shown on first run as two steps and again from Settings one at a time. Languages limit which channels are listed;
 * categories, in the order they are picked, set the order of the rows on Home. Port of LanguageActivity.
 */
export function languageScreen(params?: { first?: boolean; categories?: boolean }): Screen {
  const first = !!(params && params.first)
  let categories = !!(params && params.categories)
  let chosenLangs: number[] = Store.langs.slice()
  let picked: number[] = []
  let chips: { [id: number]: HTMLElement } = {}
  const go = h('div', { class: 'bigbtn go', f: true, 'data-go': true, style: { flex: '1', marginTop: '0' }, onclick: () => { if (picked.length) save(picked) } })
  const skip = h('div', { class: 'btn', f: true, style: { height: '80px', marginLeft: '16px' }, onclick: () => save([]) })
  const grid = h('div', { class: 'chips' })
  const step = h('div', { class: 'step' }), heading = h('h1'), lead = h('div', { class: 'lead' })
  const card = h('div', { class: 'card2', style: { width: '900px', height: 'auto', top: '110px' } }, grid, h('div', { style: { display: 'flex', alignItems: 'center', marginTop: '32px' } }, go, skip))
  const el = h('div', { class: 'screen' },
    h('div', { class: 'bg-art', style: { background: artBg('language') } }), h('div', { class: 'bg-shade' }),
    h('div', { class: 'lbrand' }, brandMark(40), 'Vistaar'),
    h('div', { class: 'lleft', style: { top: '300px' } }, step, heading, lead),
    card)

  function showStep(cats: boolean) {
    categories = cats
    picked = (cats ? Store.genres : Store.langsDone ? Store.langs : chosenLangs).slice()
    const label = cats ? 'Categories' : 'Languages'
    step.textContent = first ? `Step ${cats ? 2 : 1} of 2  ·  ${label}` : label
    heading.textContent = cats ? 'What do you like?' : 'What do you watch?'
    lead.textContent = cats ? 'Pick your favourites, most important first. Home shows their rows in that order. You can change this any time in Settings.'
      : 'Pick one or more. Only channels in these languages are listed, and they are checked first. You can change this any time in Settings.'
    skip.textContent = cats ? 'No preference' : 'All languages'
    chips = {}; clear(grid)
    const options: Array<[number, string]> = cats ? GENRES : Object.keys(LANGUAGES).map((k) => [Number(k), LANGUAGES[Number(k)]] as [number, string])
    options.forEach(([id, name]) => {
      // A category shows its place in your order; a language just a tick.
      const c = h('div', { class: 'chip', f: true, onclick: () => { const i = picked.indexOf(id); if (i >= 0) picked.splice(i, 1); else picked.push(id); paint() } }, name, cats ? h('span', { class: 'ord' }) : icon('check', 24))
      chips[id] = c; grid.appendChild(c)
    })
    paint()
  }
  function paint() {
    Object.keys(chips).forEach((k) => {
      const id = Number(k), at = picked.indexOf(id)
      if (at >= 0) chips[id].classList.add('on'); else chips[id].classList.remove('on')
      const ord = chips[id].querySelector('.ord'); if (ord) ord.textContent = at >= 0 ? String(at + 1) : ''
    })
    go.textContent = picked.length ? `Continue  ·  ${picked.length} selected` : 'Pick at least one'
    go.className = 'bigbtn ' + (picked.length ? 'go' : 'off') + (go.classList.contains('focus') ? ' focus' : '')
  }
  const focusChips = () => { const c = grid.firstElementChild as HTMLElement | null; if (c) setFocus(c) }
  function save(l: number[]) {
    if (categories) { Store.setGenres(l); if (first) { Store.setLangs(chosenLangs); App.home() } else App.go('settings') }
    else if (first) { chosenLangs = l.slice(); showStep(true); focusChips() } // first run goes on to the categories before anything is stored
    else { Store.setLangs(l); App.go('settings') }
  }
  showStep(categories)
  return {
    el,
    mount() { focusFirst() },
    onBack() {
      if (!first) { App.go('settings'); return true }
      if (categories) { showStep(false); focusChips(); return true }
      // First run: leaving without choosing means every language, so the picker never reappears on its own.
      if (!Store.langsDone) Store.setLangs([])
      App.home(); return true
    },
  }
}

export { setFocus }
