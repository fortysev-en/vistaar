import { beginMove, Order } from './reorder'
import { Catalog } from '../catalog'
import { clear, h, icon, brandMark } from '../dom'
import { focusFirst, focused, install, setFocus, setRoot } from '../nav/focus'
import type { Action } from '../nav/keys'
import { Platform } from '../platform'
import { Store } from '../store'

/** Bump when the disclaimer wording changes, to ask everyone again. */
export const DISCLAIMER_VERSION = 1
export const DISCLAIMER_TEXT = 'Vistaar is a media player only. It does not host, store or provide any content. Vistaar is not affiliated with any broadcaster, channel or content provider, including Jio, and does not endorse the use of illegal content sources. Free channels come from public playlists, and Jio channels appear only if you sign in with your own subscription. You are responsible for what you watch.'

export type Name = 'login' | 'language' | 'home' | 'jiotv' | 'freetv' | 'hd' | 'guide' | 'favs' | 'search' | 'settings' | 'playlist' | 'player' | 'disclaimer'

export interface Screen {
  el: HTMLElement
  mount?(): void
  unmount?(): void
  /** Return true if handled; otherwise the default applies (leave to Home, or the exit dialog on Home). */
  onBack?(): boolean
  onKey?(a: Action, e: KeyboardEvent): boolean
}

const factories: { [n: string]: (p: any) => Screen } = {}
let dialogEl: HTMLElement | null = null
let dialogReturn: HTMLElement | null = null

export const App = {
  name: '' as Name | '', params: undefined as any, screen: null as Screen | null,
  register(name: Name, f: (p: any) => Screen) { factories[name] = f },

  go(name: Name, params?: any) {
    if (this.screen && this.screen.unmount) this.screen.unmount()
    closeDialog()
    const host = document.getElementById('app')!
    clear(host)
    this.name = name; this.params = params
    const s = factories[name](params)
    this.screen = s
    host.appendChild(s.el); setRoot(s.el); setFocus(null)
    if (s.mount) s.mount()
  },

  /** The first screen to show for the current sign-in state. */
  /** [launch]: the app is starting, so it opens on whichever tab the viewer put first (Back then goes to Home). */
  home(launch = false) {
    if (Store.disclaimerV < DISCLAIMER_VERSION) { this.go('disclaimer'); return }
    if (!Store.hasAccess) Store.freeOnly = true // Jio sign-in lives in Settings; there is no sign-in screen at start
    if (!Store.langsDone) this.go('language', { first: true })
    else this.go(launch ? navTabs()[0][1] : 'home')
  },

  start() {
    install({
      onBack: () => {
        if (dialogEl) { closeDialog(); return true }
        const s = this.screen
        if (s && s.onBack && s.onBack()) return true
        if (this.name === 'home') exitDialog(); else if (this.name !== 'login') this.go('home')
        return true
      },
      onKey: (a, e) => (dialogEl ? false : !!(this.screen && this.screen.onKey && this.screen.onKey(a, e))),
    })
    splash(() => this.home(true))
  },
}

// ------------------------------------------------------------------ header
export interface Header { el: HTMLElement; update(): void; firstFocus: HTMLElement }

/** The header tabs this viewer has, in their saved order. Free-only mode has no Jio channels or guide. */
function navTabs(): Array<[string, Name]> {
  const jio = Store.loggedIn
  const items: Array<[string, Name]> = [['Home', 'home'], ['Jio TV', 'jiotv'], ['Free TV', 'freetv'], ['HD', 'hd'], ['Guide', 'guide'], ['Favourites', 'favs']]
  return Order.arrange(items.filter((i) => jio || (i[1] !== 'jiotv' && i[1] !== 'guide')), 'tabs', (i) => i[1])
}

