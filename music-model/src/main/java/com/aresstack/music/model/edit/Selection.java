package com.aresstack.music.model.edit;

import com.aresstack.music.model.Clip;
import com.aresstack.music.model.Composition;
import com.aresstack.music.model.Composition.PlacedNote;
import com.aresstack.music.model.MidiClip;
import com.aresstack.music.model.Track;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A stretch of a piece the user marked in the arrangement: some parts (or single clips) times a
 * range of ticks (architecture review E4, {@code REPLACE_REGION}/{@code VARIATE_REGION}). It says
 * which notes a region change is about and gives the {@link EditScope} that holds the change to
 * exactly them.
 *
 * <p>A note is selected if it <em>begins</em> within the range (architecture review E5, rule of
 * the border) and its part is selected or its clip is. A note that begins before the range and
 * sounds into it is not selected and stays as it is.
 *
 * @param partIds  the selected parts: all their notes in the range
 * @param clipIds  the selected clips: their notes in the range, whatever part they play
 * @param fromTick the first tick of the range
 * @param toTick   the first tick after it
 */
public record Selection(Set<String> partIds, Set<String> clipIds, long fromTick, long toTick) {
    public Selection {
        partIds = Set.copyOf(new LinkedHashSet<>(partIds == null ? Set.of() : partIds));
        clipIds = Set.copyOf(new LinkedHashSet<>(clipIds == null ? Set.of() : clipIds));
        if (partIds.isEmpty() && clipIds.isEmpty()) {
            throw new IllegalArgumentException("Eine Auswahl braucht mindestens einen Part oder Clip.");
        }
        if (fromTick < 0 || toTick <= fromTick) {
            throw new IllegalArgumentException("Eine Auswahl umfasst mindestens einen Tick: " + fromTick + ".." + toTick);
        }
    }

    /**
     * Parts times bars, as the user counts them: bar 1 is the first bar of the piece, both ends
     * included. "Flute, bars 5 to 8" is {@code bars(piece, List.of(flute), 5, 8)}.
     *
     * @throws IllegalArgumentException if a part is unknown or the bars are no range
     */
    public static Selection bars(Composition piece, Collection<String> partIds, int firstBar, int lastBar) {
        if (firstBar < 1 || lastBar < firstBar) {
            throw new IllegalArgumentException("Takte " + firstBar + "-" + lastBar + " sind kein Bereich.");
        }
        for (String id : partIds) {
            if (piece.part(id) == null) {
                throw new IllegalArgumentException("Unbekannter Part: " + id);
            }
        }
        return new Selection(new LinkedHashSet<>(partIds), Set.of(), piece.timeMap().tickOfBar(firstBar - 1),
                piece.timeMap().tickOfBar(lastBar));
    }

    /**
     * Single clips, over the stretch from the start of the first to the end of the last.
     *
     * @throws IllegalArgumentException if a clip is unknown or not a MIDI clip
     */
    public static Selection clips(Composition piece, Collection<String> clipIds) {
        long from = Long.MAX_VALUE;
        long to = Long.MIN_VALUE;
        for (String id : clipIds) {
            MidiClip clip = midiClip(piece, id);
            from = Math.min(from, clip.startTick());
            to = Math.max(to, clip.endTick());
        }
        if (clipIds.isEmpty()) {
            throw new IllegalArgumentException("Keine Clips gewählt.");
        }
        return new Selection(Set.of(), new LinkedHashSet<>(clipIds), from, to);
    }

    /** The same parts and clips over another range. */
    public Selection within(long from, long to) {
        return new Selection(partIds, clipIds, from, to);
    }

    /** Whether the user selected whole parts, so that a region change may give them new clips where they had none. */
    public boolean selectsPart(String partId) {
        return partIds.contains(partId);
    }

    /** Whether a clip on a track is selected, by itself or by its part. */
    public boolean selects(Track track, MidiClip clip) {
        return clipIds.contains(clip.id()) || partIds.contains(track.partOf(clip));
    }

    /** Whether a placed note is selected: its clip is, and it begins within the range. */
    public boolean selects(PlacedNote note) {
        return note.tick() >= fromTick && note.tick() < toTick && selects(note.track(), note.clip());
    }

    /**
     * The parts the selection is about, in the order of the piece: the selected parts and those
     * that play a selected clip.
     */
    public List<String> parts(Composition piece) {
        Set<String> found = new LinkedHashSet<>();
        for (var part : piece.parts()) {
            if (partIds.contains(part.id())) {
                found.add(part.id());
            }
        }
        for (Track track : piece.tracks()) {
            for (Clip clip : track.clips()) {
                if (clip instanceof MidiClip midi && clipIds.contains(midi.id())) {
                    found.add(track.partOf(midi));
                }
            }
        }
        List<String> ordered = new ArrayList<>();
        for (var part : piece.parts()) {
            if (found.contains(part.id())) {
                ordered.add(part.id());
            }
        }
        return ordered;
    }

    /** The selected notes of a part, in the order of the piece. */
    public List<PlacedNote> notes(Composition piece, String partId) {
        List<PlacedNote> selected = new ArrayList<>();
        for (PlacedNote note : piece.notesOf(partId)) {
            if (selects(note)) {
                selected.add(note);
            }
        }
        return selected;
    }

    /**
     * The scope of a change of the selected notes with these kinds of operation: the selected
     * parts and clips within the range, nothing else, no shift, no transition zone.
     */
    public EditScope scope(Set<EditOp.Kind> kinds) {
        return new EditScope(false, partIds, Set.of(), clipIds, fromTick, toTick, kinds, false, 0);
    }

    private static MidiClip midiClip(Composition piece, String id) {
        for (Track track : piece.tracks()) {
            for (Clip clip : track.clips()) {
                if (clip.id().equals(id)) {
                    if (clip instanceof MidiClip midi) {
                        return midi;
                    }
                    throw new IllegalArgumentException("Kein MIDI-Clip: " + id);
                }
            }
        }
        throw new IllegalArgumentException("Unbekannter Clip: " + id);
    }
}
