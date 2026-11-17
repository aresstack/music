package com.aresstack.music.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.util.List;
import org.junit.Test;

public final class TimeMapTest {
    @Test
    public void aConstantMapCountsBarsOfFourQuarters() {
        TimeMap time = TimeMap.of(120);
        assertEquals(0, time.tickOfBar(0));
        assertEquals(1920, time.tickOfBar(1));
        assertEquals(7, time.barOf(15359));
        assertEquals(8, time.barOf(15360));
        assertEquals(8, time.barsCovering(15360));
        assertEquals(9, time.barsCovering(15361));
        assertEquals(1, time.barsCovering(0));
        assertEquals(2.0, time.seconds(1920), 1e-9);
        assertEquals(Key.C_MAJOR, time.keyAt(5000));
    }

    @Test
    public void changesOfMeterAndTempoAreFollowed() {
        TimeMap time = new TimeMap(
                List.of(new TimeMap.Tempo(0, 120), new TimeMap.Tempo(1920, 60)),
                List.of(new TimeMap.Meter(0, 4, 4), new TimeMap.Meter(3840, 3, 4)),
                List.of(new TimeMap.KeyChange(0, Key.C_MAJOR)));
        assertEquals(3840, time.tickOfBar(2));
        assertEquals(3840 + 1440, time.tickOfBar(3));
        assertEquals(3, time.barOf(3840 + 1440));
        assertEquals(2.0 + 2 * 4.0, time.seconds(3840 + 1920), 1e-9);
        assertEquals(60, time.tempoAt(2000), 0);
    }

    @Test
    public void aMapStartsAtTickZeroAndKeepsItsOrder() {
        assertThrows(IllegalArgumentException.class, () -> new TimeMap(List.of(new TimeMap.Tempo(10, 120)),
                List.of(new TimeMap.Meter(0, 4, 4)), List.of(new TimeMap.KeyChange(0, Key.C_MAJOR))));
        assertThrows(IllegalArgumentException.class, () -> new TimeMap(
                List.of(new TimeMap.Tempo(0, 120), new TimeMap.Tempo(0, 90)),
                List.of(new TimeMap.Meter(0, 4, 4)), List.of(new TimeMap.KeyChange(0, Key.C_MAJOR))));
        assertThrows(IllegalArgumentException.class, () -> new TimeMap(List.of(new TimeMap.Tempo(0, 120)),
                List.of(new TimeMap.Meter(0, 4, 4), new TimeMap.Meter(1000, 3, 4)),
                List.of(new TimeMap.KeyChange(0, Key.C_MAJOR))));
    }
}
