package com.aresstack.music.model.edit;

import static com.aresstack.music.model.edit.Pieces.BAR;
import static com.aresstack.music.model.edit.Pieces.note;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.aresstack.music.model.Composition;
import com.aresstack.music.model.CompositionValidator;
import com.aresstack.music.model.Meta;
import com.aresstack.music.model.MidiClip;
import com.aresstack.music.model.Note;
import com.aresstack.music.model.Section;
import com.aresstack.music.model.TimeMap;
import com.aresstack.music.model.Track;
import java.util.List;
import java.util.Set;
import org.junit.Test;

/**
 * Work package F3, acceptance 7.2: every operation inside and outside its scope, the proof over
 * everything outside, and the approved shift for INSERT.
 */
public final class CompositionEditorTest {
    private final Composition piece = Pieces.eightBars();
    /** The flute in bars 5 to 8 (ticks 4 to 8 bars): the scope of "replace this region". */
    private final EditScope fluteBack = EditScope.parts(Set.of("flute"), 4 * BAR, 8 * BAR);

    private static EditResult apply(Composition piece, EditScope scope, EditOp... ops) {
        return CompositionEditor.apply(piece, Patch.of("test", ops), scope);
    }

    private static void assertApplied(EditResult result) {
        assertTrue(String.valueOf(result.violations()), result.applied());
    }

    private static void assertRefused(EditResult result, String expected) {
        assertFalse("refused", result.applied());
        assertTrue(result.violations().toString(), result.violations().toString().contains(expected));
    }

    @Test
    public void replacingARegionOfOnePartLeavesEverythingElseAsItWas() {
        String outside = ScopeProof.hashOutside(piece, fluteBack);
        EditResult result = apply(piece, fluteBack,
                new EditOp.RemoveNotes("flute-c", List.of("flute-n4", "flute-n5", "flute-n6", "flute-n7")),
                new EditOp.AddNotes("flute-c", List.of(note("new1", 4 * BAR, 80, "flute"), note("new2", 7 * BAR, 81, "flute"))));
        assertApplied(result);
        assertEquals(outside, ScopeProof.hashOutside(result.result(), fluteBack));
        assertNotEquals(piece, result.result());
        assertEquals(piece.notesOf("piano"), result.result().notesOf("piano"));
        assertEquals(6, result.result().notesOf("flute").size());
    }

    @Test
    public void aNoteOfAnotherPartIsRefused() {
        assertRefused(apply(piece, fluteBack, new EditOp.RemoveNotes("piano-c", List.of("piano-n5"))),
                "not a target of the scope");
        assertRefused(apply(piece, fluteBack, new EditOp.AddNotes("bass-c", List.of(note("x", 5 * BAR, 40, "bass")))),
                "not a target of the scope");
    }

    @Test
    public void aNoteOutsideTheTimeIsRefused() {
        assertRefused(apply(piece, fluteBack, new EditOp.RemoveNotes("flute-c", List.of("flute-n3"))), "outside the time");
        assertRefused(apply(piece, fluteBack, new EditOp.AddNotes("flute-c", List.of(note("x", 3 * BAR, 70, "flute")))),
                "outside the time");
        Note moved = new Note("flute-n5", 2 * BAR, 480, 70, 90, "flute-v1");
        assertRefused(apply(piece, fluteBack, new EditOp.ChangeNote("flute-c", moved)), "outside the time");
        Note tooLong = new Note("x", 7 * BAR, 2 * BAR, 70, 90, "flute-v1");
        assertRefused(apply(piece, fluteBack.withTransition(0), new EditOp.AddNotes("flute-c", List.of(tooLong))),
                "beyond the end of the scope");
    }

    @Test
    public void changeNoteInsideIsApplied() {
        Note louder = new Note("flute-n5", 5 * BAR, 480, 70, 120, "flute-v1");
        EditResult result = apply(piece, fluteBack.allowing(EditOp.Kind.CHANGE_NOTE), new EditOp.ChangeNote("flute-c", louder));
        assertApplied(result);
        assertEquals(ScopeProof.hashOutside(piece, fluteBack), ScopeProof.hashOutside(result.result(), fluteBack));
    }

    @Test
    public void aKindTheScopeDoesNotAllowIsRefused() {
        assertRefused(apply(piece, fluteBack, new EditOp.SetMeta(Meta.EMPTY.withTitle("Neu"))), "not allowed in this scope");
        assertRefused(apply(piece, fluteBack, new EditOp.SetPart(Pieces.part("flute", null, 74))), "not allowed");
        assertRefused(apply(piece, fluteBack, new EditOp.ShiftTime(0, BAR)), "not allowed");
        assertRefused(apply(piece, EditScope.part("flute").allowing(EditOp.Kind.SHIFT_TIME), new EditOp.ShiftTime(0, BAR)),
                "not allowed"); // the kind alone is not enough: allowsShift is
    }

