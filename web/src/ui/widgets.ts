import { h, icon } from '../dom'
import { Catalog } from '../catalog'
import { focused, setFocus } from '../nav/focus'
import { Free } from '../free'
import { LANGUAGES, type Channel } from '../types'

/** Shared building blocks: generated artwork, channel logo, LIVE badge, cards and the virtualised channel list. */

function hash(s: string): number { let x = 0; for (let i = 0; i < s.length; i++) x = (x * 31 + s.charCodeAt(i)) | 0; return Math.abs(x) }

/** Gradient artwork chosen per channel (the service provides no stills). */
export function artBg(id: string): string {
  const a = hash(id) % 360, b = (a + 60 + (hash(id + 'x') % 90)) % 360
  return `radial-gradient(ellipse at 28% 30%,hsla(${a},70%,45%,.55),rgba(0,0,0,0) 60%),radial-gradient(ellipse at 78% 70%,hsla(${b},65%,40%,.5),rgba(0,0,0,0) 62%),linear-gradient(135deg,hsl(${a},35%,14%),hsl(${b},40%,9%))`
}

export function logoBox(ch: Channel, size = 56): HTMLElement {
  const el = h('div', { class: 'logo', style: { width: size + 'px', height: size + 'px' } })
  const initials = ch.name.replace(/[^A-Za-z0-9 ]/g, '').split(' ').filter(Boolean).slice(0, 2).map((w) => w[0]).join('').toUpperCase() || 'TV'
  if (ch.logo) {
    const img = h('img', { src: ch.logo, alt: '' }) as HTMLImageElement
    img.onerror = () => { img.remove(); el.insertBefore(document.createTextNode(initials), el.firstChild) }
    el.appendChild(img)
  } else el.appendChild(document.createTextNode(initials))
  if (ch.premium) { const c = h('span', { class: 'crown' }); c.appendChild(icon('crown', Math.max(16, Math.round(size / 3)))); el.appendChild(c) }
  return el
}

export const liveBadge = () => h('span', { class: 'live' }, 'LIVE')

export function progressBar(frac: number): HTMLElement { const b = h('div', { class: 'bar' }, h('i')); (b.firstChild as HTMLElement).style.width = Math.max(0, Math.min(1, frac)) * 100 + '%'; return b }

/** A channel card for strips and grids. [onHold] toggles the favourite. */
export function card(ch: Channel, sub: string, onOpen: () => void, onHold: () => void, tall = false): HTMLElement {
  const star = h('span', { class: 'star' }, icon('star', 26))
  const upd = () => { star.style.display = Catalog.isFav(ch.id) ? '' : 'none' }
  upd()
  const el = h('div', { class: 'card' + (tall ? ' tall' : ''), f: true, style: { background: artBg(ch.id) }, onclick: onOpen, onvhold: () => { onHold(); upd() } },
    h('div', { class: 'shade' }), h('div', { class: 'tl' }, liveBadge()), star,
    h('div', { class: 'foot' }, logoBox(ch, 48), h('div', { class: 't' }, h('div', { class: 'a1' }, ch.name), h('div', { class: 'a2' }, sub))))
  return el
}

/**
 * Fixed-row-height list that only renders the rows on screen, so 850 channels stay smooth on a TV.
 * Up/Down step row by row (focus can never leak into another column); Down on the last row stays put; Up on the first row returns false.
 */
export class VList<T> {
  el: HTMLElement
  private inner: HTMLElement
  items: T[] = []
  index = 0
  private offset = 0
  private rows = new Map<number, HTMLElement>()
  constructor(private o: { rowH: number; render: (item: T, i: number) => HTMLElement; onFocus?: (item: T, i: number) => void; onOpen: (item: T, i: number) => void; onHold?: (item: T, i: number) => void }) {
    this.inner = h('div', { class: 'inner' })
    this.el = h('div', { class: 'list' }, this.inner)
  }
  private get viewH() { return this.el.clientHeight || 760 }