export function makeHeader(active: Name): Header {
  const pill = (label: string | null, ic: string | null, to: Name) => {
    const el = h('div', { class: 'pill' + (to === active ? ' on' : '') + (ic ? ' icon' : ''), f: true, 'data-tab': to, onclick: () => { if (to !== active) App.go(to) } }, ic ? icon(ic, 24) : label)
    // Hold OK on a tab to move it with Left/Right; the search button stays first and the order is remembered.
    if (!ic) el.addEventListener('vhold', () => beginMove(el, 'x', 1, () => Order.save('tabs', Array.prototype.slice.call(nav.children, 1).map((c: HTMLElement) => c.getAttribute('data-tab') || ''))))
    return el
  }
  const nav: HTMLElement = h('div', { class: 'nav' }, pill(null, 'search', 'search'), navTabs().map((i) => pill(i[0], null, i[1])))
  const status = h('span', { class: 'status' })
  const clock = h('span', { class: 'clock' })
  const refresh = h('div', { class: 'circle', f: true, onclick: () => { Catalog.start(true); update() } }, icon('refresh', 24))
  const tick = () => { const d = new Date(); let hh = d.getHours(); const ap = hh >= 12 ? 'pm' : 'am'; hh = hh % 12 || 12; clock.textContent = hh + ':' + ('0' + d.getMinutes()).slice(-2) + ' ' + ap }
  tick(); const iv = window.setInterval(() => { if (!document.body.contains(clock)) clearInterval(iv); else tick() }, 15000)
  const update = () => {
    status.textContent = Catalog.scanning ? `Checking ${Catalog.scanned}/${Catalog.all.length}` : ''
    status.style.display = Catalog.scanning ? '' : 'none'
    if (Catalog.scanning) refresh.classList.add('spin'); else refresh.classList.remove('spin')
  }
  update()
  const el = h('div', { class: 'header' },
    h('div', { class: 'brand' }, brandMark(40), 'Vistaar'),
    nav,
    h('div', { class: 'hright' }, status, clock, refresh, h('div', { class: 'circle', f: true, onclick: () => App.go('settings') }, icon('settings', 24))))
  return { el, update, firstFocus: nav.children[1] as HTMLElement }
}

// ------------------------------------------------------------------ dialogs
export function closeDialog() {
  if (!dialogEl) return
  dialogEl.parentNode && dialogEl.parentNode.removeChild(dialogEl)
  dialogEl = null
  if (App.screen) setRoot(App.screen.el)
  if (dialogReturn && document.body.contains(dialogReturn)) setFocus(dialogReturn); dialogReturn = null
}

/** Modal with two buttons; the first (Cancel) has focus so a stray OK can't confirm. */
export function dialog(title: string, message: string, cancelLabel: string, okLabel: string, onOk: () => void) {
  closeDialog()
  dialogReturn = focused()
  const cancel = h('div', { class: 'btn', f: true, onclick: () => closeDialog() }, cancelLabel)
  const ok = h('div', { class: 'btn', f: true, onclick: () => { closeDialog(); onOk() } }, okLabel)
  dialogEl = h('div', { class: 'dlg-bg' }, h('div', { class: 'dlg' }, h('h3', null, title), h('p', null, message), h('div', { class: 'bs' }, cancel, ok)))
  document.getElementById('app')!.appendChild(dialogEl)
  setRoot(dialogEl); setFocus(cancel)
}

export function exitDialog() { dialog('Exit the app?', "You'll need to reopen it to keep watching.", 'Cancel', 'Exit', () => Platform.exit()) }

export { focusFirst }

// ------------------------------------------------------------------ splash
/** The Vistaar mark for a moment, then [next]. */
function splash(next: () => void) {
  const host = document.getElementById('app')!
  clear(host)
  host.appendChild(h('div', { class: 'splash' },
    h('div', { class: 'shalo' }),
    h('div', { class: 'smark' }, brandMark(168)),
    h('div', { class: 'sname' }, 'Vistaar'),
    h('div', { class: 'sload' }, h('div', { class: 'sfill' }))))
  window.setTimeout(next, 2600)
}