    @Test
    public void partsTracksAndWordsChangeOnlyWhereAllowed() {
        EditScope words = EditScope.part("flute").allowing(EditOp.Kind.SET_META, EditOp.Kind.SET_PART,
                EditOp.Kind.SET_SECTIONS, EditOp.Kind.SET_TIME_MAP);
        assertApplied(apply(piece, words, new EditOp.SetMeta(Meta.EMPTY.withTitle("Neu"))));
        assertApplied(apply(piece, words, new EditOp.SetPart(Pieces.part("flute", "melody", 74))));
        assertRefused(apply(piece, words, new EditOp.SetPart(Pieces.part("piano", "harmony", 1))), "not a target");
        assertApplied(apply(piece, words, new EditOp.SetTimeMap(TimeMap.of(120))));
        assertApplied(apply(piece, words, new EditOp.SetSections(List.of(
                new Section("all", "Alles", null, 0, 8 * BAR, null, List.of(), null)))));
        assertRefused(apply(piece, words, new EditOp.SetSections(List.of(
                new Section("all", "Alles", null, BAR, 8 * BAR, null, List.of(), null)))), "gap");
    }

    @Test
    public void aNewPartWithItsTrackAndClip() {
        EditScope add = new EditScope(false, Set.of(), Set.of(), Set.of(), 0, EditScope.END,
                Set.of(EditOp.Kind.ADD_PART, EditOp.Kind.ADD_TRACK, EditOp.Kind.ADD_CLIP), false, 0);
        String outside = ScopeProof.hashOutside(piece, add);
        EditResult result = apply(piece, add,
                new EditOp.AddPart(Pieces.part("cello", "bass", 42)),
                new EditOp.AddTrack(new Track("cello-t", "Cello", Track.Kind.MIDI, "cello", List.of())),
                new EditOp.AddClip("cello-t", new MidiClip("cello-c", 0, 8 * BAR, null,
                        List.of(note("c1", 0, 36, "cello")))));
        assertApplied(result);
        assertEquals(outside, ScopeProof.hashOutside(result.result(), add, piece));
        assertRefused(apply(piece, add, new EditOp.AddClip("piano-t", new MidiClip("p2", 8 * BAR, BAR, null, List.of()))),
                "not a target");
    }

    @Test
    public void clipOperationsInsideAndOutside() {
        EditScope bass = EditScope.part("bass").allowing(EditOp.Kind.SET_CLIP_PART);
        EditResult split = apply(piece, bass, new EditOp.SplitClip("bass-c", 4 * BAR, "bass-c2"));
        assertApplied(split);
        Composition two = split.result();
        assertEquals(8, two.notesOf("bass").size());
        assertApplied(apply(two, bass, new EditOp.MoveClip("bass-c2", null, 8 * BAR)));
        assertApplied(apply(two, bass, new EditOp.RemoveClip("bass-c2")));
        assertRefused(apply(two, bass, new EditOp.SetClipPart("bass-c2", "flute")), "not a target");
        assertRefused(apply(piece, bass, new EditOp.SplitClip("piano-c", 4 * BAR, "x")), "not a target");
        assertRefused(apply(piece, bass, new EditOp.RemoveClip("flute-c")), "not a target");
        assertRefused(apply(piece, bass, new EditOp.MoveClip("piano-c", null, BAR)), "not a target");
        // Into a track of another part: the clip would then be played by the piano, outside the scope.
        assertRefused(apply(two, bass, new EditOp.MoveClip("bass-c2", "piano-t", 9 * BAR)), "not a target");
        // A clip that reaches beyond the time of the scope cannot be removed as a whole.
        assertRefused(apply(piece, EditScope.parts(Set.of("bass"), 0, 4 * BAR), new EditOp.RemoveClip("bass-c")),
                "beyond the time");
        // Two clips may not overlap on a track: a rule of the validator.
        assertRefused(apply(piece, bass, new EditOp.AddClip("bass-t", new MidiClip("x", BAR, BAR, null, List.of()))),
                "overlaps");
        // Unknown ids.
        assertRefused(apply(piece, bass, new EditOp.RemoveClip("nope")), "no such clip");
    }

    @Test
    public void insertWithAnApprovedShiftPasses() {
        EditScope insert = EditScope.parts(Set.of("piano", "flute", "bass"), 4 * BAR, 6 * BAR)
                .allowingShift().allowing(EditOp.Kind.SET_SECTIONS);
        Patch patch = insertPatch();
        EditResult result = CompositionEditor.apply(piece, patch, insert);
        assertApplied(result);
        Composition after = result.result();
        assertEquals(10 * BAR, after.endTick());
        // Proof: the original with the shift applied equals the result outside the new room.
        Composition shifted = TimeShift.shift(piece, 4 * BAR, 2 * BAR);
        assertEquals(ScopeProof.hashOutside(shifted, insert), ScopeProof.hashOutside(after, insert));
        // The notes after the insertion point sound two bars later, unchanged otherwise.
        assertEquals(6 * BAR, tickOf(after, "piano", "piano-n4"));
        assertEquals(0, tickOf(after, "piano", "piano-n0"));
        assertEquals(List.of(), CompositionValidator.validate(after));
    }

