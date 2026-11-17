package com.aresstack.music.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public final class CompositionValidatorTest {
    private static final Voice V1 = new Voice("v1", "Stimme 1", null, Voice.Stem.AUTO);
    private static final Part PIANO = new Part("piano", "Klavier", Role.HARMONY, "Piano", 0, null,
            Part.StaffLayout.SINGLE, List.of(V1), null);
    private static final Part FLUTE = new Part("flute", "Flöte", Role.MELODY, "Flute", 73, null,
            Part.StaffLayout.SINGLE, List.of(new Voice("v2", null, null, null)), null);

    private static Composition piece(List<Track> tracks) {
        return new Composition(Meta.EMPTY, TimeMap.of(108),
                List.of(new Section("whole", "Ganzes Stück", null, 0, 3840, null, List.of("C", "G"), null)),
                List.of(PIANO, FLUTE), tracks, List.of());
    }

    private static MidiClip clip(String id, long start, long length, String part, Note... notes) {
        return new MidiClip(id, start, length, part, List.of(notes));
    }

    @Test
    public void twoClipsWithTwoPartsOnOneTrackAreFine() {
        Track lane = new Track("lane", null, Track.Kind.MIDI, "piano", List.of(
                clip("c1", 0, 1920, null, new Note("n1", 0, 480, 60, 90, "v1")),
                clip("c2", 1920, 1920, "flute", new Note("n2", 0, 480, 72, 90, "v2"))));
        assertEquals(List.of(), CompositionValidator.validate(piece(List.of(lane))));
        assertEquals(1, piece(List.of(lane)).notesOf("flute").size());
        assertEquals(1920, piece(List.of(lane)).notesOf("flute").get(0).tick());
    }

    @Test
    public void overlapIsRefusedByTheValidatorButCanBeStored() {
        Track lane = new Track("lane", null, Track.Kind.MIDI, "piano", List.of(
                clip("c1", 0, 1920, null), clip("c2", 960, 1920, null)));
        List<CompositionValidator.Violation> found = CompositionValidator.validate(piece(List.of(lane)));
        assertEquals(1, found.size());
        assertTrue(found.get(0).toString(), found.get(0).message().startsWith("overlaps"));
    }

    @Test
    public void brokenReferencesAndNotesOutsideTheirClipAreFound() {
        Track lane = new Track("lane", null, Track.Kind.MIDI, "organ", List.of(
                clip("c1", 0, 480, "piano", new Note("n1", 0, 960, 60, 90, "v2")),
                clip("c2", 480, 480, null)));
        List<String> found = CompositionValidator.validate(piece(List.of(lane))).stream()
                .map(CompositionValidator.Violation::toString).toList();
        assertEquals(List.of(
                "lane: unknown default part organ",
                "n1: ends after the end of clip c1",
                "n1: voice v2 is not a voice of part piano",
                "c2: unknown part organ"), found);
    }

    @Test
    public void sectionsFollowOneAnotherWithoutGap() {
        Composition gap = new Composition(Meta.EMPTY, TimeMap.of(108), List.of(
                new Section("a", null, null, 0, 1920, null, null, null),
                new Section("b", null, null, 3840, 1920, null, null, null)), List.of(), List.of(), List.of());
        assertEquals("b: leaves a gap after the section before",
                CompositionValidator.validate(gap).get(0).toString());
    }
}
