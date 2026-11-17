package com.aresstack.music.model.edit;

import static com.aresstack.music.model.edit.Pieces.BAR;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.aresstack.music.model.Composition;
import com.aresstack.music.model.Composition.PlacedNote;
import com.aresstack.music.model.Note;
import java.util.List;
import java.util.Random;
import org.junit.Test;

/** Work package F3, acceptance 7.2: undo and redo over 100 random patches. */
public final class EditHistoryTest {
    private static final List<String> PARTS = List.of("piano", "flute", "bass");

    @Test
    public void undoingAHundredRandomPatchesGivesTheStartAndRedoingThemTheEnd() {
        Composition start = Pieces.eightBars();
        EditHistory<Composition> history = new EditHistory<>(start);
        Random random = new Random(20261115);
        int applied = 0;
        int counter = 0;
        while (applied < 100) {
            Composition now = history.current();
            String part = PARTS.get(random.nextInt(PARTS.size()));
            String clip = part + "-c";
            List<PlacedNote> notes = now.notesOf(part);
            EditOp op = switch (random.nextInt(3)) {
                case 0 -> new EditOp.AddNotes(clip, List.of(new Note("r" + counter++, random.nextInt(8) * BAR
                        + random.nextInt(4) * 480L, 240, 40 + random.nextInt(50), 1 + random.nextInt(127), part + "-v1")));
                case 1 -> notes.isEmpty() ? null : new EditOp.RemoveNotes(clip,
                        List.of(notes.get(random.nextInt(notes.size())).note().id()));
                default -> {
                    if (notes.isEmpty()) {
                        yield null;
                    }
                    Note n = notes.get(random.nextInt(notes.size())).note();
                    yield new EditOp.ChangeNote(clip, new Note(n.id(), n.tick(), n.length(), 30 + random.nextInt(60),
                            n.velocity(), n.voiceId()));
                }
            };
            if (op == null) {
                continue;
            }
            EditResult result = CompositionEditor.apply(now, Patch.of("Schritt " + applied, op), EditScope.part(part));
            assertTrue(String.valueOf(result.violations()), result.applied());
            if (result.result().equals(now)) {
                continue; // a change to the pitch it had: no step
            }
            history.push(result.result(), "Schritt " + applied);
            applied++;
        }
        Composition end = history.current();
        assertNotEquals(start, end);
        for (int i = 0; i < 100; i++) {
            history.undo();
        }
        assertFalse(history.canUndo());
        assertEquals(start, history.current());
        for (int i = 0; i < 100; i++) {
            history.redo();
        }
        assertFalse(history.canRedo());
        assertEquals(end, history.current());
    }

    @Test
    public void aNewStepAfterAnUndoForgetsTheRedo() {
        Composition start = Pieces.eightBars();
        EditHistory<Composition> history = new EditHistory<>(start);
        Composition a = start.withMeta(start.meta().withTitle("A"));
        Composition b = start.withMeta(start.meta().withTitle("B"));
        history.push(a, "A");
        assertEquals("A", history.undoDescription());
        history.undo();
        assertEquals("A", history.redoDescription());
        history.push(b, "B");
        assertFalse(history.canRedo());
        assertEquals(b, history.current());
        assertEquals(start, history.undo());
    }

    @Test
    public void anAmendedStateIsWhereAnUndoComesBackToAndMakesNoStepOfItsOwn() {
        EditHistory<String> history = new EditHistory<>("start");
        history.push("stage as the model knew it", "Bühne");
        history.amend("stage as the user saw it");
        assertEquals("stage as the user saw it", history.current());
        assertEquals("the step keeps its name", "Bühne", history.undoDescription());
        history.push("proposal taken", "Übernommen");
        assertEquals("stage as the user saw it", history.undo());
        assertEquals("start", history.undo());
        assertFalse(history.canUndo());
        history.redo();
        history.amend("corrected");
        assertFalse("a redo made from the old state is forgotten", history.canRedo());
    }
}