    @Test
    public void theSameShiftWithOneMoreChangeOutsideFails() {
        EditScope insert = EditScope.parts(Set.of("piano", "flute", "bass"), 4 * BAR, 6 * BAR)
                .allowingShift().allowing(EditOp.Kind.SET_SECTIONS, EditOp.Kind.CHANGE_NOTE);
        Note changed = new Note("flute-n1", 1 * BAR, BAR / 2, 99, 90, "flute-v1"); // was pitch 61
        Patch patch = insertPatch().then(Patch.of("", new EditOp.ChangeNote("flute-c", changed)));
        assertRefused(CompositionEditor.apply(piece, patch, insert), "outside the time");
        // And the proof alone sees it, too, without the check of the operation.
        Composition shifted = TimeShift.shift(piece, 4 * BAR, 2 * BAR);
        Composition after = CompositionEditor.apply(piece, insertPatch(), insert).result();
        Composition tampered = CompositionEditor.apply(after, Patch.of("", new EditOp.ChangeNote("flute-c", changed)),
                EditScope.everything()).result();
        List<String> differences = ScopeProof.differences(shifted, tampered, insert);
        assertEquals(1, differences.size());
        assertTrue(differences.get(0), differences.get(0).startsWith("note:flute-n1 changed"));
    }

    @Test
    public void aShiftNeedsItsPermissionAndComesFirst() {
        EditScope noShift = EditScope.parts(Set.of("piano", "flute", "bass"), 4 * BAR, 6 * BAR)
                .allowing(EditOp.Kind.SET_SECTIONS);
        assertRefused(CompositionEditor.apply(piece, insertPatch(), noShift), "SHIFT_TIME): not allowed");
        EditScope late = EditScope.everything();
        assertRefused(CompositionEditor.apply(piece, Patch.of("", new EditOp.SetMeta(Meta.EMPTY),
                new EditOp.ShiftTime(BAR, BAR)), late), "shifts come before");
    }

    @Test
    public void aShiftWithinAClipCutsItDeterministically() {
        Composition once = TimeShift.shift(piece, 3 * BAR + 240, BAR);
        assertEquals(once, TimeShift.shift(piece, 3 * BAR + 240, BAR));
        Track piano = once.track("piano-t");
        assertEquals(2, piano.clips().size());
        assertEquals("piano-c-2", piano.clips().get(1).id());
        // The note across the cut ends at it; the notes after it keep their place in the music, one bar later.
        assertEquals(240, ((MidiClip) piano.clips().get(0)).notes().get(3).length());
        assertEquals(5 * BAR, tickOf(once, "piano", "piano-n4"));
        assertEquals(List.of(), CompositionValidator.validate(once));
        assertEquals(4 * BAR + BAR, once.sections().get(0).lengthTicks()); // the section across the cut grows
    }

    @Test
    public void aManualEditSurvivesAnAiChangeOfAnotherPart() { // A10, technically
        Note handPlayed = new Note("piano-n2", 2 * BAR, 240, 99, 64, "piano-v1");
        Composition byHand = CompositionEditor.apply(piece,
                Patch.of("Hand", new EditOp.ChangeNote("piano-c", handPlayed)), EditScope.everything()).result();
        EditResult ai = CompositionEditor.apply(byHand, Patch.of("KI",
                new EditOp.RemoveNotes("flute-c", List.of("flute-n0", "flute-n1")),
                new EditOp.AddNotes("flute-c", List.of(note("ai1", 0, 84, "flute")))), EditScope.part("flute"));
        assertApplied(ai);
        assertEquals(byHand.notesOf("piano"), ai.result().notesOf("piano"));
        assertEquals(99, ai.result().notesOf("piano").get(2).note().pitch());
    }

    @Test
    public void aRefusedPatchNamesEveryReason() {
        EditResult result = apply(piece, fluteBack,
                new EditOp.RemoveNotes("piano-c", List.of("piano-n5")),
                new EditOp.SetMeta(Meta.EMPTY));
        assertEquals(2, result.violations().size());
        assertEquals(null, result.result());
    }

    private static Patch insertPatch() {
        return Patch.of("Zwei Takte einfügen",
                new EditOp.ShiftTime(4 * BAR, 2 * BAR),
                new EditOp.SetSections(List.of(
                        new Section("a", "A", "verse", 0, 4 * BAR, 0.5, List.of(), null),
                        new Section("x", "Brücke", "bridge", 4 * BAR, 2 * BAR, 0.6, List.of(), null),
                        new Section("b", "B", "chorus", 6 * BAR, 4 * BAR, 0.8, List.of(), null))),
                new EditOp.AddClip("piano-t", new MidiClip("piano-x", 4 * BAR, 2 * BAR, null,
                        List.of(note("px1", 0, 50, "piano"), note("px2", BAR, 52, "piano")))),
                new EditOp.AddClip("flute-t", new MidiClip("flute-x", 4 * BAR, 2 * BAR, null,
                        List.of(note("fx1", 0, 76, "flute")))));
    }

    private static long tickOf(Composition piece, String part, String noteId) {
        return piece.notesOf(part).stream().filter(n -> n.note().id().equals(noteId)).findFirst().orElseThrow().tick();
    }
}
