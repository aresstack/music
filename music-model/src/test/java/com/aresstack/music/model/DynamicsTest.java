package com.aresstack.music.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.aresstack.music.model.edit.ChangeSet;
import com.aresstack.music.model.edit.TimeShift;
import java.util.List;
import org.junit.Test;

/** Dynamic marks, hairpins and articulations: what a note sounds with. */
public final class DynamicsTest {
    private static final long BAR = 1920;
    private static final Voice V = new Voice("v1", null, null, Voice.Stem.AUTO);
    private static final Part PIANO = new Part("piano", null, Role.HARMONY, "Piano", 0, null,
            Part.StaffLayout.SINGLE, List.of(V), null);
    private static final Part BASS = new Part("bass", null, Role.BASS, "Bass", 32, null,
            Part.StaffLayout.SINGLE, List.of(new Voice("v2", null, null, Voice.Stem.AUTO)), null);
    private static final Dynamics.Scale SCALE = Dynamics.Scale.STANDARD;

    private static Composition piece(Dynamics dynamics, Note... pianoNotes) {
        Track piano = new Track("piano", null, Track.Kind.MIDI, "piano",
                List.of(new MidiClip("c1", 0, 4 * BAR, null, List.of(pianoNotes))));
        Track bass = new Track("bass", null, Track.Kind.MIDI, "bass",
                List.of(new MidiClip("c2", 0, 4 * BAR, null, List.of(new Note("b1", 0, 480, 36, 80, "v2")))));
        return new Composition(Meta.EMPTY, TimeMap.of(120), List.of(), List.of(PIANO, BASS), List.of(piano, bass),
                List.of(), dynamics);
    }

    @Test
    public void withoutMarksEveryNoteSoundsAsWritten() {
        assertEquals(77, Dynamics.NONE.velocity("piano", 0, 77));
        assertEquals(480, Dynamics.NONE.length(480, null));
        Composition plain = piece(Dynamics.NONE, new Note("n1", 0, 480, 60, 77, "v1"));
        assertEquals(77, plain.playedVelocity(plain.notesOf("piano").get(0)));
    }

    @Test
    public void aMarkScalesTheVelocityAgainstTheReferenceLevel() {
        Dynamics dynamics = new Dynamics(List.of(new Dynamics.Mark(0, Dynamics.Level.P, null),
                new Dynamics.Mark(2 * BAR, Dynamics.Level.FF, null)), List.of(), SCALE);
        int reference = SCALE.velocity(SCALE.reference());
        assertEquals(Math.round(80.0 * SCALE.velocity(Dynamics.Level.P) / reference), dynamics.velocity("piano", 0, 80));
        assertEquals(Math.round(80.0 * SCALE.velocity(Dynamics.Level.FF) / reference),
                dynamics.velocity("piano", 3 * BAR, 80));
        assertTrue(dynamics.velocity("piano", 0, 80) < 80);
        assertTrue(dynamics.velocity("piano", 3 * BAR, 80) > 80);
        assertEquals("never above 127", 127, dynamics.velocity("piano", 3 * BAR, 127));
    }

    @Test
    public void aHairpinLeadsEvenlyToItsTarget() {
        Dynamics dynamics = new Dynamics(List.of(new Dynamics.Mark(0, Dynamics.Level.P, null)),
                List.of(new Dynamics.Hairpin(BAR, 3 * BAR, Dynamics.Level.F, null)), SCALE);
        double p = SCALE.velocity(Dynamics.Level.P);
        double f = SCALE.velocity(Dynamics.Level.F);
        assertEquals(p, dynamics.levelVelocity("piano", BAR), 1e-9);
        assertEquals((p + f) / 2, dynamics.levelVelocity("piano", 2 * BAR), 1e-9);
        assertEquals(f, dynamics.levelVelocity("piano", 3 * BAR), 1e-9);
        assertEquals("held after its end", f, dynamics.levelVelocity("piano", 4 * BAR), 1e-9);
        assertTrue(dynamics.swells(dynamics.hairpins().get(0)));
        Dynamics fading = new Dynamics(List.of(new Dynamics.Mark(0, Dynamics.Level.FF, null)),
                List.of(new Dynamics.Hairpin(0, BAR, Dynamics.Level.PP, null)), SCALE);
        assertFalse(fading.swells(fading.hairpins().get(0)));
    }

