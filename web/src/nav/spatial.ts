/** Pure directional focus search (no DOM), modelled on Android's FocusFinder so the web app moves focus like the Android app. */

export type Dir = 'left' | 'right' | 'up' | 'down'
export interface Rect { left: number; top: number; right: number; bottom: number }
export interface Cand { id: string; rect: Rect }

const cx = (r: Rect) => (r.left + r.right) / 2
const cy = (r: Rect) => (r.top + r.bottom) / 2

/** Is [c] on the [dir] side of [from]? Uses edges, with a little tolerance for rows that overlap slightly. */
function isOnSide(from: Rect, c: Rect, dir: Dir): boolean {
  switch (dir) {
    case 'left': return c.left < from.left && c.right <= from.right
    case 'right': return c.right > from.right && c.left >= from.left
    case 'up': return c.top < from.top && c.bottom <= from.bottom
    case 'down': return c.bottom > from.bottom && c.top >= from.top
  }
}

/** Do the two rects overlap on the axis perpendicular to travel? Those are in the "beam" and win over off-axis candidates. */
function inBeam(from: Rect, c: Rect, dir: Dir): boolean {
  return dir === 'left' || dir === 'right' ? c.bottom > from.top && c.top < from.bottom : c.right > from.left && c.left < from.right
}

/** Gap along the direction of travel (0 if the edges touch or overlap). */
function major(from: Rect, c: Rect, dir: Dir): number {
  switch (dir) {
    case 'left': return Math.max(0, from.left - c.right)
    case 'right': return Math.max(0, c.left - from.right)
    case 'up': return Math.max(0, from.top - c.bottom)
    case 'down': return Math.max(0, c.top - from.bottom)
  }
}

/** Offset between centres on the perpendicular axis. */
function minor(from: Rect, c: Rect, dir: Dir): number {
  return dir === 'left' || dir === 'right' ? Math.abs(cy(c) - cy(from)) : Math.abs(cx(c) - cx(from))
}

/** Best candidate to move to, or null. Android's weighting: major^2 + 13*minor^2, with beam candidates always preferred. */
export function pickNext(from: Rect, dir: Dir, cands: Cand[]): string | null {
  let best: { id: string; beam: boolean; score: number } | null = null
  for (const c of cands) {
    if (!isOnSide(from, c.rect, dir)) continue
    const beam = inBeam(from, c.rect, dir)
    const mj = major(from, c.rect, dir), mn = minor(from, c.rect, dir)
    const score = mj * mj + 13 * mn * mn
    if (!best || (beam && !best.beam) || (beam === best.beam && score < best.score)) best = { id: c.id, beam, score }
  }
  return best ? best.id : null
}
