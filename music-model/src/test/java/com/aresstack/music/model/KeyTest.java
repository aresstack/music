package com.aresstack.music.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

/** F2: key signatures and the spelling of every pitch in all 24 keys. */
public final class KeyTest {
    private static final int[] NATURAL = {0, 2, 4, 5, 7, 9, 11};

    @Test
    public void everyKeySpellsEveryPitchAsThatPitch() {
        assertEquals(24, Key.all().size());
        for (Key key : Key.all()) {
            for (int pitch = 0; pitch < 128; pitch++) {
                Key.Spelling spelling = key.spell(pitch);
                assertEquals(key + " " + pitch, pitch,
                        (spelling.octave() + 1) * 12 + NATURAL[spelling.letter()] + spelling.alteration());
                assertTrue(key + " " + pitch, Math.abs(spelling.alteration()) <= 2);
            }
        }
    }

    @Test
    public void everyScaleIsWrittenWithSevenLettersAsTheSignatureSays() {
        for (Key key : Key.all()) {
            int[] signature = key.signature();
            int count = 0;
            for (int alteration : signature) {
                count += alteration;
            }
            assertEquals(key.toString(), key.fifths(), count);
            Set<Integer> letters = new HashSet<>();
            for (int pc : key.scale()) {
                Key.Spelling spelling = key.spell(60 + pc);
                letters.add(spelling.letter());
                assertEquals(key + ": a scale tone needs no accidental against the signature",
                        signature[spelling.letter()], spelling.alteration());
                assertTrue(key.contains(60 + pc));
            }
            assertEquals(key.toString(), 7, letters.size());
            assertTrue(key.toString(), Math.abs(key.fifths()) <= 6);
        }
    }

    @Test
    public void theKeySignaturesAreTheUsualOnes() {
        String[] major = {"C 0", "Db -5", "D 2", "Eb -3", "E 4", "F -1", "F# 6", "G 1", "Ab -4", "A 3", "Bb -2", "B 5"};
        String[] minor = {"C -3", "C# 4", "D -1", "Eb -6", "E 1", "F -4", "F# 3", "G -2", "G# 5", "A 0", "Bb -5", "B 2"};
        for (int tonic = 0; tonic < 12; tonic++) {
            Key dur = new Key(tonic, Key.Mode.MAJOR);
            Key moll = new Key(tonic, Key.Mode.MINOR);
            assertEquals(major[tonic], dur.tonicName() + " " + dur.fifths());
            assertEquals(minor[tonic], moll.tonicName() + " " + moll.fifths());
        }
    }

    @Test
    public void dMinorWritesBFlatAndCSharp() {
        Key d = new Key(2, Key.Mode.MINOR);
        assertEquals(-1, d.fifths());
        assertEquals("D minor", d.name());
        assertEquals("d-Moll", d.germanName());
        assertEquals("Bb3", d.spell(58).name());
        assertEquals("C#4", d.spell(61).name());
        assertEquals("B3", d.spell(59).name());   // raised sixth (melodic minor)
        assertEquals("Eb4", d.spell(63).name());  // Neapolitan
        assertEquals("F#4", d.spell(66).name());  // Picardy third
        assertEquals("G#4", d.spell(68).name());
        assertTrue(d.contains(61));
        assertTrue(d.contains(59));
        assertFalse(d.contains(63));
    }

    @Test
    public void cMajorWritesTheChromaticTonesAsUsual() {
        StringBuilder written = new StringBuilder();
        for (int pitch = 60; pitch < 72; pitch++) {
            written.append(Key.C_MAJOR.spell(pitch).name()).append(' ');
        }
        assertEquals("C4 C#4 D4 Eb4 E4 F4 F#4 G4 G#4 A4 Bb4 B4 ", written.toString());
    }

    @Test
    public void sharpKeysUseNaturalsAndDoubleSharpsWhereTheyMust() {
        Key fSharp = new Key(6, Key.Mode.MAJOR);
        assertEquals("E4", fSharp.spell(64).name()); // lowered seventh: E natural against E sharp
        assertEquals("E#4", fSharp.spell(65).name());
        Key gSharpMinor = new Key(8, Key.Mode.MINOR);
        assertEquals("Fx4", gSharpMinor.spell(67).name()); // leading tone of G sharp minor
        assertEquals("G4", Key.parse("G").spell(67).name());
        assertEquals("F4", Key.parse("G major").spell(65).name()); // lowered seventh of G
    }

    @Test
    public void readsKeysAsPeopleWriteThem() {
        assertEquals(new Key(2, Key.Mode.MINOR), Key.parse("D minor"));
        assertEquals(new Key(2, Key.Mode.MINOR), Key.parse("d-Moll"));
        assertEquals(new Key(2, Key.Mode.MINOR), Key.parse("Dm"));
        assertEquals(new Key(2, Key.Mode.MINOR), Key.parse("d"));
        assertEquals(new Key(6, Key.Mode.MAJOR), Key.parse("F# major"));
        assertEquals(new Key(6, Key.Mode.MAJOR), Key.parse("Fis-Dur"));
        assertEquals(new Key(10, Key.Mode.MAJOR), Key.parse("Bb"));
        assertEquals(new Key(10, Key.Mode.MAJOR), Key.parse("B-Dur"));
        assertEquals(new Key(11, Key.Mode.MAJOR), Key.parse("H-Dur"));
        assertEquals(new Key(11, Key.Mode.MAJOR), Key.parse("B major"));
        assertEquals(new Key(3, Key.Mode.MINOR), Key.parse("es-Moll"));
        assertEquals(new Key(8, Key.Mode.MAJOR), Key.parse("As-Dur"));
        assertEquals(new Key(4, Key.Mode.MINOR), Key.parse("Em"));
        assertEquals(Key.C_MAJOR, Key.parse("C major"));
        for (Key key : Key.all()) {
            assertEquals(key, Key.parse(key.name()));
            assertEquals(key, Key.parse(key.germanName()));
        }
        // Six flats or six sharps: as written.
        Key gFlat = Key.parse("Gb major");
        assertEquals(-6, gFlat.fifths());
        assertEquals("Gb major", gFlat.name());
        assertEquals("Ges-Dur", gFlat.germanName());
        assertEquals(gFlat, Key.parse("Ges-Dur"));
        assertEquals(gFlat, Key.parse(gFlat.name()));
        assertEquals(6, Key.parse("F# major").fifths());
        Key dSharp = Key.parse("D# minor");
        assertEquals(6, dSharp.fifths());
        assertEquals("D# minor", dSharp.name());
        assertEquals("dis-Moll", dSharp.germanName());
        assertEquals(dSharp, Key.parse("dis-Moll"));
        assertEquals(-6, Key.parse("Eb minor").fifths());
        assertEquals(-6, Key.parse("es-Moll").fifths());
        // Elsewhere the spelling follows from the tonic: C# major stays D flat major.
        assertEquals(Key.parse("Db major"), Key.parse("C# major"));
        assertEquals(new Key(6, Key.Mode.MAJOR), new Key(6, Key.Mode.MAJOR, false));
        assertEquals(new Key(1, Key.Mode.MAJOR), new Key(1, Key.Mode.MAJOR, false));
    }

    @Test(expected = IllegalArgumentException.class)
    public void refusesWhatIsNoKey() {
        Key.parse("Q minor");
    }
}
