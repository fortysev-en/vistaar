package com.vistaar.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineTest {
    private val min = 60_000L
    private val w = 1_700_000_000_000L - 1_700_000_000_000L % (30 * min) // some half hour
    private fun ppm(m: Int) = Math.round(m * Timeline.PPM).toInt()

    @Test fun roundsDownToTheHalfHourLocally() {
        assertEquals(w, Timeline.floorStep(w + 17 * min, 0))
        val off = (5 * 60 + 45) * min // a +5:45 zone still lands on :00 / :30 local
        assertEquals(0L, Math.floorMod(Timeline.floorStep(w + 17 * min, off.toInt()) + off, 30 * min))
    }

    @Test fun laysOutAndClips() {
        val inside = Timeline.block(w, w + 30 * min, w + 60 * min)!!
        assertEquals(ppm(30), inside.x); assertEquals(ppm(30) - Timeline.GAP, inside.w)
        assertFalse(inside.cutLeft); assertFalse(inside.cutRight)
        val early = Timeline.block(w, w - 60 * min, w + 30 * min)!!
        assertEquals(0, early.x); assertTrue(early.cutLeft)
        val late = Timeline.block(w, w + 150 * min, w + 240 * min)!!
        assertTrue(late.cutRight)
        assertNull(Timeline.block(w, w + 180 * min, w + 210 * min))
        assertNull(Timeline.block(w, w - 60 * min, w))
    }

    @Test fun dropsEdgeSliversButKeepsShortProgrammesInside() {
        assertNull(Timeline.block(w, w - 30 * min, w + 3 * min))
        assertNotNull(Timeline.block(w, w - 30 * min, w + 8 * min))
        assertEquals(16, Timeline.block(w, w + 30 * min, w + 33 * min)!!.w)
    }

    @Test fun ticksAndNowLine() {
        val t = Timeline.ticks(w)
        assertEquals(6, t.size); assertEquals(4, t[0].first); assertEquals(w + 30 * min, t[1].second)
        assertEquals(ppm(42), Timeline.nowX(w, w + 42 * min))
        assertNull(Timeline.nowX(w, w - 1)); assertNull(Timeline.nowX(w, Timeline.windowEnd(w)))
    }
}
