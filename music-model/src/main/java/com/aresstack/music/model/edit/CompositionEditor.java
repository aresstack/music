package com.aresstack.music.model.edit;

import com.aresstack.music.model.Clip;
import com.aresstack.music.model.Composition;
import com.aresstack.music.model.CompositionValidator;
import com.aresstack.music.model.CompositionValidator.Violation;
import com.aresstack.music.model.MidiClip;
import com.aresstack.music.model.Note;
import com.aresstack.music.model.Part;
import com.aresstack.music.model.Track;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The one way to change a piece (architecture review 2.3): applies a {@link Patch} within an
 * {@link EditScope} and gives the changed piece or the reasons for refusing it. Manual edits and
 * edits by an AI pass the same way; the model does not know who made the patch.
 *
 * <p>Three checks, each on its own enough to refuse:
 * <ol>
 * <li><b>Each operation</b> is checked before it is applied: its kind must be allowed, its targets
 *     must be targets of the scope, and what it touches must lie in the scope's time.</li>
 * <li><b>The result</b> must not break a rule of {@link CompositionValidator} that the original kept.</li>
 * <li><b>The proof</b>: everything outside the scope is the same in the result as in the original
 *     with the patch's approved shifts applied ({@link ScopeProof}). A shift with
 *     {@link EditScope#allowsShift()} passes; the same shift and one more note changed outside
 *     does not.</li>
 * </ol>
 */
public final class CompositionEditor {
    private CompositionEditor() {
    }

    /** A breach of the scope or an operation that cannot be carried out. */
    private static final class Refused extends Exception {
        private final String objectId;

        Refused(String objectId, String message) {
            super(message, null, false, false);
            this.objectId = objectId;
        }
    }

    public static EditResult apply(Composition piece, Patch patch, EditScope scope) {
        Reach reach = new Reach(scope, piece);
        List<Violation> found = new ArrayList<>();
        Composition current = piece;
        Composition reference = piece;
        boolean beyondShifts = false;
        List<EditOp> operations = patch.operations();
        for (int i = 0; i < operations.size(); i++) {
            EditOp op = operations.get(i);
            String where = "operation " + (i + 1) + " (" + op.kind() + "): ";
            if (!scope.allows(op.kind())) {
                found.add(new Violation("patch", where + "not allowed in this scope"));
                continue;
            }
            if (op instanceof EditOp.ShiftTime shift) {
                if (beyondShifts) {
                    found.add(new Violation("patch", where + "shifts come before every other operation"));
                    continue;
                }
                try {
                    current = TimeShift.shift(current, shift.fromTick(), shift.ticks());
                    reference = TimeShift.shift(reference, shift.fromTick(), shift.ticks());
                } catch (IllegalArgumentException invalid) {
                    found.add(new Violation("patch", where + invalid.getMessage()));
                }
                continue;
            }
            beyondShifts = true;
            try {
                current = step(current, op, reach);
            } catch (Refused refused) {
                found.add(new Violation(refused.objectId, where + refused.getMessage()));
            } catch (IllegalArgumentException invalid) {
                found.add(new Violation("patch", where + invalid.getMessage()));
            }
        }
        if (!found.isEmpty()) {
            return new EditResult(null, found);
        }
        Set<String> before = new HashSet<>();
        for (Violation v : CompositionValidator.validate(piece)) {
            before.add(v.toString());
        }
        for (Violation v : CompositionValidator.validate(current)) {
            if (!before.contains(v.toString())) {
                found.add(v);
            }
        }
        for (String difference : ScopeProof.differences(reference, current, scope)) {
            found.add(new Violation("scope", "changed outside the scope: " + difference));
        }
        return found.isEmpty() ? new EditResult(current, List.of()) : new EditResult(null, found);
    }

    private static Composition step(Composition piece, EditOp op, Reach reach) throws Refused {
        EditScope scope = reach.scope();
        return switch (op) {
            case EditOp.AddPart add -> {
                if (piece.part(add.part().id()) != null) {
                    throw new Refused(add.part().id(), "a part of this id exists");
                }
                List<Part> parts = new ArrayList<>(piece.parts());
                parts.add(add.part());
                yield piece.withParts(parts);
            }
            case EditOp.SetPart set -> {
                Part old = piece.part(set.part().id());
                if (old == null) {
                    throw new Refused(set.part().id(), "no such part");
                }
                if (!reach.partInScope(old.id())) {
                    throw new Refused(old.id(), "the part is not a target of the scope");
                }
                List<Part> parts = new ArrayList<>(piece.parts());
                parts.set(parts.indexOf(old), set.part());
                yield piece.withParts(parts);
            }
            case EditOp.AddTrack add -> {
                if (piece.track(add.track().id()) != null) {
                    throw new Refused(add.track().id(), "a track of this id exists");
                }
                List<Track> tracks = new ArrayList<>(piece.tracks());
                tracks.add(add.track());
                yield piece.withTracks(tracks);
            }
            case EditOp.AddClip add -> {
                Track track = piece.track(add.trackId());
                if (track == null) {
                    throw new Refused(add.trackId(), "no such track");
                }
                if (TimeShift.clipIds(piece).contains(add.clip().id())) {
                    throw new Refused(add.clip().id(), "a clip of this id exists");
                }
                requireClipInScope(reach, track, add.clip());
                if (add.clip() instanceof MidiClip midi) {
                    for (Note note : midi.notes()) {
                        requireNoteInScope(reach, track, midi, note);
                    }
                }
                yield replaceTrack(piece, track, withClip(track.clips(), add.clip()));
            }
            case EditOp.RemoveClip remove -> {
                Located at = locate(piece, remove.clipId());
                requireClipInScope(reach, at.track, at.clip);
                List<Clip> clips = new ArrayList<>(at.track.clips());
                clips.remove(at.clip);
                yield replaceTrack(piece, at.track, clips);
            }
            case EditOp.MoveClip move -> {
                Located at = locate(piece, move.clipId());
                requireClipInScope(reach, at.track, at.clip);
                Track target = move.trackId() == null ? at.track : piece.track(move.trackId());
                if (target == null) {
                    throw new Refused(move.trackId(), "no such track");
                }
                Clip moved = TimeShift.moved(at.clip, move.startTick());
                requireClipInScope(reach, target, moved);
                List<Clip> clips = new ArrayList<>(at.track.clips());
                clips.remove(at.clip);
                Composition without = replaceTrack(piece, at.track, clips);
                Track landing = without.track(target.id());
                yield replaceTrack(without, landing, withClip(landing.clips(), moved));
            }
            case EditOp.ResizeClip resize -> {
                Located at = locate(piece, resize.clipId());
                MidiClip midi = midi(at);
                if (!reach.targetsClip(at.track, midi)) {
                    throw new Refused(midi.id(), "the clip is not a target of the scope");
                }
                long end = midi.startTick() + resize.lengthTicks();
                if (midi.endTick() < scope.editFrom()) {
                    throw new Refused(midi.id(), "the clip ends at tick " + midi.endTick() + ", before the time of the scope");
                }
                if (end < scope.editFrom()) {
                    throw new Refused(midi.id(), "the clip would end at tick " + end + ", before the time of the scope");
                }
                if (end > scope.toTick()) {
                    throw new Refused(midi.id(), "the clip would end at tick " + end + ", beyond the time of the scope");
                }
                yield replaceClip(piece, at,
                        new MidiClip(midi.id(), midi.startTick(), resize.lengthTicks(), midi.partId(), midi.notes()));
            }
            case EditOp.SplitClip split -> {
                Located at = locate(piece, split.clipId());
                MidiClip midi = midi(at);
                requireClipInScope(reach, at.track, midi);
                if (TimeShift.clipIds(piece).contains(split.newClipId())) {
                    throw new Refused(split.newClipId(), "a clip of this id exists");
                }
                MidiClip[] halves = TimeShift.split(midi, split.atTick(), split.newClipId());
                List<Clip> clips = new ArrayList<>(at.track.clips());
                int index = clips.indexOf(midi);
                clips.set(index, halves[0]);
                clips.add(index + 1, halves[1]);
                yield replaceTrack(piece, at.track, clips);
            }
            case EditOp.SetClipPart set -> {
                Located at = locate(piece, set.clipId());
                MidiClip midi = midi(at);
                requireClipInScope(reach, at.track, midi);
                if (set.partId() != null && piece.part(set.partId()) == null) {
                    throw new Refused(set.partId(), "no such part");
                }
                MidiClip changed = new MidiClip(midi.id(), midi.startTick(), midi.lengthTicks(), set.partId(), midi.notes());
                requireClipInScope(reach, at.track, changed);
                yield replaceClip(piece, at, changed);
            }
            case EditOp.AddNotes add -> {
                Located at = locate(piece, add.clipId());
                MidiClip midi = midi(at);
                for (Note note : add.notes()) {
                    requireNoteInScope(reach, at.track, midi, note);
                }
                List<Note> notes = new ArrayList<>(midi.notes());
                notes.addAll(add.notes());
                yield replaceClip(piece, at, midi.withNotes(notes));
            }
            case EditOp.RemoveNotes remove -> {
                Located at = locate(piece, remove.clipId());
                MidiClip midi = midi(at);
                List<Note> notes = new ArrayList<>(midi.notes());
                for (String id : remove.noteIds()) {
                    Note note = note(midi, id);
                    requireNoteBeginsInScope(reach, at.track, midi, note);
                    notes.remove(note);
                }
                yield replaceClip(piece, at, midi.withNotes(notes));
            }
            case EditOp.ChangeNote change -> {
                Located at = locate(piece, change.clipId());
                MidiClip midi = midi(at);
                Note old = note(midi, change.note().id());
                requireNoteBeginsInScope(reach, at.track, midi, old);
                requireNoteInScope(reach, at.track, midi, change.note());
                List<Note> notes = new ArrayList<>(midi.notes());
                notes.set(notes.indexOf(old), change.note());
                yield replaceClip(piece, at, midi.withNotes(notes));
            }
            case EditOp.AddAsset add -> {
                if (piece.asset(add.asset().id()) != null) {
                    throw new Refused(add.asset().id(), "an asset of this id exists");
                }
                List<com.aresstack.music.model.Asset> assets = new ArrayList<>(piece.assets());
                assets.add(add.asset());
                yield piece.withAssets(assets);
            }
            case EditOp.SetAudioClip set -> {
                Located at = locate(piece, set.clip().id());
                if (!(at.clip instanceof com.aresstack.music.model.AudioClip old)) {
                    throw new Refused(at.clip.id(), "not an audio clip");
                }
                requireClipInScope(reach, at.track, old);
                com.aresstack.music.model.AudioClip changed = set.clip();
                if (changed.startTick() != old.startTick()) {
                    throw new Refused(changed.id(), "an audio clip is moved with MOVE_CLIP");
                }
                requireClipInScope(reach, at.track, changed);
                yield replaceClip(piece, at, changed);
            }
            case EditOp.SetSections set ->
                    new Composition(piece.meta(), piece.timeMap(), set.sections(), piece.parts(), piece.tracks(), piece.assets(),
                            piece.dynamics());
            case EditOp.SetTimeMap set -> piece.withTimeMap(set.timeMap());
            case EditOp.SetDynamics set -> piece.withDynamics(set.dynamics());
            case EditOp.SetMeta set -> piece.withMeta(set.meta());
            case EditOp.ShiftTime shift -> TimeShift.shift(piece, shift.fromTick(), shift.ticks());
        };
    }

    private record Located(Track track, Clip clip) {
    }

    private static Located locate(Composition piece, String clipId) throws Refused {
        for (Track track : piece.tracks()) {
            for (Clip clip : track.clips()) {
                if (clip.id().equals(clipId)) {
                    return new Located(track, clip);
                }
            }
        }
        throw new Refused(clipId, "no such clip");
    }

    private static MidiClip midi(Located at) throws Refused {
        if (at.clip instanceof MidiClip midi) {
            return midi;
        }
        throw new Refused(at.clip.id(), "not a MIDI clip");
    }

    private static Note note(MidiClip clip, String noteId) throws Refused {
        for (Note note : clip.notes()) {
            if (note.id().equals(noteId)) {
                return note;
            }
        }
        throw new Refused(noteId, "no such note in clip " + clip.id());
    }

    private static void requireClipInScope(Reach reach, Track track, Clip clip) throws Refused {
        if (!reach.targetsClip(track, clip)) {
            throw new Refused(clip.id(), "the clip is not a target of the scope");
        }
        if (!reach.scope().covers(clip.startTick(), reach.end(clip))) {
            throw new Refused(clip.id(), "the clip reaches beyond the time of the scope");
        }
    }

    /**
     * A note that is there may be removed or changed if it begins in the scope, however long it
     * sounds: a note belongs to the stretch in which it begins (architecture review E5, rule of
     * the border), and the proof does not protect it either.
     */
    private static void requireNoteBeginsInScope(Reach reach, Track track, MidiClip clip, Note note) throws Refused {
        long tick = clip.startTick() + note.tick();
        if (!reach.targetsClip(track, clip)) {
            throw new Refused(note.id(), "the note's clip " + clip.id() + " is not a target of the scope");
        }
        if (!reach.scope().begins(tick)) {
            throw new Refused(note.id(), "the note begins at tick " + tick + ", outside the time of the scope");
        }
    }

    /** A note that comes in must begin in the scope and end before its end (or the transition zone's). */
    private static void requireNoteInScope(Reach reach, Track track, MidiClip clip, Note note) throws Refused {
        long tick = clip.startTick() + note.tick();
        requireNoteBeginsInScope(reach, track, clip, note);
        if (tick + note.length() > reach.scope().noteEndLimit()) {
            throw new Refused(note.id(), "the note sounds beyond the end of the scope");
        }
    }

    private static List<Clip> withClip(List<Clip> clips, Clip clip) {
        List<Clip> all = new ArrayList<>(clips);
        int at = all.size();
        while (at > 0 && all.get(at - 1).startTick() > clip.startTick()) {
            at--;
        }
        all.add(at, clip);
        return all;
    }

    private static Composition replaceTrack(Composition piece, Track track, List<Clip> clips) {
        List<Track> tracks = new ArrayList<>(piece.tracks());
        tracks.set(tracks.indexOf(track), track.withClips(clips));
        return piece.withTracks(tracks);
    }

    private static Composition replaceClip(Composition piece, Located at, Clip changed) {
        List<Clip> clips = new ArrayList<>(at.track.clips());
        clips.set(clips.indexOf(at.clip), changed);
        return replaceTrack(piece, at.track, clips);
    }
}
