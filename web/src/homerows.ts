import { GENRES, type Channel } from './types'

/** The category rows on Home: the viewer's favourite categories first, in their order, then the others. Port of HomeRows.kt. */
export const MAX_ROWS = 8

/** Free-playlist category names that belong to a Jio genre but are spelled differently. */
const FREE_ALIASES: { [g: number]: string[] } = { 7: ['Animation'], 15: ['Religious'], 17: ['Education'], 10: ['Documentary', 'Science'], 18: ['Shop'] }

export function freeMatches(genre: number, freeCategory: string): boolean {
  const name = (GENRES.filter((g) => g[0] === genre)[0] || [0, ''])[1]
  return [name].concat(FREE_ALIASES[genre] || []).some((n) => n.toLowerCase() === freeCategory.toLowerCase())
}

/** Genre ids to show: [picked] first in pick order, then the rest of [available]; categories with nothing in them are left out. */
export function rowOrder(picked: number[], available: number[], max = MAX_ROWS): number[] {
  const first = picked.filter((g) => available.indexOf(g) >= 0)
  return first.concat(available.filter((g) => first.indexOf(g) < 0)).slice(0, Math.max(max, first.length))
}

/** Each row's channels: Jio first (the chosen languages in the order picked), then free channels of the same category. */
export function buildRows(jio: Channel[], free: Channel[], picked: number[], langs: number[], freeCategory: (c: Channel) => string): Array<{ genre: number; name: string; list: Channel[] }> {
  const rank = (c: Channel) => { const i = langs.indexOf(c.lang); return i < 0 ? langs.length : i }
  const by: { [g: number]: Channel[] } = {}
  GENRES.forEach(([g]) => {
    const mine = jio.filter((c) => c.cat === g).map((c, i) => ({ c, i })).sort((a, b) => rank(a.c) - rank(b.c) || a.i - b.i).map((x) => x.c)
    by[g] = mine.concat(free.filter((c) => freeMatches(g, freeCategory(c))))
  })
  const available = GENRES.map((g) => g[0]).filter((g) => by[g].length)
  return rowOrder(picked, available).map((g) => ({ genre: g, name: GENRES.filter((x) => x[0] === g)[0][1], list: by[g] }))
}