  setItems(items: T[], key?: (t: T) => string) {
    const cur = this.items[this.index]
    // nothing changed (the scan fires often): leave the rows, and the focus, alone
    if (key && items.length === this.items.length && items.every((x, i) => key(x) === key(this.items[i]))) return
    const had = !!focused() && this.contains(focused())
    this.items = items
    if (key && cur) { const k = key(cur); const i = items.findIndex((x) => key(x) === k); this.index = i >= 0 ? i : 0 } else this.index = Math.min(this.index, Math.max(0, items.length - 1))
    this.inner.style.height = items.length * this.o.rowH + 'px'
    this.rows.forEach((r) => r.parentNode && r.parentNode.removeChild(r)); this.rows.clear()
    this.clamp(); this.draw()
    if (had) { const r = this.rows.get(this.index); if (r) setFocus(r) } // rebuilt rows must not drop the user's focus
  }

  private clamp() {
    const top = this.index * this.o.rowH, bottom = top + this.o.rowH + 8
    if (top < this.offset) this.offset = top
    if (bottom > this.offset + this.viewH) this.offset = bottom - this.viewH
    this.offset = Math.max(0, Math.min(this.offset, Math.max(0, this.items.length * this.o.rowH - this.viewH)))
    this.inner.style.transform = `translateY(${-this.offset}px)`
  }

  private draw() {
    const rh = this.o.rowH
    const from = Math.max(0, Math.floor(this.offset / rh) - 3), to = Math.min(this.items.length - 1, Math.ceil((this.offset + this.viewH) / rh) + 3)
    this.rows.forEach((r, i) => { if (i < from || i > to) { r.parentNode && r.parentNode.removeChild(r); this.rows.delete(i) } })
    for (let i = from; i <= to; i++) {
      if (this.rows.has(i)) continue
      const r = this.o.render(this.items[i], i)
      r.style.top = i * rh + 'px'; r.setAttribute('data-f', ''); r.classList.add('row')
      r.addEventListener('click', () => { this.index = i; this.o.onOpen(this.items[i], i) })
      r.addEventListener('vhold', () => { if (this.o.onHold) this.o.onHold(this.items[i], i) })
      r.addEventListener('vfocus', () => { this.index = i; this.o.onFocus && this.o.onFocus(this.items[i], i) })
      this.inner.appendChild(r); this.rows.set(i, r)
    }
  }

  /** Re-render the rows currently on screen (e.g. after the guide loads or a star changes). */
  refresh() { this.rows.forEach((r) => r.parentNode && r.parentNode.removeChild(r)); this.rows.clear(); this.draw(); this.focusIndex(this.index, false) }

  focusIndex(i: number, scroll = true) {
    if (!this.items.length) return
    this.index = Math.max(0, Math.min(i, this.items.length - 1))
    if (scroll) this.clamp(); this.draw()
    const r = this.rows.get(this.index)
    if (r) setFocus(r)
  }
  get focusedRow(): HTMLElement | undefined { return this.rows.get(this.index) }
  contains(el: Element | null): boolean { return !!el && this.el.contains(el) }

  /** true = handled (stay in the list). */
  step(dir: 'up' | 'down'): boolean {
    if (dir === 'down') { if (this.index < this.items.length - 1) this.focusIndex(this.index + 1); return true }
    if (this.index > 0) { this.focusIndex(this.index - 1); return true }
    return false
  }
}

export function sleep(ms: number) { return new Promise<void>((r) => setTimeout(r, ms)) }
export const fmtTime = (t: number) => { const d = new Date(t); let hh = d.getHours(); const m = d.getMinutes(); const ap = hh >= 12 ? 'pm' : 'am'; hh = hh % 12 || 12; return hh + ':' + (m < 10 ? '0' : '') + m + ' ' + ap }
/** Language (Jio) or category (free) name of a channel's lang value. */
export const langName = (id: number) => (id < 0 ? Free.catName(id) : LANGUAGES[id] || 'Other')
