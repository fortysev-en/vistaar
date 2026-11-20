import { Catalog } from '../catalog'
import { clear, h, icon, brandMark } from '../dom'
import { focusFirst, focused, install, setFocus, setRoot } from '../nav/focus'
import type { Action } from '../nav/keys'
import { Platform } from '../platform'
import { Store } from '../store'

export type Name = 'login' | 'language' | 'home' | 'jiotv' | 'freetv' | 'hd' | 'guide' | 'favs' | 'search' | 'settings' | 'playlist' | 'player'

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
  home() { this.go(!Store.hasAccess ? 'login' : !Store.langsDone ? 'language' : 'home', !Store.langsDone ? { first: true } : undefined) },

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
    this.home()
  },
}

// ------------------------------------------------------------------ header
export interface Header { el: HTMLElement; update(): void; firstFocus: HTMLElement }

export function makeHeader(active: Name): Header {
  const jio = Store.loggedIn // free-only mode has no Jio channels or guide
  const items: Array<[string, Name]> = [['Home', 'home'], ['Jio TV', 'jiotv'], ['Free TV', 'freetv'], ['HD', 'hd'], ['Guide', 'guide'], ['Favourites', 'favs']]
  const pill = (label: string | null, ic: string | null, to: Name) => h('div', { class: 'pill' + (to === active ? ' on' : '') + (ic ? ' icon' : ''), f: true, onclick: () => { if (to !== active) App.go(to) } }, ic ? icon(ic, 24) : label)
  const nav = h('div', { class: 'nav' }, pill(null, 'search', 'search'), items.filter((i) => jio || (i[1] !== 'jiotv' && i[1] !== 'guide')).map((i) => pill(i[0], null, i[1])))
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
