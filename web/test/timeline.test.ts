import { describe, expect, it } from 'vitest'
import { block, clampStart, floorStep, GRID_W, nowX, openStart, PPM, SPAN_MIN, ticks, windowEnd } from '../src/timeline'

const MIN = 60_000
// 2026-11-24 20:00 local, whatever the machine's zone is
const at = (h: number, m = 0, d = 24) => new Date(2026, 10, d, h, m, 0, 0).getTime()

describe('timeline', () => {
  it('rounds down to the half hour in local time', () => {
    expect(floorStep(at(20, 42))).toBe(at(20, 30))
    expect(floorStep(at(20, 0))).toBe(at(20, 0))
    // a zone with a 45-minute offset still lands on :00 / :30 local
    const off = 5 * 3600_000 + 45 * MIN
    const utc = Date.UTC(2026, 10, 24, 15, 10) // 20:55 local at +5:45
    expect(((floorStep(utc, off) + off) / MIN) % 30).toBe(0)
    expect(floorStep(utc, off)).toBe(Date.UTC(2026, 10, 24, 14, 45)) // 20:30 local
  })

  it('lays a programme out by minute and clips it at the edges', () => {
    const w = at(20, 0)
    const inside = block(w, at(20, 30), at(21, 0))!
    expect(inside.x).toBe(Math.round(30 * PPM)); expect(inside.w).toBe(Math.round(30 * PPM) - 8)
    expect(inside.cutLeft).toBe(false); expect(inside.cutRight).toBe(false)
    const early = block(w, at(19, 0), at(20, 30))!
    expect(early.x).toBe(0); expect(early.cutLeft).toBe(true); expect(early.w).toBe(Math.round(30 * PPM) - 8)
    const late = block(w, at(22, 30), at(23, 30))!
    expect(late.cutRight).toBe(true); expect(late.x + late.w + 8).toBeCloseTo(GRID_W, -1)
    expect(block(w, at(23, 0), at(23, 30))).toBeNull()
    expect(block(w, at(18, 0), at(19, 0))).toBeNull()
  })

  it('drops a sliver peeking in at an edge but keeps a short programme inside the window', () => {
    const w = at(20, 0)
    expect(block(w, at(19, 30), at(20, 3))).toBeNull() // 3 minutes visible, cut
    expect(block(w, at(19, 30), at(20, 8))).not.toBeNull()
    const short = block(w, at(20, 30), at(20, 33))! // 3 minutes, fully inside
    expect(short.w).toBe(16)
  })

  it('has six half-hour ticks and a now line only inside the window', () => {
    const w = at(20, 0)
    const t = ticks(w)
    expect(t.length).toBe(SPAN_MIN / 30); expect(t[1].t).toBe(at(20, 30)); expect(t[0].x).toBe(4)
    expect(nowX(w, at(20, 42))).toBe(Math.round(42 * PPM))
    expect(nowX(w, at(19, 59))).toBeNull(); expect(nowX(w, windowEnd(w))).toBeNull()
  })

  it('keeps the window inside the chosen day and opens other days at 18:00', () => {
    const now = at(20, 42)
    expect(clampStart(at(23, 0), now, 0)).toBe(at(21, 0)) // last start that fits 3 hours before midnight
    expect(clampStart(at(-2, 0), now, 0)).toBe(at(0, 0))
    expect(openStart(now, 0)).toBe(at(20, 30))
    expect(openStart(now, 2)).toBe(at(18, 0, 26))
  })
})
