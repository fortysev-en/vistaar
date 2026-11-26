/**
 * Time-grid math for the Guide: a 3-hour window laid out at 1412/180 px per minute (the design's 1920px grid).
 * Pure functions (unit-tested); the Android app has the same module in Timeline.kt.
 */
export const SPAN_MIN = 180
export const STEP_MIN = 30
export const GRID_W = 1412
export const GAP = 8
export const PPM = GRID_W / SPAN_MIN
/** A programme with less than this showing at a window edge is left out. */
export const MIN_SLIVER_MIN = 6
const MIN = 60_000

/** A programme laid out inside the window: x and w in px; cutLeft/cutRight when it runs past an edge. */
export interface Block { x: number; w: number; cutLeft: boolean; cutRight: boolean }

const zoneOffsetMs = (ms: number) => -new Date(ms).getTimezoneOffset() * MIN

/** ms rounded down to the half hour in local time. */
export function floorStep(ms: number, zoneOffset = zoneOffsetMs(ms)): number {
  const step = STEP_MIN * MIN
  return Math.floor((ms + zoneOffset) / step) * step - zoneOffset
}

export const windowEnd = (winStart: number) => winStart + SPAN_MIN * MIN

/** The block for a programme start..end in the window, or null when it is entirely outside (or only a sliver shows at an edge). */
export function block(winStart: number, start: number, end: number): Block | null {
  const we = windowEnd(winStart)
  const vs = Math.max(start, winStart), ve = Math.min(end, we)
  if (ve <= vs) return null
  const cutLeft = start < winStart, cutRight = end > we
  if ((cutLeft || cutRight) && ve - vs < MIN_SLIVER_MIN * MIN) return null
  const x = Math.round((vs - winStart) / MIN * PPM)
  const w = Math.max(16, Math.round((ve - vs) / MIN * PPM) - GAP)
  return { x, w, cutLeft, cutRight }
}

/** Tick positions (px, plus a small inset) and times every half hour from the window start. */
export function ticks(winStart: number): Array<{ x: number; t: number }> {
  const out: Array<{ x: number; t: number }> = []
  for (let i = 0; i < SPAN_MIN / STEP_MIN; i++) out.push({ x: Math.round(i * STEP_MIN * PPM) + 4, t: winStart + i * STEP_MIN * MIN })
  return out
}

/** x of the "now" line inside the window, or null when now is outside it. */
export function nowX(winStart: number, now: number): number | null {
  return now < winStart || now >= windowEnd(winStart) ? null : Math.round((now - winStart) / MIN * PPM)
}

/** Local midnight of the day [day] days from [now]. */
export function dayStart(now: number, day: number): number {
  const d = new Date(now); d.setHours(0, 0, 0, 0); d.setDate(d.getDate() + day)
  return d.getTime()
}

/** Keeps a window start inside day [day]: from its midnight to the last start that still fits the whole window in that day. */
export function clampStart(winStart: number, now: number, day: number): number {
  const lo = dayStart(now, day), hi = dayStart(now, day + 1) - SPAN_MIN * MIN
  return Math.min(Math.max(winStart, lo), Math.max(lo, hi))
}

/** Where the window opens when day [day] is chosen: this half hour for today, 18:00 for other days. */
export function openStart(now: number, day: number): number {
  return day === 0 ? clampStart(floorStep(now), now, 0) : dayStart(now, day) + 18 * 60 * MIN
}
