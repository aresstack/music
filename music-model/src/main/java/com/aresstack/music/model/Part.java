package com.aresstack.music.model;

import java.util.List;

/**
 * A player on the stage: one sound, one staff (or a grand staff) in the score, one stem in the
 * renderer, one channel strip in the mix. Clips refer to their part by its id; tracks only
 * name a part as their default.
 *
 * @param role          one of {@link Role#ALL}, or {@code null} when none was given
 * @param instrument    the instrument in words, for display; may be {@code null}
 * @param program       the General MIDI program, 0 to 127
 * @param libraryId     the sound library the part is played with; "builtin" is the plain Java sound
 * @param legacyChannel the MIDI channel an imported piece wrote for this part where it differs
 *                      from the one the studio would choose; {@code null} for every other part.
 *                      MIDI channels are derived, never chosen; this keeps old pieces as they were.
 */
public record Part(String id, String name, String role, String instrument, int program, String libraryId,
                   StaffLayout staffLayout, List<Voice> voices, Integer legacyChannel) {
    public enum StaffLayout { SINGLE, GRAND }

    public Part {
        Ids.require(id, "a part");
        name = name == null || name.isBlank() ? id : name;
        if (program < 0 || program > 127) {
            throw new IllegalArgumentException("A General MIDI program runs from 0 to 127: " + program);
        }
        libraryId = libraryId == null || libraryId.isBlank() ? "builtin" : libraryId;
        staffLayout = staffLayout == null ? StaffLayout.SINGLE : staffLayout;
        voices = List.copyOf(voices == null ? List.of() : voices);
        if (voices.isEmpty()) {
            throw new IllegalArgumentException("A part has at least one voice: " + id);
        }
        if (legacyChannel != null && (legacyChannel < 0 || legacyChannel > 15)) {
            throw new IllegalArgumentException("A MIDI channel runs from 0 to 15: " + legacyChannel);
        }
    }

    /** Whether the part plays drums: its notes are drum sounds, not pitches. */
    public boolean drums() {
        return Role.DRUMS.equals(role);
    }

    public Voice voice(String voiceId) {
        for (Voice voice : voices) {
            if (voice.id().equals(voiceId)) {
                return voice;
            }
        }
        return null;
    }

    /** The voice a note without a voice of its own belongs to. */
    public Voice firstVoice() {
        return voices.get(0);
    }
}
