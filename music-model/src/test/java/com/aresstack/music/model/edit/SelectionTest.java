package com.aresstack.music.model.edit;

import static com.aresstack.music.model.edit.Pieces.BAR;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.aresstack.music.model.Composition;
import com.aresstack.music.model.MidiClip;
import com.aresstack.music.model.Note;
import com.aresstack.music.model.Track;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.Test;

/**
 * Work package C2: a selection of parts or clips times bars, the notes it means, and the scope
 * that holds a region change to them; a note that begins in the region may go even if it sounds
 * beyond it (architecture review E5, rule of the border).
 */
public final class SelectionTest {
    private final Composition piece = Pieces.eightBars();

    @Test
    public void fluteBarsFiveToEight() {
        Selection flute = Selection.bars(piece, List.of("flute"), 5, 8);
        assertEquals(4 * BAR, flute.fromTick());
        assertEquals(8 * BAR, flute.toTick());
        assertEquals(List.of("flute"), flute.parts(piece));
        assertEquals(List.of("flute-n4", "flute-n5", "flute-n6", "flute-n7"),
                flute.notes(piece, "flute").stream().map(n -> n.note().id()).toList());
        assertEquals(List.of(), flute.notes(piece, "piano"));
        EditScope scope = flute.scope(EditScope.NOTES_AND_CLIPS);
        assertEquals(Set.of("flute"), scope.partIds());
        assertEquals(4 * BAR, scope.fromTick());
        assertEquals(8 * BAR, scope.toTick());
        assertFalse(scope.allowsShift());
    }

    @Test
    public void aClipSelectionCoversItsClip() {
        Selection clip = Selection.clips(piece, List.of("bass-c"));
        assertEquals(0, clip.fromTick());
        assertEquals(8 * BAR, clip.toTick());
        assertEquals(List.of("bass"), clip.parts(piece));
        assertFalse(clip.selectsPart("bass"));
        assertEquals(8, clip.notes(piece, "bass").size());
    }

    @Test
    public void unknownThingsAreRefused() {
        for (Runnable wrong : List.<Runnable>of(() -> Selection.bars(piece, List.of("oboe"), 1, 2),
                () -> Selection.bars(piece, List.of("flute"), 3, 2), () -> Selection.bars(piece, List.of("flute"), 0, 2),
                () -> Selection.clips(piece, List.of("nope")), () -> new Selection(Set.of(), Set.of(), 0, 1))) {
            try {
                wrong.run();
                fail("refused");
            } catch (IllegalArgumentException expected) {
                // as it should be
            }
        }
    }

    @Test
    public void aNoteThatBeginsInTheRegionMayGoEvenIfItSoundsBeyond() {
        Composition held = withFluteNote(new Note("long", 7 * BAR + BAR / 2, BAR, 77, 90, "flute-v1"),
                new Note("before", 3 * BAR + BAR / 2, BAR, 76, 90, "flute-v1"));
        EditScope scope = Selection.bars(held, List.of("flute"), 5, 8).scope(EditScope.NOTES_AND_CLIPS);
        EditResult removed = CompositionEditor.apply(held, Patch.of("weg", new EditOp.RemoveNotes("flute-c",
                List.of("long"))), scope);
        assertTrue(String.valueOf(removed.violations()), removed.applied());
        // the note that begins before the region and sounds into it stays out of reach
        EditResult before = CompositionEditor.apply(held, Patch.of("weg", new EditOp.RemoveNotes("flute-c",
                List.of("before"))), scope);
        assertFalse(before.applied());
        // what comes in must still end within the region
        EditResult tooLong = CompositionEditor.apply(held, Patch.of("neu", new EditOp.AddNotes("flute-c",
                List.of(new Note("x", 7 * BAR + BAR / 2, BAR, 70, 90, "flute-v1")))), scope);
        assertFalse(tooLong.applied());
        Note shorter = new Note("long", 7 * BAR + BAR / 2, BAR / 2, 77, 90, "flute-v1");
        EditResult shortened = CompositionEditor.apply(held, Patch.of("kürzer", new EditOp.ChangeNote("flute-c",
                shorter)), scope.allowing(EditOp.Kind.CHANGE_NOTE));
        assertTrue(String.valueOf(shortened.violations()), shortened.applied());
    }

    private Composition withFluteNote(Note... more) {
        List<Track> tracks = new ArrayList<>();
        for (Track track : piece.tracks()) {
            if (!track.id().equals("flute-t")) {
                tracks.add(track);
                continue;
            }
            MidiClip clip = (MidiClip) track.clips().get(0);
            List<Note> notes = new ArrayList<>(clip.notes());
            notes.addAll(List.of(more));
            MidiClip longer = new MidiClip(clip.id(), 0, 9 * BAR, null, notes);
            tracks.add(track.withClips(List.of(longer)));
        }
        return piece.withTracks(tracks);
    }
}
