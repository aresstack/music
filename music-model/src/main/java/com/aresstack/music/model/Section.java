package com.aresstack.music.model;

import java.util.List;

/**
 * A part of the form of a piece: it says what happens from one tick to another, it holds no
 * notes. The sections of a piece follow one another without gap.
 *
 * @param function    what the section does in the form, for example "intro" or "chorus"; may be {@code null}
 * @param intensity   how strong the section is, 0 to 1, or {@code null} if not said
 * @param harmony     the chords of the section, one per bar as far as known; may be empty
 * @param instruction what the section shall be, in words; may be {@code null}
 */
public record Section(String id, String name, String function, long startTick, long lengthTicks, Double intensity,
                      List<String> harmony, String instruction) {
    public Section {
        Ids.require(id, "a section");
        if (startTick < 0 || lengthTicks < 1) {
            throw new IllegalArgumentException("A section has a start from 0 and a length of at least one tick: " + id);
        }
        if (intensity != null && !(intensity >= 0 && intensity <= 1)) {
            throw new IllegalArgumentException("Intensity runs from 0 to 1: " + intensity);
        }
        harmony = harmony == null ? List.of() : List.copyOf(harmony);
    }

    public long endTick() {
        return startTick + lengthTicks;
    }
}
