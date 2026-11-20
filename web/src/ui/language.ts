import { h, icon, brandMark } from '../dom'
import { focusFirst, setFocus } from '../nav/focus'
import { Store } from '../store'
import { LANGUAGES } from '../types'
import { App, type Screen } from './app'
import { artBg } from './widgets'

/** Language choice, shown once on first run and again from Settings: pick one or more; only those channels are listed. Port of LanguageActivity. */
export function languageScreen(params?: { first?: boolean }): Screen {
  const first = !!(params && params.first)
  const picked: number[] = Store.langs.slice()
  const chips: { [id: number]: HTMLElement } = {}
  const go = h('div', { class: 'bigbtn go', f: true, 'data-go': true, style: { flex: '1', marginTop: '0' }, onclick: () => { if (picked.length) save(picked) } })
  const all = h('div', { class: 'btn', f: true, style: { height: '80px', marginLeft: '16px' }, onclick: () => save([]) }, 'All languages')
  const grid = h('div', { class: 'chips' })
  Object.keys(LANGUAGES).forEach((k) => {
    const id = Number(k)
    const c = h('div', { class: 'chip', f: true, onclick: () => { const i = picked.indexOf(id); if (i >= 0) picked.splice(i, 1); else picked.push(id); paint() } }, LANGUAGES[id], icon('check', 24))
    chips[id] = c; grid.appendChild(c)
  })
  const card = h('div', { class: 'card2', style: { width: '900px', height: 'auto', top: '110px' } }, grid, h('div', { style: { display: 'flex', alignItems: 'center', marginTop: '32px' } }, go, all))
  const el = h('div', { class: 'screen' },
    h('div', { class: 'bg-art', style: { background: artBg('language') } }), h('div', { class: 'bg-shade' }),
    h('div', { class: 'lbrand' }, brandMark(40), 'Vistaar'),
    h('div', { class: 'lleft', style: { top: '300px' } },
      h('div', { class: 'step' }, first ? 'Your languages' : 'Languages'),
      h('h1', null, 'What do you watch?'),
      h('div', { class: 'lead' }, 'Pick one or more. Only channels in these languages are listed, and they are checked first. You can change this any time in Settings.')),
    card)

  function paint() {
    Object.keys(chips).forEach((k) => { const id = Number(k); if (picked.indexOf(id) >= 0) chips[id].classList.add('on'); else chips[id].classList.remove('on') })
    go.textContent = picked.length ? `Continue  ·  ${picked.length} selected` : 'Pick at least one'
    go.className = 'bigbtn ' + (picked.length ? 'go' : 'off') + (go.classList.contains('focus') ? ' focus' : '')
  }
  function save(l: number[]) { Store.setLangs(l); if (first) App.home(); else App.go('settings') }
  paint()
  return {
    el,
    mount() { focusFirst() },
    // First run: leaving without choosing means every language, so the picker never reappears on its own.
    onBack() { if (first && !Store.langsDone) Store.setLangs([]); if (first) App.home(); else App.go('settings'); return true },
  }
}

export { setFocus }
