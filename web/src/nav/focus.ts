import { keyAction, type Action } from './keys'
import { pickNext, type Cand, type Dir } from './spatial'

/** DOM glue for the focus engine: elements marked data-f are focusable; arrows move focus spatially; OK clicks (hold OK fires "vhold"); Magic Remote pointer hovers. */

let current: HTMLElement | null = null
let root: HTMLElement | null = null // focus search is limited to the active layer (screen or dialog)
let backHandler: (() => boolean) | null = null
let keyHandler: ((a: Action, e: KeyboardEvent) => boolean) | null = null

const visible = (el: HTMLElement) => !!(el.offsetWidth || el.offsetHeight) && getComputedStyle(el).visibility !== 'hidden'
export const focusables = (r: ParentNode = root || document): HTMLElement[] => Array.from(r.querySelectorAll<HTMLElement>('[data-f]')).filter(visible)

export function setRoot(el: HTMLElement | null) { root = el }

export function setFocus(el: HTMLElement | null) {
  if (current && current !== el) { current.classList.remove('focus'); current.dispatchEvent(new CustomEvent('vblur')) }
  current = el
  if (el) { el.classList.add('focus'); el.dispatchEvent(new CustomEvent('vfocus')) }
}
export const focused = () => current

/** Focus the first focusable element (or the first one matching [selector]). */
export function focusFirst(selector?: string) {
  const all = focusables()
  setFocus((selector ? all.find((e) => e.matches(selector)) : all[0]) || null)
}

export function move(dir: Dir): boolean {
  const all = focusables()
  if (!current || all.indexOf(current) < 0) { setFocus(all[0] || null); return !!all[0] }
  const r = current.getBoundingClientRect()
  const others = all.filter((e) => e !== current)
  const cands: Cand[] = others.map((e, i) => { const b = e.getBoundingClientRect(); return { id: String(i), rect: { left: b.left, top: b.top, right: b.right, bottom: b.bottom } } })
  const id = pickNext({ left: r.left, top: r.top, right: r.right, bottom: r.bottom }, dir, cands)
  if (id === null) return false
  setFocus(others[Number(id)])
  return true
}

/** Install key + pointer handling once. [onKey] sees every action first and returns true when it handled it (lists step rows themselves). */
export function install(opts: { onBack: () => boolean; onKey?: (a: Action, e: KeyboardEvent) => boolean }) {
  backHandler = opts.onBack; keyHandler = opts.onKey || null
  let holdTimer = 0, held = false
  document.addEventListener('keydown', (e) => {
    const a = keyAction(e.keyCode)
    if (!a) return
    if (a.type === 'ok') {
      e.preventDefault()
      if (!e.repeat) { held = false; holdTimer = window.setTimeout(() => { held = true; if (current) current.dispatchEvent(new CustomEvent('vhold')) }, 600) }
      return
    }
    if (keyHandler && keyHandler(a, e)) { e.preventDefault(); return }
    if (a.type === 'dir') { move(a.dir); e.preventDefault() }
    else if (a.type === 'back') { backHandler && backHandler(); e.preventDefault() }
  })
  document.addEventListener('keyup', (e) => {
    const a = keyAction(e.keyCode)
    if (!a || a.type !== 'ok') return
    clearTimeout(holdTimer); e.preventDefault()
    if (!held && !(keyHandler && keyHandler(a, e)) && current) current.click()
    held = false
  })
  // Magic Remote pointer: hovering focuses, clicking activates (click is native).
  document.addEventListener('mouseover', (e) => {
    const el = (e.target as HTMLElement).closest('[data-f]') as HTMLElement | null
    if (el && el !== current && (!root || root.contains(el))) setFocus(el)
  })
}
