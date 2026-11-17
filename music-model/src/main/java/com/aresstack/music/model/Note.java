package com.aresstack.music.model;

/**
 * A note of a MIDI clip. Its tick counts from the start of its clip, so that moving a clip
 * moves its notes without touching them.
 *
 * @param voiceId      the voice of the clip's part the note belongs to
 * @param articulation how the note is played (staccato, accent ...), or {@code null} for plainly as written
 */
public record Note(String id, long tick, long length, int pitch, int velocity, String voiceId,
                   Articulation articulation) {
    public Note {
        Ids.require(id, "a note");
        if (tick < 0 || length < 1) {
            throw new IllegalArgumentException("A note starts at 0 or later and lasts at least one tick: " + id);
        }
        if (pitch < 0 || pitch > 127 || velocity < 1 || velocity > 127) {
            throw new IllegalArgumentException("Pitch runs from 0 to 127 and velocity from 1 to 127: " + id);
        }
        Ids.require(voiceId, "the voice of a note");
    }

    /** A note played plainly as written. */
    public Note(String id, long tick, long length, int pitch, int velocity, String voiceId) {
        this(id, tick, length, pitch, velocity, voiceId, null);
    }

    public Note withArticulation(Articulation changed) {
        return new Note(id, tick, length, pitch, velocity, voiceId, changed);
    }

    public long endTick() {
        return tick + length;
    }
}
