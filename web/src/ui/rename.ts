import { Catalog } from '../catalog'
import { h } from '../dom'
import { focusFirst } from '../nav/focus'
import { App, type Screen } from './app'
import { keyboard } from './keyboard'

/** Gives a favourite your own name with the on-screen keyboard. Port of RenameActivity. */
const MAX = 40

export function renameScreen(params?: { id?: string }): Screen {
  const id = (params && params.id) || ''
  const ch = Catalog.favourites().filter((c) => c.id === id)[0]
  let name = ch ? Catalog.label(ch) : ''
  let caps = false
  const field = h('div', { class: 'rfield' })
  const paint = () => { field.textContent = (name || 'Original name') + (caps ? '   ⇧' : ''); field.style.color = name ? 'var(--text)' : 'var(--dim)' }
  const done = () => App.go('favs', { focus: id })
  // Words start with a capital on their own; Caps keeps every letter upper case (for names such as HD or TV).
  const type = (k: string) => { if (name.length < MAX) name += caps || !name || name.slice(-1) === ' ' ? k.toUpperCase() : k }
  const kb = keyboard((k) => { if (k === 'del') name = name.slice(0, -1); else if (k === 'clear') name = ''; else type(k); paint() })
  const extra = (label: string, act: () => void) => kb.appendChild(h('div', { class: 'key sm', f: true, onclick: () => { act(); paint() } }, label))
  extra('Caps', () => { caps = !caps }); extra('&', () => type('&'))
  const el = h('div', { class: 'screen' },
    h('div', { class: 'rbox' },
      h('h1', null, 'Rename channel'),
      h('div', { class: 'rorig' }, 'Original name: ' + (ch ? ch.name : '')),
      field, kb,
      h('div', { class: 'rbtns' },
        h('div', { class: 'bigbtn go', f: true, onclick: () => { if (ch) Catalog.rename(ch, name); done() } }, 'Save name'),
        h('div', { class: 'bigbtn sec', f: true, onclick: () => { if (ch) Catalog.rename(ch, ''); done() } }, 'Use original name'))))
  paint()
  return {
    el,
    mount() { focusFirst('.key') },
    onBack() { done(); return true },
  }
}
