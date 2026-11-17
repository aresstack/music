package com.aresstack.music.notation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.aresstack.music.model.Composition;
import com.aresstack.music.model.Meta;
import com.aresstack.music.model.MidiClip;
import com.aresstack.music.model.Note;
import com.aresstack.music.model.Part;
import com.aresstack.music.model.Section;
import com.aresstack.music.model.TimeMap;
import com.aresstack.music.model.Track;
import com.aresstack.music.model.Voice;
import com.aresstack.music.notation.ai.AiNotesJson;
import com.aresstack.music.notation.context.ContextBuilder;
import com.aresstack.music.notation.context.ContextTiers;
import com.aresstack.music.notation.context.MusicalContext;
import com.aresstack.music.notation.json.CompositionJson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.List;
import org.junit.Test;

public final class NotationIntegrationTest {
    private static final long BAR = 1920;

    @Test
    public void aCompositionSurvivesAJsonRoundTrip() throws Exception {
        Composition piece = piece();
        JsonObject encoded = CompositionJson.write(piece);
        assertEquals("aresstack-composition", encoded.get("format").getAsString());
        assertEquals(1, encoded.get("version").getAsInt());
        Composition decoded = CompositionJson.read(encoded);
        assertEquals(encoded, CompositionJson.write(decoded));
        assertEquals(2, decoded.notesOf("melody").size());
    }

    @Test
    public void aCompactContextIsDeterministicAndDoesNotReplaceTheScore() {
        Composition piece = piece();
        MusicalContext first = ContextBuilder.build(piece, 0, 2 * BAR, 0, "melody");
        MusicalContext second = ContextBuilder.build(piece, 0, 2 * BAR, 0, "melody");
        assertEquals(first.text(), second.text());
        assertEquals(first.digest(), second.digest());
        assertTrue(first.text().contains("# PIECE"));
        assertTrue(first.text().contains("# FORM"));
        assertTrue(first.text().contains("# PARTS"));
        assertTrue(first.text().contains("# NOTES"));
        assertTrue(first.text().contains("C4/60"));

        ContextTiers tiers = ContextTiers.forContinuation(piece, 8 * BAR, 0, false);
        MusicalContext fromTiers = ContextBuilder.build(piece, tiers, "melody", 0);
        assertFalse(fromTiers.text().isEmpty());
    }

    @Test
    public void detailedNotesCanBeReadAndWrittenWithoutAChatClient() throws Exception {
        String answer = "{\"part\":\"melody\",\"section\":\"a\",\"notes\":["
                + "{\"bar\":1,\"beat\":0,\"dur\":1,\"midi\":60,\"vel\":80},"
                + "[2,0,1,64,92]]}";
        AiNotesJson.NoteBatch batch = AiNotesJson.read(answer, "melody", "a");
        assertEquals(2, batch.notes().size());
        assertEquals(60, batch.notes().get(0).pitch());
        assertEquals(64, batch.notes().get(1).pitch());
        assertEquals(batch, AiNotesJson.read(AiNotesJson.write(batch), "melody", "a"));
    }

    @Test(expected = IOException.class)
    public void notesFromAnotherPartAreRefused() throws Exception {
        AiNotesJson.read("{\"part\":\"bass\",\"section\":\"a\",\"notes\":[]}", "melody", "a");
    }

    @Test
    public void theFormatDoesNotImposeAnImplicit256NoteLimit() throws Exception {
        JsonObject raw = new JsonObject();
        raw.addProperty("part", "melody");
        raw.addProperty("section", "a");
        com.google.gson.JsonArray notes = new com.google.gson.JsonArray();
        for (int index = 0; index < 300; index++) {
            com.google.gson.JsonObject note = new com.google.gson.JsonObject();
            note.addProperty("bar", index + 1);
            note.addProperty("beat", 0);
            note.addProperty("duration", 1);
            note.addProperty("pitch", 60);
            notes.add(note);
        }
        raw.add("notes", notes);
        assertEquals(300, AiNotesJson.read(raw, "melody", "a").notes().size());
        try {
            AiNotesJson.read(raw, "melody", "a", 256);
            throw new AssertionError("The optional resource limit must be enforced.");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("256"));
        }
    }

    private static Composition piece() {
        Part flute = new Part("melody", "Flute", "melody", "Flute", 73, null, null,
                List.of(new Voice("v1", "Flute", null, null)), null);
        List<Note> notes = List.of(new Note("n1", 0, 480, 60, 80, "v1"),
                new Note("n2", BAR, 480, 64, 82, "v1"));
        MidiClip clip = new MidiClip("c1", 0, 8 * BAR, null, notes);
        Track track = new Track("t1", "Flute", Track.Kind.MIDI, "melody", List.of(clip));
        Section section = new Section("a", "A", null, 0, 8 * BAR, null, List.of("C"), null);
        return new Composition(new Meta("Example", null, null, null, null, null),
                TimeMap.of(100), List.of(section), List.of(flute), List.of(track), List.of());
    }
}
