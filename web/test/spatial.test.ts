import { describe, expect, it } from 'vitest'
import { pickNext, type Cand, type Rect } from '../src/nav/spatial'

const r = (left: number, top: number, w = 100, h = 60): Rect => ({ left, top, right: left + w, bottom: top + h })
const c = (id: string, left: number, top: number, w?: number, h?: number): Cand => ({ id, rect: r(left, top, w, h) })

describe('pickNext', () => {
  const grid = [c('a', 0, 0), c('b', 120, 0), c('c', 240, 0), c('d', 0, 80), c('e', 120, 80), c('f', 240, 80)]
  it('moves along rows and columns', () => {
    expect(pickNext(r(0, 0), 'right', grid)).toBe('b')
    expect(pickNext(r(120, 0), 'down', grid)).toBe('e')
    expect(pickNext(r(240, 80), 'left', grid)).toBe('e')
    expect(pickNext(r(120, 80), 'up', grid)).toBe('b')
  })
  it('returns null at an edge instead of wrapping', () => {
    expect(pickNext(r(0, 0), 'left', grid)).toBeNull()
    expect(pickNext(r(240, 80), 'down', grid)).toBeNull()
  })
  it('prefers a candidate in the beam over a nearer off-axis one', () => {
    const cands = [c('near-off', 150, 70, 40, 20), c('far-beam', 0, 300)]
    expect(pickNext(r(0, 0), 'down', cands)).toBe('far-beam')
  })
  it('from a tall list row, Left goes to the category column, not a far row', () => {
    const cats = [c('cat1', 0, 0, 200, 60), c('cat2', 0, 70, 200, 60), c('cat3', 0, 140, 200, 60)]
    const row = r(260, 75, 700, 90)
    expect(pickNext(row, 'left', cats)).toBe('cat2')
  })
  it('ignores candidates behind', () => {
    expect(pickNext(r(0, 0), 'up', grid)).toBeNull()
  })
})
