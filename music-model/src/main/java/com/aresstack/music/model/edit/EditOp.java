package com.aresstack.music.model.edit;

import com.aresstack.music.model.Asset;
import com.aresstack.music.model.AudioClip;
import com.aresstack.music.model.Clip;
import com.aresstack.music.model.Dynamics;
import com.aresstack.music.model.Meta;
import com.aresstack.music.model.Note;
import com.aresstack.music.model.Part;
import com.aresstack.music.model.Section;
import com.aresstack.music.model.TimeMap;
import com.aresstack.music.model.Track;
import java.util.List;

/**
 * One operation of a {@link Patch}. Every operation names its targets by id; nothing is found
 * by name, position or "the selected one". Ticks are ticks of the piece, not of a clip, except
 * for the ticks of notes, which as always count from the start of their clip.
 */
public sealed interface EditOp {
    /** The kinds of operation, as an {@link EditScope} allows them. */
    enum Kind {
        ADD_PART, SET_PART, ADD_TRACK, ADD_CLIP, REMOVE_CLIP, MOVE_CLIP, RESIZE_CLIP, SPLIT_CLIP, SET_CLIP_PART,
        ADD_NOTES, REMOVE_NOTES, CHANGE_NOTE, SET_SECTIONS, SET_TIME_MAP, SET_META, SHIFT_TIME, ADD_ASSET,
        SET_AUDIO_CLIP, SET_DYNAMICS
    }

    Kind kind();

    /** A new part. Its id must not be in use. */
    record AddPart(Part part) implements EditOp {
        public AddPart {
            require(part, "part");
        }

        @Override
        public Kind kind() {
            return Kind.ADD_PART;
        }
    }

    /** Replaces the part of the same id, for example to give it another instrument. */
    record SetPart(Part part) implements EditOp {
        public SetPart {
            require(part, "part");
        }

        @Override
        public Kind kind() {
            return Kind.SET_PART;
        }
    }

    /** A new, empty track at the end of the arrangement; its clips come with {@link AddClip}. */
    record AddTrack(Track track) implements EditOp {
        public AddTrack {
            require(track, "track");
            if (!track.clips().isEmpty()) {
                throw new IllegalArgumentException("A new track comes empty; its clips are added one by one: " + track.id());
            }
        }

        @Override
        public Kind kind() {
            return Kind.ADD_TRACK;
        }
    }

    /** A new clip on a track, with the notes it already holds. */
    record AddClip(String trackId, Clip clip) implements EditOp {
        public AddClip {
            require(trackId, "trackId");
            require(clip, "clip");
        }

        @Override
        public Kind kind() {
            return Kind.ADD_CLIP;
        }
    }

    /** Takes a clip away, with its notes. */
    record RemoveClip(String clipId) implements EditOp {
        public RemoveClip {
            require(clipId, "clipId");
        }

        @Override
        public Kind kind() {
            return Kind.REMOVE_CLIP;
        }
    }

    /**
     * Moves a clip to another start, and to another track if one is named.
     *
     * @param trackId the track the clip moves to, or {@code null} to stay on its track
     */
    record MoveClip(String clipId, String trackId, long startTick) implements EditOp {
        public MoveClip {
            require(clipId, "clipId");
            if (startTick < 0) {
                throw new IllegalArgumentException("A clip starts at tick 0 or later: " + startTick);
            }
        }

        @Override
        public Kind kind() {
            return Kind.MOVE_CLIP;
        }
    }

    /**
     * Gives a MIDI clip another length; its start, part and notes stay. The end it has and the end
     * it gets must both lie in the scope's time (from {@link EditScope#editFrom()} to
     * {@link EditScope#toTick()}): a clip of the original may grow into the scope, so that a note of
     * the transition can sound past its old end, but it cannot shrink out of it or grow beyond it.
     */
    record ResizeClip(String clipId, long lengthTicks) implements EditOp {
        public ResizeClip {
            require(clipId, "clipId");
            if (lengthTicks < 1) {
                throw new IllegalArgumentException("A clip is at least one tick long: " + lengthTicks);
            }
        }

        @Override
        public Kind kind() {
            return Kind.RESIZE_CLIP;
        }
    }

