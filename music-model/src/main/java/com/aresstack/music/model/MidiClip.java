package com.aresstack.music.model;

import java.util.List;

/**
 * Notes for a part. The clip end bounds the note events (a note ends at the latest there), not
 * the sound: what rings on past the end is the business of the renderer.
 *
 * @param partId the part that plays the clip, or {@code null} for the default part of its track
 * @param notes  the notes in the order they were written, ticks counted from the clip start
 */
public record MidiClip(String id, long startTick, long lengthTicks, String partId, List<Note> notes) implements Clip {
    public MidiClip {
        Ids.require(id, "a clip");
        if (startTick < 0 || lengthTicks < 1) {
            throw new IllegalArgumentException("A clip starts at 0 or later and lasts at least one tick: " + id);
        }
        if (partId != null) {
            Ids.require(partId, "the part of a clip");
        }
        notes = List.copyOf(notes == null ? List.of() : notes);
    }

    @Override
    public Track.Kind kind() {
        return Track.Kind.MIDI;
    }

    public long endTick() {
        return startTick + lengthTicks;
    }

    public MidiClip withNotes(List<Note> changed) {
        return new MidiClip(id, startTick, lengthTicks, partId, changed);
    }
}
