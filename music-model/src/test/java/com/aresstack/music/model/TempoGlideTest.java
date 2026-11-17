package com.aresstack.music.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

/** Accelerando and ritardando: a tempo that glides evenly per tick to the next one. */
public final class TempoGlideTest {
    private static final long BAR = 1920;

    /** 120 BPM for two bars, then a ritardando over two bars down to 60, then 60. */
    private static TimeMap ritardando() {
        return new TimeMap(List.of(new TimeMap.Tempo(0, 120), new TimeMap.Tempo(2 * BAR, 120, true),
                new TimeMap.Tempo(4 * BAR, 60)), List.of(new TimeMap.Meter(0, 4, 4)),
                List.of(new TimeMap.KeyChange(0, Key.C_MAJOR)));
    }

    @Test
    public void theTempoGlidesEvenlyBetweenItsPoints() {
        TimeMap time = ritardando();
        assertEquals(120, time.tempoAt(BAR), 1e-9);
        assertEquals(120, time.tempoAt(2 * BAR), 1e-9);
        assertEquals(90, time.tempoAt(3 * BAR), 1e-9);
        assertEquals(60, time.tempoAt(4 * BAR), 1e-9);
        assertEquals(60, time.tempoAt(9 * BAR), 1e-9);
        assertTrue(time.glidingAt(3 * BAR));
        assertFalse(time.glidingAt(BAR));
        assertFalse(time.steadyBetween(2 * BAR - 10, 2 * BAR + 10));
        assertTrue(time.steadyBetween(0, BAR));
    }

    @Test
    public void secondsAreTheExactIntegralOfTheGlide() {
        TimeMap time = ritardando();
        // Two bars at 120 BPM are four seconds.
        assertEquals(4.0, time.seconds(2 * BAR), 1e-9);
        // Eight quarters gliding from 120 to 60 BPM: 60 / (60 - 120) * 8 * ln(60 / 120) minutes = 8 ln 2 seconds.
        assertEquals(4.0 + 8 * Math.log(2), time.seconds(4 * BAR), 1e-9);
        // Longer than at 120 throughout, shorter than at 60 throughout.
        double glide = time.seconds(4 * BAR) - time.seconds(2 * BAR);
        assertTrue(glide > 4.0 && glide < 8.0);
        // Then one bar at 60 BPM: four seconds.
        assertEquals(4.0 + 8 * Math.log(2) + 4.0, time.seconds(5 * BAR), 1e-9);
    }

    @Test
    public void ticksAtSecondsInvertTheGlide() {
        TimeMap time = ritardando();
        for (long tick = 0; tick <= 6 * BAR; tick += 97) {
            long back = time.tickAt(time.seconds(tick));
            assertTrue(tick + " came back as " + back, Math.abs(back - tick) <= 1);
        }
    }

    @Test
    public void aPlayerOfStepsHearsTheSameTimes() {
        TimeMap time = ritardando();
        List<TimeMap.Tempo> steps = time.playedTempos();
        assertEquals("a step every " + TimeMap.GLIDE_STEP_TICKS + " ticks of the glide, and the tempos around it",
                2 + 2 * BAR / TimeMap.GLIDE_STEP_TICKS, steps.size());
        TimeMap stepped = new TimeMap(steps, time.meters(), time.keys());
        for (long tick = 0; tick <= 6 * BAR; tick += TimeMap.GLIDE_STEP_TICKS) {
            assertEquals("at tick " + tick, time.seconds(tick), stepped.seconds(tick), 1e-9);
        }
        // The steps slow down all the way.
        for (int i = 2; i < steps.size() - 1; i++) {
            assertTrue(steps.get(i).bpm() < steps.get(i - 1).bpm());
        }
    }

    @Test
    public void anAccelerandoGetsFaster() {
        TimeMap time = new TimeMap(List.of(new TimeMap.Tempo(0, 80, true), new TimeMap.Tempo(BAR, 160)),
                List.of(new TimeMap.Meter(0, 4, 4)), List.of(new TimeMap.KeyChange(0, Key.C_MAJOR)));
        double plain = TimeMap.of(80).seconds(BAR);
        assertTrue(time.seconds(BAR) < plain);
        assertEquals(120, time.tempoAt(BAR / 2), 1e-9);
    }

    @Test
    public void withoutAGlideNothingChanges() {
        TimeMap time = new TimeMap(List.of(new TimeMap.Tempo(0, 108), new TimeMap.Tempo(BAR, 90)),
                List.of(new TimeMap.Meter(0, 4, 4)), List.of(new TimeMap.KeyChange(0, Key.C_MAJOR)));
        assertEquals(time.tempos(), time.playedTempos());
        assertEquals(BAR / 480.0 * 60 / 108 + BAR / 480.0 * 60 / 90, time.seconds(2 * BAR), 1e-12);
    }

    @Test(expected = IllegalArgumentException.class)
    public void theLastTempoCannotGlide() {
        new TimeMap(List.of(new TimeMap.Tempo(0, 108, true)), List.of(new TimeMap.Meter(0, 4, 4)),
                List.of(new TimeMap.KeyChange(0, Key.C_MAJOR)));
    }
}
