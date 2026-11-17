package com.aresstack.music.model;

import java.util.List;

/**
 * A lane of the arrangement. It carries no sound of its own: a MIDI track only names the part
 * its clips are played by unless a clip names another one. A track is either MIDI or audio.
 *
 * @param defaultPartId the part of clips that name none; {@code null} on an audio track
 */
public record Track(String id, String name, Kind kind, String defaultPartId, List<Clip> clips) {
    public enum Kind { MIDI, AUDIO }

    public Track {
        Ids.require(id, "a track");
        name = name == null || name.isBlank() ? id : name;
        if (kind == null) {
            throw new IllegalArgumentException("A track is either MIDI or audio: " + id);
        }
        if (defaultPartId != null) {
            Ids.require(defaultPartId, "the default part of a track");
        }
        clips = List.copyOf(clips == null ? List.of() : clips);
    }

    /** The part a clip of this track is played by. */
    public String partOf(MidiClip clip) {
        return clip.partId() != null ? clip.partId() : defaultPartId;
    }

    public Track withClips(List<Clip> changed) {
        return new Track(id, name, kind, defaultPartId, changed);
    }
}
