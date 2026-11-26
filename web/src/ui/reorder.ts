import { grab } from '../nav/focus'
import { Store } from '../store'
import { arrange } from '../arrange'

export { arrange }

/** The viewer's own order for the header tabs and the category lists, kept under "ord.<key>". Hold OK on an item to move it. */
export const Order = {
  saved(key: string): string[] { return Store.str('ord.' + key).split('|').filter(Boolean) },
  arrange<T>(items: T[], key: string, id: (t: T) => string): T[] { return arrange(items, this.saved(key), id) },
  save(key: string, ids: string[]) { Store.put('ord.' + key, ids.join('|')) },
  resetAll() { try { Object.keys(window.localStorage).filter((k) => k.indexOf('ord.') === 0).forEach((k) => window.localStorage.removeItem(k)) } catch { /* no storage */ } },
}

function toast(text: string) {
  const t = document.createElement('div'); t.className = 'toast'; t.textContent = text
  document.body.appendChild(t); window.setTimeout(() => { if (t.parentNode) t.parentNode.removeChild(t) }, 2200)
}

/**
 * Starts moving [el] among its siblings: the arrows along [axis] move it (never before the first [pinned] siblings), any other key drops it.
 * In a grid of [columns], Up and Down move it by a whole row. [moved] runs after each step so the caller can save the new order.
 */
export function beginMove(el: HTMLElement, axis: 'x' | 'y', pinned: number, moved: () => void, columns = 0) {
  const parent = el.parentNode as HTMLElement | null
  if (!parent) return
  const index = () => Array.prototype.indexOf.call(parent.children, el) as number
  if (index() < pinned) return
  el.classList.add('moving'); toast('Move it, then press OK')
  const prev = axis === 'y' ? 'up' : 'left', next = axis === 'y' ? 'down' : 'right'
  grab((a) => {
    if (a.type === 'dir' && columns && (a.dir === 'up' || a.dir === 'down')) {
      const i = index(), n = parent.children.length
      let to = i + (a.dir === 'up' ? -columns : columns)
      if (to >= n && Math.floor(i / columns) < Math.floor((n - 1) / columns)) to = n - 1 // down into a shorter last row
      if (to >= pinned && to < n) { const at = parent.children[to]; parent.insertBefore(el, to > i ? at.nextSibling : at); moved() }
    } else if (a.type === 'dir' && (a.dir === prev || a.dir === next)) {
      if (a.dir === prev) { const p = el.previousElementSibling; if (p && index() > pinned) parent.insertBefore(el, p) }
      else { const n = el.nextElementSibling; if (n) parent.insertBefore(n, el) }
      moved()
    } else { el.classList.remove('moving'); grab(null) }
  })
}
