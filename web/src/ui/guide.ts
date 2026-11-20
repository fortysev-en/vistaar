import { Catalog, Epg } from '../catalog'
import { clear, h } from '../dom'
import { focused, focusFirst } from '../nav/focus'
import type { Channel, Program } from '../types'
import { App, makeHeader, type Name, type Screen } from './app'
import { fmtTime, liveBadge, logoBox, VList } from './widgets'

/** Guide: pick a Jio channel on the left, see today's programmes on the right. Port of GuideActivity. */
export function guideScreen(p?: { id?: string; from?: { name: Name; params?: any } }): Screen {
  const header = makeHeader('guide')
  let current: Channel | null = null
  let timer = 0

  const progs = h('div', { style: { position: 'absolute', left: '0', right: '0', top: '56px', bottom: '0', overflow: 'hidden' } })
  const title = h('div', { style: { fontSize: '36px', fontWeight: '600', height: '48px', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' } })
  const right = h('div', { style: { position: 'absolute', left: '800px', right: '96px', top: '160px', bottom: '54px' } }, title, progs)
  const empty = h('div', { class: 'empty hide' }, 'Checking which channels your plan includes…')
  const list = new VList<Channel>({
    rowH: 88,
    render: (c) => h('div', null, h('span', { class: 'num s' }, Catalog.numberOf(c)), logoBox(c, 52), h('div', { class: 'info' }, h('div', { class: 'nm p' }, c.name))),
    onFocus: (c) => show(c),
    onOpen: (c) => App.go('player', { id: c.id, from: { name: 'guide', params: p } }),
  })
  const left = h('div', { style: { position: 'absolute', left: '88px', top: '160px', width: '660px', bottom: '54px' } }, list.el, empty)
  const el = h('div', { class: 'screen' }, left, right, header.el)

  function show(c: Channel) {
    if (current && current.id === c.id) return
    current = c; title.textContent = c.name; clear(progs)
    clearTimeout(timer)
    timer = window.setTimeout(() => {
      Epg.load(c.id).then((l) => {
        if (!current || current.id !== c.id) return
        render(l)
      })
    }, 150)
  }
  function render(l: Program[]) {
    clear(progs)
    if (!l.length) { progs.appendChild(h('div', { style: { color: 'var(--muted)', fontSize: '24px', marginTop: '24px' } }, 'No guide available for this channel.')); return }
    const now = Date.now()
    const nowIdx = Math.max(0, l.findIndex((x) => now >= x.start && now <= x.end))
    const first = Math.max(0, nowIdx - 1)
    l.slice(first, first + 8).forEach((x) => {
      const on = now >= x.start && now <= x.end
      progs.appendChild(h('div', { style: { display: 'flex', alignItems: 'center', height: '84px', padding: '0 24px', marginBottom: '8px', borderRadius: '16px', background: on ? 'var(--sel)' : 'var(--surface)' } },
        h('span', { class: 'mono', style: { width: '150px', color: 'var(--muted)', fontSize: '20px' } }, fmtTime(x.start)),
        h('div', { style: { flex: '1', minWidth: '0' } }, h('div', { style: { fontSize: '24px', fontWeight: '600', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' } }, x.title), x.desc ? h('div', { style: { fontSize: '18px', color: 'var(--muted)', marginTop: '4px', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' } }, x.desc) : null),
        on ? liveBadge() : null))
    })
  }
  function refresh() {
    header.update()
    const pool = Catalog.playableChannels()
    list.setItems(pool, (c) => c.id)
    empty.className = 'empty' + (pool.length ? ' hide' : '')
    if (pool.length && !current) {
      const start = (p && p.id && pool.findIndex((c) => c.id === p.id)) || Catalog.recents().map((r) => pool.findIndex((c) => c.id === r.id)).filter((i) => i >= 0)[0] || 0
      list.focusIndex(Math.max(0, start))
    }
  }
  return {
    el,
    mount() { Catalog.onChange = refresh; Catalog.start(false); refresh(); if (!focused()) focusFirst() },
    unmount() { clearTimeout(timer); Catalog.onChange = null },
    onBack() { if (p && p.from && p.from.name === 'player') { App.go('player', p.from.params); return true } return false },
    onKey(a) {
      const f = focused()
      if (a.type === 'dir' && f && list.contains(f) && (a.dir === 'up' || a.dir === 'down')) return list.step(a.dir)
      return false
    },
  }
}
