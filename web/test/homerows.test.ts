import { describe, expect, it } from 'vitest'
import { buildRows, freeMatches, rowOrder } from '../src/homerows'
import type { Channel } from '../src/types'

describe('home rows', () => {
  it('puts picked categories first in pick order', () => expect(rowOrder([8, 12], [5, 6, 12, 8])).toEqual([8, 12, 5, 6]))
  it('leaves empty categories out even if picked', () => expect(rowOrder([8], [5, 6])).toEqual([5, 6]))
  it('caps the rows unless more were picked', () => {
    expect(rowOrder([], [1, 2, 3], 2)).toEqual([1, 2])
    expect(rowOrder([3, 2, 1], [1, 2, 3], 2)).toEqual([3, 2, 1])
  })
  it('lists Jio by language preference, then free channels', () => {
    const ch = (id: string, lang: number, cat: number, url = '') => ({ id, name: id, order: 0, lang, hd: false, logo: '', url, ua: '', ref: '', cat }) as Channel
    const rows = buildRows([ch('1', 6, 8), ch('2', 1, 8)], [ch('f', -1, 0, 'u')], [8], [1, 6], () => 'Sports')
    expect(rows[0].list.map((c) => c.id)).toEqual(['2', '1', 'f'])
  })
  it('matches free category names to their genre', () => { expect(freeMatches(15, 'Religious')).toBe(true); expect(freeMatches(8, 'sports')).toBe(true) })
})
