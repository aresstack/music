package com.aresstack.music.model.mix;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;
import org.junit.Test;

/** Work package D3: one kind of lane for level, place and share of the room. */
public final class AutomationLaneTest {
    private static AutomationLane lane(String parameter, AutomationLane.Point... points) {
        return new AutomationLane("lane", "melody-ch", parameter, List.of(points));
    }

    private static AutomationLane.Point at(long tick, double value) {
        return new AutomationLane.Point(tick, value);
    }

    @Test
    public void betweenTwoPointsTheValueRunsInAStraightLineAndHoldsOutside() {
        AutomationLane swell = lane(AutomationLane.LEVEL, at(1920, -12), at(3840, 0), at(7680, -6));
        assertEquals(-12, swell.valueAt(0), 0);
        assertEquals(-12, swell.valueAt(1920), 0);
        assertEquals(-6, swell.valueAt(2880), 1e-12);
        assertEquals(0, swell.valueAt(3840), 0);
        assertEquals(-3, swell.valueAt(5760), 1e-12);
        assertEquals(-6, swell.valueAt(99_999), 0);
    }

    @Test
    public void twoPointsAtOneTickMakeAJump() {
        AutomationLane jump = lane(AutomationLane.X, at(0, -1), at(1920, -1), at(1920, 1), at(3840, 1));
        assertEquals(-1, jump.valueAt(1919), 0);
        assertEquals(1, jump.valueAt(1921), 0);
    }

    @Test
    public void placeAndRoomShareStayWithinTheRangeOfTheChannel() {
        lane(AutomationLane.X, at(0, -1), at(10, 1));
        lane(AutomationLane.DEPTH, at(0, 0), at(10, 1));
        lane(AutomationLane.REVERB, at(0, 0), at(10, 1));
        for (String parameter : List.of(AutomationLane.X, AutomationLane.DEPTH, AutomationLane.REVERB)) {
            try {
                lane(parameter, at(0, 0.5), at(10, 1.5));
                fail(parameter + " beyond 1 is refused");
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage(), expected.getMessage().contains(parameter));
            }
        }
        try {
            lane(AutomationLane.DEPTH, at(0, -0.1));
            fail("a depth before the front edge is refused");
        } catch (IllegalArgumentException expected) {
            // as the channel
        }
        // The level is dB around the level of the channel and has no range of its own.
        assertEquals(-60, lane(AutomationLane.LEVEL, at(0, -60)).valueAt(0), 0);
    }

    @Test
    public void anUnknownParameterIsKeptButNotKnown() {
        AutomationLane later = lane("brightness", at(0, 7));
        assertFalse(later.known());
        assertEquals(7, later.valueAt(0), 0);
        assertTrue(lane(AutomationLane.REVERB, at(0, 0.2)).known());
        assertEquals(List.of("level", "x", "depth", "reverb", "height"), AutomationLane.PARAMETERS);
    }

    @Test
    public void aMixFindsTheLaneOfAParameter() {
        AutomationLane x = new AutomationLane("lane-x", "melody-ch", AutomationLane.X, List.of(at(0, 0)));
        AutomationLane master = new AutomationLane("lane-m", null, AutomationLane.LEVEL, List.of(at(0, -3)));
        MixScene mix = new MixScene(new Room("hall", 2, 0.3), 0, LevelMode.FIXED, List.of(), List.of(x, master));
        assertSame(x, mix.lane("melody-ch", AutomationLane.X));
        assertSame(master, mix.lane(null, AutomationLane.LEVEL));
        assertNull(mix.lane("melody-ch", AutomationLane.DEPTH));
    }
}
