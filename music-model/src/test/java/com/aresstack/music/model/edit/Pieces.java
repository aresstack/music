package com.aresstack.music.model.edit;

import com.aresstack.music.model.Composition;
import com.aresstack.music.model.Meta;
import com.aresstack.music.model.MidiClip;
import com.aresstack.music.model.Note;
import com.aresstack.music.model.Part;
import com.aresstack.music.model.Role;
import com.aresstack.music.model.Section;
import com.aresstack.music.model.TimeMap;
import com.aresstack.music.model.Track;
import com.aresstack.music.model.Voice;
import java.util.ArrayList;
import java.util.List;

/** A small piece for the tests: three parts on three tracks, eight bars, one note per bar each. */
final class Pieces {
    static final long BAR = 1920;

    private Pieces() {
    }

    static Part part(String id, String role, int program) {
        return new Part(id, id, role, id, program, null, Part.StaffLayout.SINGLE,
                List.of(new Voice(id + "-v1", null, null, null)), null);
    }

    static Composition eightBars() {
        List<Part> parts = List.of(part("piano", Role.HARMONY, 0), part("flute", Role.MELODY, 73),
                part("bass", Role.BASS, 32));
        List<Track> tracks = new ArrayList<>();
        int pitch = 48;
        for (Part part : parts) {
            List<Note> notes = new ArrayList<>();
            for (int bar = 0; bar < 8; bar++) {
                notes.add(new Note(part.id() + "-n" + bar, bar * BAR, BAR / 2, pitch + bar, 90, part.id() + "-v1"));
            }
            tracks.add(new Track(part.id() + "-t", part.id(), Track.Kind.MIDI, part.id(),
                    List.of(new MidiClip(part.id() + "-c", 0, 8 * BAR, null, notes))));
            pitch += 12;
        }
        List<Section> sections = List.of(
                new Section("a", "A", "verse", 0, 4 * BAR, 0.5, List.of(), null),
                new Section("b", "B", "chorus", 4 * BAR, 4 * BAR, 0.8, List.of(), null));
        return new Composition(new Meta("Test", null, null, null, null, null), TimeMap.of(100), sections, parts, tracks,
                List.of());
    }

    static Note note(String id, long tick, int pitch, String part) {
        return new Note(id, tick, BAR / 4, pitch, 80, part + "-v1");
    }
}
