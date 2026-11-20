import { h, icon } from '../dom'

/** On-screen keyboard (letters, digits, optional URL symbols) that works with any remote. [onKey] gets a character, 'del', 'clear' or 'space'. */
export function keyboard(onKey: (k: string) => void, symbols = false): HTMLElement {
  const kb = h('div', { class: 'kb' })
  const add = (label: string, k: string, cls = '') => kb.appendChild(h('div', { class: 'key ' + cls, f: true, onclick: () => onKey(k) }, label === '⌫' ? icon('backspace', 30) : label))
  'abcdefghijklmnopqrstuvwxyz0123456789'.split('').forEach((c) => add(c.toUpperCase(), c))
  if (symbols) ':/.-_?=&%'.split('').forEach((c) => add(c, c))
  if (symbols) { add('⌫', 'del', 'sm'); add('Clear', 'clear', 'sm'); add('https://', 'https://', 'sm'); kb.classList.add('tight') } // links have no spaces; keys are shorter so the whole screen fits
  else { add('Space', ' ', 'sm w2'); add('⌫', 'del', 'sm'); add('Clear', 'clear', 'sm') }
  return kb
}
