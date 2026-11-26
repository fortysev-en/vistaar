import { focusFirst } from '../nav/focus'
import { h, brandMark } from '../dom'
import { Store } from '../store'
import { Platform } from '../platform'
import { App, DISCLAIMER_TEXT, DISCLAIMER_VERSION, type Screen } from './app'

/** First-run disclaimer, shown once to everyone (bump DISCLAIMER_VERSION to ask again). With { view: true } (from Settings) it is read-only. Port of DisclaimerActivity. */
export function disclaimerScreen(params?: { view?: boolean }): Screen {
  const view = !!(params && params.view)
  const accept = h('div', { class: 'bigbtn go dbtn', f: true, onclick: () => { if (view) App.go('settings', { cat: 3 }); else { Store.disclaimerV = DISCLAIMER_VERSION; App.home() } } }, view ? 'Close' : 'Accept and continue')
  const exit = h('div', { class: 'bigbtn sec dbtn', f: true, onclick: () => Platform.exit() }, 'Exit')
  const el = h('div', { class: 'screen' },
    h('div', { class: 'lbrand' }, brandMark(40), 'Vistaar'),
    h('div', { class: 'lleft disc' },
      h('div', { class: 'step' }, 'Disclaimer'),
      h('h1', null, 'Before you start'),
      h('div', { class: 'lead' }, DISCLAIMER_TEXT),
      h('div', { class: 'dbtns' }, accept, view ? null : exit)))
  return {
    el,
    mount() { focusFirst('.dbtn') },
    onBack() { if (view) { App.go('settings', { cat: 3 }); return true } Platform.exit(); return true },
  }
}