    /**
     * Cuts a MIDI clip in two at a tick of the piece. Notes that begin from there on go to the new
     * clip; a note that sounds across the cut ends at it.
     */
    record SplitClip(String clipId, long atTick, String newClipId) implements EditOp {
        public SplitClip {
            require(clipId, "clipId");
            require(newClipId, "newClipId");
        }

        @Override
        public Kind kind() {
            return Kind.SPLIT_CLIP;
        }
    }

    /** Lets another part play a MIDI clip; {@code null} gives it back to the default part of its track. */
    record SetClipPart(String clipId, String partId) implements EditOp {
        public SetClipPart {
            require(clipId, "clipId");
        }

        @Override
        public Kind kind() {
            return Kind.SET_CLIP_PART;
        }
    }

    /** Adds notes to a MIDI clip; their ticks count from the clip start. */
    record AddNotes(String clipId, List<Note> notes) implements EditOp {
        public AddNotes {
            require(clipId, "clipId");
            notes = List.copyOf(require(notes, "notes"));
        }

        @Override
        public Kind kind() {
            return Kind.ADD_NOTES;
        }
    }

    /** Takes notes out of a MIDI clip. */
    record RemoveNotes(String clipId, List<String> noteIds) implements EditOp {
        public RemoveNotes {
            require(clipId, "clipId");
            noteIds = List.copyOf(require(noteIds, "noteIds"));
        }

        @Override
        public Kind kind() {
            return Kind.REMOVE_NOTES;
        }
    }

    /** Replaces the note of the same id in a MIDI clip. */
    record ChangeNote(String clipId, Note note) implements EditOp {
        public ChangeNote {
            require(clipId, "clipId");
            require(note, "note");
        }

        @Override
        public Kind kind() {
            return Kind.CHANGE_NOTE;
        }
    }

    /** A new audio file the piece uses as source material (work package B3). Its id must not be in use. */
    record AddAsset(Asset asset) implements EditOp {
        public AddAsset {
            require(asset, "asset");
        }

        @Override
        public Kind kind() {
            return Kind.ADD_ASSET;
        }
    }

    /**
     * Replaces the audio clip of the same id where it lies: its gain, its fades, its length or its
     * asset (work package B3). Moving it is {@link MoveClip}.
     */
    record SetAudioClip(AudioClip clip) implements EditOp {
        public SetAudioClip {
            require(clip, "clip");
        }

        @Override
        public Kind kind() {
            return Kind.SET_AUDIO_CLIP;
        }
    }

    /** Replaces the form of the piece. */
    record SetSections(List<Section> sections) implements EditOp {
        public SetSections {
            sections = List.copyOf(require(sections, "sections"));
        }

        @Override
        public Kind kind() {
            return Kind.SET_SECTIONS;
        }
    }

    /** Replaces tempo, meter and key. */
    record SetTimeMap(TimeMap timeMap) implements EditOp {
        public SetTimeMap {
            require(timeMap, "timeMap");
        }

        @Override
        public Kind kind() {
            return Kind.SET_TIME_MAP;
        }
    }

    /** Replaces the dynamic marks, hairpins and the dynamic scale of the piece. */
    record SetDynamics(Dynamics dynamics) implements EditOp {
        public SetDynamics {
            require(dynamics, "dynamics");
        }

        @Override
        public Kind kind() {
            return Kind.SET_DYNAMICS;
        }
    }

    /** Replaces title, genre, mood and the other words about the piece. */
    record SetMeta(Meta meta) implements EditOp {
        public SetMeta {
            require(meta, "meta");
        }

        @Override
        public Kind kind() {
            return Kind.SET_META;
        }
    }

    /**
     * Makes room: everything from {@code fromTick} on moves later by {@code ticks} (architecture
     * review E5, approved shift for {@code INSERT}). It is deterministic and moves no note within
     * its clip; see {@link TimeShift} for what exactly moves. Only the application creates it, and
     * the scope must allow it ({@link EditScope#allowsShift()}).
     */
    record ShiftTime(long fromTick, long ticks) implements EditOp {
        public ShiftTime {
            if (fromTick < 0 || ticks < 1) {
                throw new IllegalArgumentException("A shift starts at tick 0 or later and makes room of at least one tick.");
            }
        }

        @Override
        public Kind kind() {
            return Kind.SHIFT_TIME;
        }
    }

    private static <T> T require(T value, String what) {
        if (value == null) {
            throw new IllegalArgumentException("An operation needs its " + what + ".");
        }
        return value;
    }
}
