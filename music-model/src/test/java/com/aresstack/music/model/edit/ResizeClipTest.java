package com.aresstack.music.model.edit;

import static com.aresstack.music.model.edit.Pieces.BAR;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.aresstack.music.model.Composition;
import com.aresstack.music.model.CompositionValidator;
import com.aresstack.music.model.MidiClip;
import com.aresstack.music.model.Note;
import java.util.List;
import java.util.Set;
import org.junit.Test;

/**
 * A clip of the original grows into the scope, so that a note of the transition can sound past
 * its old end; it cannot shrink out of the scope, grow beyond it, or change outside its targets.
 */
public final class ResizeClipTest {
    private final Composition piece = Pieces.eightBars();
    /** "Continue the flute after bar 8, revising bars 7 and 8": the scope of a continuation. */
    private final EditScope continuation = EditScope.parts(Set.of("flute"), 8 * BAR, EditScope.END)
            .withTransition(2 * BAR).allowing(EditOp.Kind.RESIZE_CLIP);

    private static EditResult apply(Composition piece, EditScope scope, EditOp... ops) {
        return CompositionEditor.apply(piece, Patch.of("test", ops), scope);
    }

    private static void assertRefused(EditResult result, String expected) {
        assertFalse("refused", result.applied());
        assertTrue(result.violations().toString(), result.violations().toString().contains(expected));
    }

    private static MidiClip clip(Composition piece, String id) {
        return (MidiClip) piece.tracks().stream().flatMap(t -> t.clips().stream()).filter(c -> c.id().equals(id))
                .findFirst().orElseThrow();
    }

    @Test
    public void aClipGrowsSoThatATransitionNoteRingsPastItsOldEnd() {
        Note ringing = new Note("ring", 7 * BAR + BAR / 2, BAR, 77, 80, "flute-v1"); // into bar 9
        String outside = ScopeProof.hashOutside(piece, continuation);

        EditResult result = apply(piece, continuation, new EditOp.ResizeClip("flute-c", 8 * BAR + BAR / 2),
                new EditOp.AddNotes("flute-c", List.of(ringing)));

        assertTrue(String.valueOf(result.violations()), result.applied());
        Composition after = result.result();
        assertEquals(8 * BAR + BAR / 2, clip(after, "flute-c").endTick());
        assertEquals(List.of(), CompositionValidator.validate(after));
        assertEquals(outside, ScopeProof.hashOutside(after, continuation, piece));
        assertEquals(piece.notesOf("piano"), after.notesOf("piano"));
    }

    @Test
    public void withoutTheClipTheNoteCannotSoundPastItsEnd() {
        Note ringing = new Note("ring", 7 * BAR + BAR / 2, BAR, 77, 80, "flute-v1");
        assertRefused(apply(piece, continuation, new EditOp.AddNotes("flute-c", List.of(ringing))), "ring");
    }

    @Test
    public void resizingNeedsItsPermission() {
        EditScope plain = EditScope.parts(Set.of("flute"), 8 * BAR, EditScope.END).withTransition(2 * BAR);
        assertRefused(apply(piece, plain, new EditOp.ResizeClip("flute-c", 9 * BAR)), "not allowed in this scope");
    }

    @Test
    public void aClipOfAnotherPartIsRefused() {
        assertRefused(apply(piece, continuation, new EditOp.ResizeClip("piano-c", 9 * BAR)), "not a target of the scope");
    }

    @Test
    public void aClipCannotShrinkOutOfTheScope() {
        // the scope begins at bar 7 (tick 6 bars), the clip would then end in bar 6
        assertRefused(apply(piece, continuation, new EditOp.ResizeClip("flute-c", 5 * BAR)), "before the time of the scope");
    }

    @Test
    public void aClipCannotGrowBeyondTheScope() {
        EditScope region = EditScope.parts(Set.of("flute"), 4 * BAR, 8 * BAR).allowing(EditOp.Kind.RESIZE_CLIP);
        assertRefused(apply(piece, region, new EditOp.ResizeClip("flute-c", 9 * BAR)), "beyond the time of the scope");
    }

    @Test
    public void aClipThatEndsBeforeTheScopeCannotReachIntoIt() {
        EditScope later = EditScope.parts(Set.of("flute"), 10 * BAR, EditScope.END).withTransition(BAR)
                .allowing(EditOp.Kind.RESIZE_CLIP);
        assertRefused(apply(piece, later, new EditOp.ResizeClip("flute-c", 11 * BAR)), "ends at tick " + 8 * BAR);
    }

    @Test
    public void shrinkingPastANoteOfTheClipBreaksTheModelAndIsRefused() {
        // flute-n7 begins at bar 8 and lasts half a bar; the clip may not end before it does
        assertRefused(apply(piece, continuation, new EditOp.ResizeClip("flute-c", 7 * BAR + BAR / 4)), "flute-n7");
    }

    @Test
    public void theProofSeesALengthChangeWhereResizingIsNotAllowed() {
        EditScope plain = EditScope.parts(Set.of("flute"), 8 * BAR, EditScope.END).withTransition(2 * BAR);
        Composition grown = CompositionEditor.apply(piece, Patch.of("grow", new EditOp.ResizeClip("flute-c", 9 * BAR)),
                continuation).result();
        assertEquals(1, ScopeProof.differences(piece, grown, plain).size());
        assertEquals(List.of(), ScopeProof.differences(piece, grown, continuation));
    }
}
