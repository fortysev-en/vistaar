import { GENRES } from '../types'
import { Order } from './reorder'
import * as Api from '../api'
import { Catalog } from '../catalog'
import { Free } from '../free'
import { clear, h, icon } from '../dom'
import { focusFirst, setFocus } from '../nav/focus'
import type { Action } from '../nav/keys'
import { Platform } from '../platform'
import { DEFAULT_PLAYLIST, Store } from '../store'
import { LANGUAGES } from '../types'
import { VERSION } from '../version'
import { App, dialog, exitDialog, type Screen } from './app'
import { fmtTime } from './widgets'

/** Settings: Library, Account, About. Every row does something real. Port of SettingsActivity. */
interface Row { label: string; desc?: string; value?: string; kind?: 'action' | 'info'; destructive?: boolean; act?: () => void }
interface Cat { label: string; icon: string; intro: string; rows: () => Row[] }

function genreSummary(): string {
  const names = Store.genres.map((id) => (GENRES.filter((g) => g[0] === id)[0] || [0, ''])[1]).filter(Boolean)
  return names.length ? names.slice(0, 2).join(', ') + (names.length > 2 ? ' +' + (names.length - 2) : '') : 'Default order'
}

export function settingsScreen(p?: { cat?: number }): Screen {
  const jio = Store.loggedIn
  let current = p && p.cat ? p.cat : 0
  let updateState = '', signOutState = '', signingOut = false

  const checked = () => { const t = Number(Store.str('okAt') || 0); return t ? new Date(t).toLocaleDateString(undefined, { day: 'numeric', month: 'short' }) + ', ' + fmtTime(t) : 'never' }
  const langSummary = () => { const l = Store.langs; if (!l.length) return 'All'; const n = l.map((x) => LANGUAGES[x]).filter(Boolean).sort(); return n.length <= 2 ? n.join(', ') : n.length + ' languages' }
  const playlistName = () => { const u = Store.playlistUrl; return u === DEFAULT_PLAYLIST ? 'India (default)' : (u.replace(/^https?:\/\//, '').split('/').pop() || u).slice(0, 28) }

  async function checkUpdate() {
    updateState = 'Checking…'; render()
    try {
      const r = await fetch('https://api.github.com/repos/fortysev-en/vistaar/releases/latest')
      const j = await r.json(); const tag = String(j.tag_name || '').replace(/^v/, '')
      const newer = (a: string, b: string) => { const x = a.split('.').map(Number), y = b.split('.').map(Number); for (let i = 0; i < 3; i++) { if ((x[i] || 0) !== (y[i] || 0)) return (x[i] || 0) > (y[i] || 0) } return false }
      updateState = !tag ? 'Couldn’t check' : newer(tag, VERSION) ? `Version ${tag} available (reinstall the app)` : 'You’re up to date'
    } catch { updateState = 'Couldn’t check' }
    render()
  }
  async function signOut() {
    if (signingOut) return
    signingOut = true; signOutState = 'Signing out…'; render()
    await Api.logoutRemote()
    Store.clear(); Store.freeOnly = true; Catalog.reset() // back to the free channels
    App.go('home')
  }

  const cats: Cat[] = [
    { label: 'Library', icon: 'list', intro: 'Keep the channel list and your saved channels tidy.', rows: () => [
      { label: 'Refresh channels', desc: jio ? 'Re-check which channels your plan includes' : 'Reload the free channel list', value: Catalog.scanning ? `Checking ${Catalog.scanned}/${Catalog.all.length}` : jio ? 'Checked ' + checked() : '', act: () => { Catalog.start(true); render() } },
      { label: 'Languages', desc: 'Only channels in these languages are listed', value: langSummary(), act: () => App.go('language', { first: false }) },
      { label: 'Home categories', desc: 'Choose which rows Home shows first', value: genreSummary(), act: () => App.go('language', { first: false, categories: true }) },
      { label: 'Free TV playlist', desc: 'Choose a country or paste any M3U link', value: playlistName(), act: () => App.go('playlist') },
      { label: 'Clear favourites', desc: 'Remove every saved channel', value: Catalog.favCount() + ' saved', act: () => { Catalog.clearFavs(); render() } },
      { label: 'Reset order', desc: 'Put the tabs and categories back in their usual order', act: () => { Order.resetAll(); render() } },
      { label: 'Clear recently watched', desc: 'Resets Home and the remembered audio language of each channel', act: () => { Catalog.clearRecents(); Store.clearAudioPrefs(); render() } },
    ] },
    { label: 'Account', icon: 'logout', intro: jio ? 'Manage the account this TV is signed in to.' : 'You are using the free channels only.', rows: () => [
      jio ? { label: 'Sign out', desc: 'Frees this TV on your Jio account and lets you use a different number', value: signOutState, destructive: true, act: () => signOut() }
        : { label: 'Sign in to Jio', desc: 'Add your Jio channels, guide and HD', act: () => App.go('login', { add: true }) },
      { label: 'Exit app', desc: 'Close the app', act: () => exitDialog() },
    ] },
    { label: 'About', icon: 'info', intro: 'Share these details if something isn’t working.', rows: () => [
      { label: 'App version', value: VERSION, kind: 'info' },
      { label: 'Check for updates', desc: 'Looks for a newer Vistaar on GitHub', value: updateState, act: () => checkUpdate() },
      { label: 'Disclaimer', desc: 'What Vistaar is, and is not', act: () => App.go('disclaimer', { view: true }) },
      { label: 'GitHub', desc: 'Source code and releases', value: 'github.com/fortysev-en/vistaar', kind: 'info' },
      { label: 'Documentation', desc: 'Guides and how it works', value: 'fortyseven.dev/projects/vistaar', kind: 'info' },
      { label: 'Device', value: Platform.name + ' · Chromium ' + Platform.chromium, kind: 'info' },
    ].concat(jio ? [{ label: 'Channels on your plan', value: Catalog.playableChannels().length + ' of ' + Catalog.all.length, kind: 'info' }, { label: 'Plan last checked', value: checked(), kind: 'info' }] : []) as Row[] },
  ]

  const nav = h('div', { class: 'set-nav' })
  const title = h('h1'), intro = h('div', { class: 'intro' }), rowsBox = h('div')
  const el = h('div', { class: 'screen' },
    h('div', { class: 'set-side' }),
    h('div', { class: 'backp', f: true, onclick: () => App.go('home') }, icon('left', 20), 'Home'),
    nav, h('div', { class: 'set-main' }, title, intro, rowsBox))

  function render(focusNav = false) {
    const hadRows = !!(document.querySelector('.set-main .focus'))
    const rowFocus = hadRows ? Array.prototype.indexOf.call(rowsBox.children, document.querySelector('.set-main .focus')) : -1
    clear(nav); nav.appendChild(h('h1', null, 'Settings'))
    cats.forEach((c, i) => {
      const it = h('div', { class: 'sitem' + (i === current ? ' on' : ''), f: true, onclick: () => { current = i; render() } }, icon(c.icon, 26), c.label, h('span', { class: 'chev' }, icon('right', 18)))
      it.addEventListener('vfocus', () => { if (current !== i) { current = i; window.setTimeout(() => render(true), 0) } })
      nav.appendChild(it)
    })
    const c = cats[current]; title.textContent = c.label; intro.textContent = c.intro
    clear(rowsBox)
    c.rows().forEach((r) => {
      const info = r.kind === 'info'
      rowsBox.appendChild(h('div', { class: 'srow' + (r.destructive ? ' bad' : ''), f: !info, onclick: () => r.act && r.act() },
        h('div', { class: 'l' }, h('div', { class: 'lb p' }, r.label), r.desc ? h('div', { class: 'ds s' }, r.desc) : null),
        r.value ? h('div', { class: 'v s' + (info ? ' mono' : '') }, r.value) : null,
        info ? null : h('span', { class: 'chev m' }, icon('right', 18))))
    })
    if (rowFocus >= 0 && rowsBox.children[rowFocus]) setFocus(rowsBox.children[rowFocus] as HTMLElement)
    if (focusNav) setFocus(nav.children[current + 1] as HTMLElement)
  }
  render()
  return {
    el,
    mount() { Catalog.onChange = () => render(); Catalog.start(false); focusFirst('.srow') },
    unmount() { Catalog.onChange = null },
    onBack() { App.go('home'); return true },
    /** Left from a row returns to the category it belongs to, not whichever one sits level with the row. */
    onKey(a: Action) {
      if (a.type === 'dir' && a.dir === 'left' && document.querySelector('.set-main .focus')) { setFocus(nav.children[current + 1] as HTMLElement); return true }
      return false
    },
  }
}

export { dialog, Free }
