package com.aresstack.music.model.edit;

import static com.aresstack.music.model.edit.Pieces.BAR;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.aresstack.music.model.Composition;
import com.aresstack.music.model.Part;
import com.aresstack.music.model.TimeMap;
import java.util.List;
import java.util.Set;
import org.junit.Test;

/** Targeted cache invalidation: only parts that sound different need a new stem. */
public final class ChangeSetTest {
    private final Composition piece = Pieces.eightBars();

    @Test
    public void aChangedNoteConcernsItsPartOnly() {
        Composition after = CompositionEditor.apply(piece, Patch.of("",
                new EditOp.AddNotes("flute-c", List.of(Pieces.note("x", BAR, 90, "flute")))),
                EditScope.part("flute")).result();
        ChangeSet changes = ChangeSet.between(piece, after);
        assertEquals(Set.of("flute"), changes.partsToRender());
        assertEquals(Set.of("flute"), changes.staleStems());
    }

    @Test
    public void aRenamedPartSoundsTheSame() {
        Part flute = piece.part("flute");
        Part renamed = new Part("flute", "Querflöte", flute.role(), flute.instrument(), flute.program(),
                flute.libraryId(), flute.staffLayout(), flute.voices(), flute.legacyChannel());
        Composition after = CompositionEditor.apply(piece, Patch.of("", new EditOp.SetPart(renamed)),
                EditScope.part("flute").allowing(EditOp.Kind.SET_PART)).result();
        ChangeSet changes = ChangeSet.between(piece, after);
        assertTrue(changes.partsToRender().isEmpty());
        assertTrue(changes.isEmpty());
    }

    @Test
    public void aNewTempoConcernsEveryPartAndARoleChangeItsPart() {
        Composition faster = new Composition(piece.meta(), TimeMap.of(120), piece.sections(), piece.parts(),
                piece.tracks(), piece.assets());
        assertEquals(Set.of("bass", "flute", "piano"), ChangeSet.between(piece, faster).partsToRender());
        Part bass = piece.part("bass");
        Part role = new Part("bass", bass.name(), "texture", bass.instrument(), bass.program(), bass.libraryId(),
                bass.staffLayout(), bass.voices(), bass.legacyChannel());
        Composition after = piece.withParts(List.of(piece.part("piano"), piece.part("flute"), role));
        assertEquals(Set.of("bass"), ChangeSet.between(piece, after).partsToRender());
    }
}
