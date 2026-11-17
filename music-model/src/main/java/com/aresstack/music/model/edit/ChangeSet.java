package com.aresstack.music.model.edit;

import com.aresstack.music.model.Composition;
import com.aresstack.music.model.Part;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Which parts sound different after a change, so that only their stems need rendering again and
 * every other cached stem stays valid (targeted cache invalidation). A part sounds different if
 * its sound settings (role, instrument, program, sound library, MIDI channel) or one of its notes
 * in the piece (tick, length, pitch, velocity) changed; a renamed part, a note moved to another
 * voice or a note stored in another clip at the same place sounds the same. A change of tempo
 * changes the sound of every part.
 *
 * @param added    parts that are new
 * @param removed  parts that are gone
 * @param changed  parts that are still there and sound different
 * @param tempo    whether the tempo changed, which concerns every part
 * @param form     whether something changed that does not sound: sections, meter, key, words
 * @param allAfter every part of the changed piece
 */
public record ChangeSet(SortedSet<String> added, SortedSet<String> removed, SortedSet<String> changed,
                        boolean tempo, boolean form, SortedSet<String> allAfter) {
    public ChangeSet {
        added = Collections.unmodifiableSortedSet(new TreeSet<>(added));
        removed = Collections.unmodifiableSortedSet(new TreeSet<>(removed));
        changed = Collections.unmodifiableSortedSet(new TreeSet<>(changed));
        allAfter = Collections.unmodifiableSortedSet(new TreeSet<>(allAfter));
    }

    public static ChangeSet between(Composition before, Composition after) {
        SortedSet<String> added = new TreeSet<>();
        SortedSet<String> removed = new TreeSet<>();
        SortedSet<String> changed = new TreeSet<>();
        SortedSet<String> all = new TreeSet<>();
        for (Part part : after.parts()) {
            all.add(part.id());
            Part old = before.part(part.id());
            if (old == null) {
                added.add(part.id());
            } else if (!sound(before, old).equals(sound(after, part))) {
                changed.add(part.id());
            }
        }
        for (Part part : before.parts()) {
            if (after.part(part.id()) == null) {
                removed.add(part.id());
            }
        }
        boolean tempo = !before.timeMap().tempos().equals(after.timeMap().tempos());
        boolean form = !before.sections().equals(after.sections()) || !before.meta().equals(after.meta())
                || !before.dynamics().equals(after.dynamics())
                || !before.timeMap().meters().equals(after.timeMap().meters())
                || !before.timeMap().keys().equals(after.timeMap().keys());
        return new ChangeSet(added, removed, changed, tempo, form, all);
    }

    /** The parts whose stem must be rendered (again). */
    public SortedSet<String> partsToRender() {
        SortedSet<String> parts = new TreeSet<>(tempo ? allAfter : changed);
        parts.addAll(added);
        return Collections.unmodifiableSortedSet(parts);
    }

    /** The parts whose cached stem no longer fits the piece. */
    public SortedSet<String> staleStems() {
        SortedSet<String> parts = new TreeSet<>(tempo ? allAfter : changed);
        parts.addAll(removed);
        return Collections.unmodifiableSortedSet(parts);
    }

    /** Whether nothing changed at all, sounding or not. */
    public boolean isEmpty() {
        return added.isEmpty() && removed.isEmpty() && changed.isEmpty() && !tempo && !form;
    }

    private static String sound(Composition piece, Part part) {
        List<String> notes = new ArrayList<>();
        for (Composition.PlacedNote placed : piece.notesOf(part.id())) {
            // As the note sounds: a dynamic mark or an articulation changes the stem as a new velocity does.
            notes.add(placed.tick() + ":" + piece.playedLength(placed) + ":" + placed.note().pitch() + ":"
                    + piece.playedVelocity(placed));
        }
        Collections.sort(notes);
        return Objects.toString(part.role()) + "|" + part.instrument() + "|" + part.program() + "|" + part.libraryId()
                + "|" + part.legacyChannel() + "|" + notes;
    }
}
