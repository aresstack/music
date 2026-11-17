package com.aresstack.music.model;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import org.junit.Test;

/** F2: chord symbols of a harmony plan and their tones. */
public final class ChordTest {
    private static int[] sorted(String symbol) {
        int[] tones = Chord.parse(symbol).tones();
        Arrays.sort(tones);
        return tones;
    }

    @Test
    public void readsTheUsualSymbols() {
        assertArrayEquals(new int[]{0, 4, 7}, sorted("C"));
        assertArrayEquals(new int[]{2, 5, 9}, sorted("Dm"));
        assertArrayEquals(new int[]{1, 4, 7, 9}, sorted("A7"));
        assertArrayEquals(new int[]{2, 5, 9, 10}, sorted("Bbmaj7"));
        assertArrayEquals(new int[]{0, 6, 9}, sorted("F#dim"));
        assertArrayEquals(new int[]{0, 2, 7}, sorted("Gsus4"));
        assertArrayEquals(new int[]{0, 4, 7}, sorted("C/E"));
        assertArrayEquals(new int[]{2, 5, 9, 11}, sorted("Bm7b5"));
        assertArrayEquals(new int[]{0, 4, 8}, sorted("Caug"));
        assertArrayEquals(new int[]{0, 2, 5, 9}, sorted("Dm7"));
        assertArrayEquals(new int[]{2, 9}, sorted("D5"));
        assertArrayEquals(new int[]{2, 6, 9, 11}, sorted("D6"));
        assertTrue(Chord.parse("Gm/Bb").contains(46));
    }

    @Test
    public void extensionsKeepTheirTonesAndTheQualityOfTheSeventh() {
        assertArrayEquals(new int[]{0, 2, 4, 7, 10}, sorted("C9"));
        assertArrayEquals(new int[]{0, 2, 4, 7, 11}, sorted("Cmaj9"));
        assertArrayEquals(new int[]{0, 2, 4, 7, 11}, sorted("CΔ9"));
        assertArrayEquals(new int[]{0, 2, 3, 7, 10}, sorted("Cm9"));
        assertArrayEquals(new int[]{0, 2, 4, 5, 7, 10}, sorted("C11"));
        assertArrayEquals(new int[]{0, 2, 4, 7, 9, 10}, sorted("C13"));
        assertArrayEquals(new int[]{0, 2, 4, 7, 9}, sorted("C69"));
        assertArrayEquals(new int[]{0, 2, 4, 7}, sorted("Cadd9"));
        assertArrayEquals(new int[]{0, 1, 4, 7, 10}, sorted("C7b9"));
        assertArrayEquals(new int[]{0, 3, 4, 7, 10}, sorted("C7(#9)"));
        assertArrayEquals(new int[]{0, 2, 4, 6, 7, 11}, sorted("Cmaj9#11"));
        assertArrayEquals(new int[]{0, 5, 7, 10}, sorted("C7sus4"));
        assertArrayEquals(new int[]{0, 3, 6, 9}, sorted("Cdim7"));
        assertArrayEquals(new int[]{0, 3, 6, 10}, sorted("Cø7"));
        assertArrayEquals(new int[]{0, 3, 7, 11}, sorted("CmMaj7"));
        assertTrue(Chord.parse("C9").contains(62));
        assertTrue(!Chord.parse("Cmaj9").contains(70));
    }

    @Test
    public void unknownExtensionsAreRefusedSoTheScaleApplies() {
        assertNull(Chord.tryParse("C7alt"));
        assertNull(Chord.tryParse("C9x"));
    }

    @Test
    public void somethingElseIsNoChord() {
        assertNull(Chord.tryParse("N.C."));
        assertNull(Chord.tryParse(""));
        assertNull(Chord.tryParse("Hm"));
    }
}
