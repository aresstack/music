package com.aresstack.music.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Checks what the records cannot check one by one: that ids are unique, that every reference
 * leads somewhere, and the rules of the first stage of the model (typed tracks, clips of a
 * track do not overlap, notes lie within their clip, sections follow one another without gap).
 * The rules are rules of this validator, not of the format: a file may hold what they forbid.
 */
public final class CompositionValidator {
    /** One thing that is wrong, with the id of the object it concerns. */
    public record Violation(String objectId, String message) {
        @Override
        public String toString() {
            return objectId + ": " + message;
        }
    }

    private CompositionValidator() {
    }

    /** Everything that is wrong, in a fixed order; empty if nothing is. */
    public static List<Violation> validate(Composition composition) {
        List<Violation> found = new ArrayList<>();
        unique(found, "section", composition.sections().stream().map(Section::id).toList());
        unique(found, "part", composition.parts().stream().map(Part::id).toList());
        unique(found, "track", composition.tracks().stream().map(Track::id).toList());
        unique(found, "asset", composition.assets().stream().map(Asset::id).toList());
        sections(found, composition.sections());
        dynamics(found, composition);
        Set<String> voices = new HashSet<>();
        for (Part part : composition.parts()) {
            Set<String> own = new HashSet<>();
            for (Voice voice : part.voices()) {
                if (!own.add(voice.id()) || !voices.add(voice.id())) {
                    found.add(new Violation(voice.id(), "voice id used twice"));
                }
                boolean grand = part.staffLayout() == Part.StaffLayout.GRAND;
                if (grand != (voice.staff() != null)) {
                    found.add(new Violation(voice.id(), grand ? "a voice on a grand staff names its system"
                            : "a voice on a single staff names no system"));
                }
            }
        }
        Set<String> clips = new HashSet<>();
        Set<String> notes = new HashSet<>();
        for (Track track : composition.tracks()) {
            if (track.kind() == Track.Kind.MIDI) {
                if (track.defaultPartId() != null && composition.part(track.defaultPartId()) == null) {
                    found.add(new Violation(track.id(), "unknown default part " + track.defaultPartId()));
                }
            } else if (track.defaultPartId() != null) {
                found.add(new Violation(track.id(), "an audio track has no part"));
            }
            List<Clip> ordered = new ArrayList<>(track.clips());
            ordered.sort(Comparator.comparingLong(Clip::startTick));
            long busyUntil = -1;
            String busyWith = null;
            for (Clip clip : ordered) {
                if (!clips.add(clip.id())) {
                    found.add(new Violation(clip.id(), "clip id used twice"));
                }
                if (clip.kind() != track.kind()) {
                    found.add(new Violation(clip.id(), "a " + clip.kind() + " clip on a " + track.kind() + " track"));
                }
                if (clip.startTick() < busyUntil) {
                    found.add(new Violation(clip.id(), "overlaps clip " + busyWith + " on track " + track.id()));
                }
                if (clip instanceof MidiClip midi) {
                    midi(found, composition, track, midi, notes);
                    if (midi.endTick() > busyUntil) {
                        busyUntil = midi.endTick();
                        busyWith = midi.id();
                    }
                } else if (clip instanceof AudioClip audio) {
                    boolean known = composition.assets().stream().anyMatch(asset -> asset.id().equals(audio.assetId()));
                    if (!known) {
                        found.add(new Violation(audio.id(), "unknown asset " + audio.assetId()));
                    }
                    // Its length is in samples, so where it ends in ticks depends on the tempo (B3).
                    long end = audio.endTick(composition.timeMap());
                    if (end > busyUntil) {
                        busyUntil = end;
                        busyWith = audio.id();
                    }
                }
            }
        }
        return found;
    }

    private static void midi(List<Violation> found, Composition composition, Track track, MidiClip clip, Set<String> notes) {
        String partId = track.partOf(clip);
        Part part = partId == null ? null : composition.part(partId);
        if (part == null) {
            found.add(new Violation(clip.id(), partId == null ? "no part plays the clip" : "unknown part " + partId));
        }
        for (Note note : clip.notes()) {
            if (!notes.add(note.id())) {
                found.add(new Violation(note.id(), "note id used twice"));
            }
            if (note.endTick() > clip.lengthTicks()) {
                found.add(new Violation(note.id(), "ends after the end of clip " + clip.id()));
            }
            if (part != null && part.voice(note.voiceId()) == null) {
                found.add(new Violation(note.id(), "voice " + note.voiceId() + " is not a voice of part " + part.id()));
            }
        }
    }

    private static void sections(List<Violation> found, List<Section> sections) {
        long expected = 0;
        for (Section section : sections) {
            if (section.startTick() != expected) {
                found.add(new Violation(section.id(), section.startTick() < expected ? "overlaps the section before"
                        : "leaves a gap after the section before"));
            }
            expected = section.endTick();
        }
    }

    /** Every mark and hairpin of a part names a part of the piece; a part's hairpins do not overlap. */
    private static void dynamics(List<Violation> found, Composition composition) {
        Dynamics dynamics = composition.dynamics();
        for (Dynamics.Mark mark : dynamics.marks()) {
            if (mark.partId() != null && composition.part(mark.partId()) == null) {
                found.add(new Violation(mark.partId(), "a dynamic mark at tick " + mark.tick() + " names an unknown part"));
            }
        }
        for (int i = 0; i < dynamics.hairpins().size(); i++) {
            Dynamics.Hairpin pin = dynamics.hairpins().get(i);
            if (pin.partId() != null && composition.part(pin.partId()) == null) {
                found.add(new Violation(pin.partId(), "a hairpin at tick " + pin.startTick() + " names an unknown part"));
            }
            for (int j = i + 1; j < dynamics.hairpins().size(); j++) {
                Dynamics.Hairpin other = dynamics.hairpins().get(j);
                if (java.util.Objects.equals(pin.partId(), other.partId()) && other.startTick() < pin.endTick()) {
                    found.add(new Violation(pin.partId() == null ? "dynamics" : pin.partId(),
                            "hairpins overlap at tick " + other.startTick()));
                }
            }
        }
    }

    private static void unique(List<Violation> found, String what, List<String> ids) {
        Set<String> seen = new HashSet<>();
        for (String id : ids) {
            if (!seen.add(id)) {
                found.add(new Violation(id, what + " id used twice"));
            }
        }
    }
}
