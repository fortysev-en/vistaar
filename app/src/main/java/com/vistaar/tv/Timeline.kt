package com.vistaar.tv

import java.util.Calendar
import java.util.TimeZone

/**
 * Time-grid math for the Guide: a 3-hour window laid out at 1412/180 design px per minute (the 1920px design's grid).
 * Pure functions, so they are unit-tested; the web app has the same module in web/src/timeline.ts.
 */
object Timeline {
    const val SPAN_MIN = 180
    const val STEP_MIN = 30
    const val GRID_W = 1412
    const val GAP = 8
    /** A programme with less than this showing at a window edge is left out. */
    const val MIN_SLIVER_MIN = 6
    const val PPM = GRID_W / SPAN_MIN.toDouble()
    private const val MIN = 60_000L

    /** A programme laid out inside the window: [x] and [w] in design px; [cutLeft]/[cutRight] when it runs past an edge. */
    class Block(val x: Int, val w: Int, val cutLeft: Boolean, val cutRight: Boolean)

    /** [ms] rounded down to the half hour in local time (zones with a 45-minute offset still land on :00/:30 local). */
    fun floorStep(ms: Long, zoneOffset: Int = TimeZone.getDefault().getOffset(ms)): Long {
        val step = STEP_MIN * MIN
        return Math.floorDiv(ms + zoneOffset, step) * step - zoneOffset
    }

    fun windowEnd(winStart: Long) = winStart + SPAN_MIN * MIN

    /** The block for a programme [start]..[end] in the window, or null when it is entirely outside. */
    fun block(winStart: Long, start: Long, end: Long): Block? {
        val ws = winStart; val we = windowEnd(winStart)
        val vs = maxOf(start, ws); val ve = minOf(end, we)
        if (ve <= vs) return null
        val cutL = start < ws; val cutR = end > we
        if ((cutL || cutR) && ve - vs < MIN_SLIVER_MIN * MIN) return null // a few minutes peeking in at an edge: not worth a block
        val x = Math.round((vs - ws) / MIN.toDouble() * PPM).toInt()
        val w = (Math.round((ve - vs) / MIN.toDouble() * PPM).toInt() - GAP).coerceAtLeast(16)
        return Block(x, w, cutL, cutR)
    }

    /** Tick positions (design px, plus a small inset) and times every half hour from the window start. */
    fun ticks(winStart: Long): List<Pair<Int, Long>> =
        (0 until SPAN_MIN / STEP_MIN).map { i -> Math.round(i * STEP_MIN * PPM).toInt() + 4 to winStart + i * STEP_MIN * MIN }

    /** x of the "now" line inside the window, or null when now is outside it. */
    fun nowX(winStart: Long, now: Long): Int? =
        if (now < winStart || now >= windowEnd(winStart)) null else Math.round((now - winStart) / MIN.toDouble() * PPM).toInt()

    /** Local midnight of the day [day] days from [now]. */
    fun dayStart(now: Long, day: Int): Long = Calendar.getInstance().apply {
        timeInMillis = now; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0); add(Calendar.DAY_OF_YEAR, day)
    }.timeInMillis

    /** Keeps a window start inside day [day]: from its midnight to the last start that still fits the whole window in that day. */
    fun clampStart(winStart: Long, now: Long, day: Int): Long {
        val lo = dayStart(now, day); val hi = dayStart(now, day + 1) - SPAN_MIN * MIN
        return winStart.coerceIn(lo, maxOf(lo, hi))
    }

    /** Where the window opens when day [day] is chosen: this half hour for today, 18:00 for other days. */
    fun openStart(now: Long, day: Int): Long = if (day == 0) clampStart(floorStep(now), now, 0) else dayStart(now, day) + 18 * 60 * MIN
}