    @Test
    public void withinAHairpinNotesStartAtItsPeakAndTheExpressionCarriesTheCourse() {
        Dynamics dynamics = new Dynamics(List.of(new Dynamics.Mark(0, Dynamics.Level.P, null)),
                List.of(new Dynamics.Hairpin(BAR, 3 * BAR, Dynamics.Level.F, null)), SCALE);
        double p = SCALE.velocity(Dynamics.Level.P);
        double f = SCALE.velocity(Dynamics.Level.F);
        int reference = SCALE.velocity(SCALE.reference());
        assertEquals(1.0, dynamics.expression("piano", 0), 1e-9);
        assertEquals(p / f, dynamics.expression("piano", BAR), 1e-9);
        assertEquals((p + f) / 2 / f, dynamics.expression("piano", 2 * BAR), 1e-9);
        assertEquals(1.0, dynamics.expression("piano", 3 * BAR), 1e-9);
        assertEquals(Math.round(64.0 * f / reference), dynamics.velocity("piano", BAR, 64));
        List<Dynamics.ExpressionPoint> curve = dynamics.expressionCurve("piano", TimeMap.GLIDE_STEP_TICKS);
        assertEquals(new Dynamics.ExpressionPoint(BAR, (int) Math.round(127 * p / f)), curve.get(0));
        assertEquals(new Dynamics.ExpressionPoint(3 * BAR, 127), curve.get(curve.size() - 1));
        assertTrue(Dynamics.NONE.expressionCurve("piano", TimeMap.GLIDE_STEP_TICKS).isEmpty());
    }

    @Test
    public void aMarkOfOnePartLeavesTheOthers() {
        Dynamics dynamics = new Dynamics(List.of(new Dynamics.Mark(0, Dynamics.Level.PPP, "bass")), List.of(), SCALE);
        assertEquals(80, dynamics.velocity("piano", 0, 80));
        assertTrue(dynamics.velocity("bass", 0, 80) < 40);
    }

    @Test
    public void articulationsShapeLengthAndVelocity() {
        Dynamics.Articulations a = Dynamics.Articulations.STANDARD;
        assertTrue(a.length(Articulation.STACCATO) < 1);
        assertTrue(a.length(Articulation.STACCATISSIMO) < a.length(Articulation.STACCATO));
        assertTrue(a.velocity(Articulation.ACCENT) > 1);
        assertTrue(a.length(Articulation.LEGATO) > 1);
        Composition piece = piece(Dynamics.NONE, new Note("n1", 0, 480, 60, 80, "v1", Articulation.STACCATO),
                new Note("n2", 480, 480, 62, 80, "v1", Articulation.ACCENT),
                new Note("n3", 960, 480, 64, 80, "v1", Articulation.LEGATO),
                new Note("n4", 1440, 480, 64, 80, "v1"));
        List<Composition.PlacedNote> notes = piece.notesOf("piano");
        assertEquals(Math.round(480 * a.length(Articulation.STACCATO)), piece.playedLength(notes.get(0)));
        assertEquals(80, piece.playedVelocity(notes.get(0)));
        assertEquals(480, piece.playedLength(notes.get(1)));
        assertEquals(Math.round(80 * a.velocity(Articulation.ACCENT)), piece.playedVelocity(notes.get(1)));
        assertEquals("a legato note never sounds into the next note of its pitch", 480,
                piece.playedLength(notes.get(2)));
        // Changed factors change the sound.
        Dynamics shorter = Dynamics.NONE.withArticulations(a.withLength(Articulation.STACCATO, 0.2));
        assertEquals(96, shorter.length(480, Articulation.STACCATO));
    }

    @Test
    public void aChangedMarkRendersOnlyThePartsItHoldsFor() {
        Composition before = piece(Dynamics.NONE, new Note("n1", 0, 480, 60, 80, "v1"));
        Composition after = before.withDynamics(new Dynamics(
                List.of(new Dynamics.Mark(0, Dynamics.Level.F, "piano")), List.of(), SCALE));
        ChangeSet changes = ChangeSet.between(before, after);
        assertEquals(java.util.Set.of("piano"), changes.partsToRender());
        assertFalse(changes.isEmpty());
        assertEquals(List.of(), CompositionValidator.validate(after));
        Composition unknown = before.withDynamics(new Dynamics(
                List.of(new Dynamics.Mark(0, Dynamics.Level.F, "flute")), List.of(), SCALE));
        assertEquals(1, CompositionValidator.validate(unknown).size());
    }

    @Test
    public void anInsertedPassageMovesTheMarksAfterIt() {
        Dynamics dynamics = new Dynamics(List.of(new Dynamics.Mark(0, Dynamics.Level.P, null),
                new Dynamics.Mark(2 * BAR, Dynamics.Level.F, null)),
                List.of(new Dynamics.Hairpin(BAR, 3 * BAR, Dynamics.Level.FF, null)), SCALE);
        Composition piece = piece(dynamics, new Note("n1", 0, 480, 60, 80, "v1"));
        Composition shifted = TimeShift.shift(piece, 2 * BAR, BAR);
        assertEquals(0, shifted.dynamics().marks().get(0).tick());
        assertEquals(3 * BAR, shifted.dynamics().marks().get(1).tick());
        assertEquals(BAR, shifted.dynamics().hairpins().get(0).startTick());
        assertEquals("a hairpin across the insertion grows", 4 * BAR, shifted.dynamics().hairpins().get(0).endTick());
        assertNotEquals(piece.dynamics(), shifted.dynamics());
    }

    @Test(expected = IllegalArgumentException.class)
    public void theLevelsGetLouderUpwards() {
        SCALE.with(Dynamics.Level.FF, 10);
    }
}
