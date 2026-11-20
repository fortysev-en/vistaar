import { Catalog } from '../catalog'
import { Free } from '../free'
import { h, icon } from '../dom'
import { focusFirst } from '../nav/focus'
import { Store } from '../store'
import { App, type Screen } from './app'
import { keyboard } from './keyboard'

/** Free TV playlist: pick an iptv-org country playlist or type any M3U link. The old list is kept if the new one fails. Port of PlaylistActivity. */
export function playlistScreen(): Screen {
  const presets: Array<[string, string]> = [['India', 'in'], ['United States', 'us'], ['United Kingdom', 'gb'], ['Pakistan', 'pk'], ['Bangladesh', 'bd'], ['Nepal', 'np'],
    ['Sri Lanka', 'lk'], ['United Arab Emirates', 'ae'], ['Singapore', 'sg'], ['Malaysia', 'my'], ['Canada', 'ca'], ['Australia', 'au']]
  let url = Store.playlistUrl
  let busy = false
  const urlText = h('div', { style: { font: '26px "Roboto Mono",Consolas,monospace', wordBreak: 'break-all', maxHeight: '80px', overflow: 'hidden' } })
  const status = h('div', { style: { fontSize: '20px', color: 'var(--muted)', marginTop: '14px', minHeight: '28px' } })
  const say = (m: string, bad: boolean) => { status.textContent = m; status.style.color = bad ? 'var(--amber)' : 'var(--muted)' }
  const paint = () => { urlText.textContent = url || 'https://'; urlText.style.color = url ? 'var(--text)' : 'var(--dim)' }

  async function apply(link: string) {
    if (busy) return
    if (!/^https?:\/\//.test(link)) { say('The link must start with http:// or https://', true); return }
    busy = true; say('Loading playlist…', false)
    const err = await Free.switchTo(link)
    busy = false
    if (err) say(err + '. The previous playlist is still in use.', true); else { say('Loaded ' + Free.channels.length + ' channels. Free TV now uses this playlist.', false); Free.langKey = ''; Catalog.start(false) }
  }

  const kb = keyboard((k) => {
    if (k === 'del') url = url.slice(0, -1); else if (k === 'clear') url = ''; else if (k === 'https://') url = 'https://'; else if (url.length < 200) url += k
    paint()
  }, true)
  const use = h('div', { class: 'bigbtn go', f: true, style: { marginTop: '20px' }, onclick: () => apply(url.trim()) }, 'Use this link')
  const presetList = h('div', { style: { marginTop: '8px' } }, presets.map((pr, i) => {
    const link = 'https://iptv-org.github.io/iptv/countries/' + pr[1] + '.m3u'
    return h('div', { class: 'srow', f: true, style: { minHeight: '68px', padding: '0 22px' }, onclick: () => { url = link; paint(); apply(link) } },
      h('div', { class: 'l' }, h('div', { class: 'lb p', style: { fontSize: '22px' } }, pr[0])), h('div', { class: 'v s mono' }, i === 0 ? 'default' : pr[1].toUpperCase()))
  }))
  const el = h('div', { class: 'screen' },
    h('div', { style: { position: 'absolute', right: '0', top: '0', width: '640px', height: '1080px', background: 'var(--surface2)' } }),
    h('div', { class: 'backp', f: true, onclick: () => App.go('settings') }, icon('left', 20), 'Settings'),
    h('div', { style: { position: 'absolute', left: '96px', top: '150px', width: '1000px' } },
      h('div', { style: { fontSize: '48px', fontWeight: '600' } }, 'Free TV playlist'),
      h('div', { style: { fontSize: '22px', color: 'var(--muted)', margin: '8px 0 20px' } }, 'Any M3U link works. The default is the iptv-org India list.'),
      h('div', { class: 'qbox', style: { height: '104px', display: 'block', padding: '14px 24px', overflow: 'hidden' } }, urlText),
      h('div', { style: { marginTop: '12px' } }, kb), use, status),
    h('div', { style: { position: 'absolute', right: '70px', top: '150px', width: '500px', bottom: '54px', overflow: 'hidden' } },
      h('div', { style: { fontSize: '28px', fontWeight: '600', marginBottom: '16px', marginLeft: '8px' } }, 'Country playlists'), presetList))
  paint()
  return { el, mount() { focusFirst('.kb .key') }, onBack() { App.go('settings'); return true }, onKey(a) { if (a.type === 'char') { url += a.ch; paint(); return true } return false } }
}
