package com.aresstack.music.notation.ai;

import com.aresstack.music.model.Articulation;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Read and write the detailed note format produced by a composing model.
 *
 * Keep the format independent of prompting, chat providers, retries, and file storage.
 * Address one part and one section per batch; specify resource limits at call sites.
 */
public final class AiNotesJson {

    private AiNotesJson() {
    }

    public record WrittenNote(double bar, double beat, double duration, int pitch, int velocity,
                              String voice, Articulation articulation) {
        public WrittenNote {
            if (!Double.isFinite(bar) || bar < 1 || !Double.isFinite(beat) || beat < 0
                    || !Double.isFinite(duration) || duration <= 0) {
                throw new IllegalArgumentException("Note position and duration must be finite and positive.");
            }
            if (pitch < 0 || pitch > 127 || velocity < 0 || velocity > 127) {
                throw new IllegalArgumentException("MIDI pitch and velocity must be in the range 0..127.");
            }
        }
    }

    public record NoteBatch(String partId, String sectionId, List<WrittenNote> notes) {
        public NoteBatch {
            if (partId == null || partId.isBlank() || sectionId == null || sectionId.isBlank()) {
                throw new IllegalArgumentException("Specify the target part and section.");
            }
            notes = List.copyOf(notes);
        }
    }

    public static NoteBatch read(String json, String expectedPart, String expectedSection) throws IOException {
        try {
            return read(JsonParser.parseString(json).getAsJsonObject(), expectedPart, expectedSection);
        } catch (RuntimeException invalid) {
            throw new IOException("Invalid note JSON: " + invalid.getMessage(), invalid);
        }
    }

    public static NoteBatch read(JsonObject json, String expectedPart, String expectedSection) throws IOException {
        return read(json, expectedPart, expectedSection, Integer.MAX_VALUE);
    }

    public static NoteBatch read(JsonObject json, String expectedPart, String expectedSection,
                                 int maxNotes) throws IOException {
        if (maxNotes < 0) {
            throw new IllegalArgumentException("The note limit cannot be negative.");
        }
        if (json == null) {
            throw new IOException("Missing note JSON object.");
        }
        String part = optional(json, "part");
        String section = optional(json, "section");
        if (part != null && expectedPart != null && !part.equals(expectedPart)) {
            throw new IOException("Refuse notes for a different part: " + part);
        }
        if (section != null && expectedSection != null && !section.equals(expectedSection)) {
            throw new IOException("Refuse notes for a different section: " + section);
        }
        String resolvedPart = part != null ? part : expectedPart;
        String resolvedSection = section != null ? section : expectedSection;
        JsonElement rawNotes = json.get("notes");
        if (rawNotes == null || !rawNotes.isJsonArray()) {
            throw new IOException("Missing notes array.");
        }
        JsonArray elements = rawNotes.getAsJsonArray();
        if (elements.size() > maxNotes) {
            throw new IOException("The note batch exceeds the configured limit of " + maxNotes + ".");
        }
        List<WrittenNote> notes = new ArrayList<>(elements.size());
        for (int index = 0; index < elements.size(); index++) {
            try {
                JsonObject note = normalized(elements.get(index));
                String notePart = optional(note, "part");
                if (notePart != null && resolvedPart != null && !notePart.equals(resolvedPart)) {
                    throw new IllegalArgumentException("A note belongs to a different part.");
                }
                notes.add(new WrittenNote(
                        numeric(note, "bar", 1),
                        numeric(note, "beat", Double.NaN, "start", "offset", "pos", "position"),
                        numeric(note, "duration", Double.NaN, "dur", "length", "len"),
                        (int) numeric(note, "pitch", Double.NaN, "note", "midi", "key"),
                        (int) numeric(note, "velocity", 80, "vel"),
                        optional(note, "voice"),
                        Articulation.parse(optional(note, "articulation"))));
            } catch (RuntimeException invalid) {
                throw new IOException("Invalid note at index " + index + ": " + invalid.getMessage(), invalid);
            }
        }
        try {
            return new NoteBatch(resolvedPart, resolvedSection, notes);
        } catch (RuntimeException invalid) {
            throw new IOException("Invalid target for note batch: " + invalid.getMessage(), invalid);
        }
    }

    public static JsonObject write(NoteBatch batch) {
        Objects.requireNonNull(batch, "batch");
        JsonObject json = new JsonObject();
        json.addProperty("part", batch.partId());
        json.addProperty("section", batch.sectionId());
        JsonArray written = new JsonArray();
        for (WrittenNote note : batch.notes()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("bar", note.bar());
            entry.addProperty("beat", note.beat());
            entry.addProperty("duration", note.duration());
            entry.addProperty("pitch", note.pitch());
            entry.addProperty("velocity", note.velocity());
            if (note.voice() != null) {
                entry.addProperty("voice", note.voice());
            }
            if (note.articulation() != null) {
                entry.addProperty("articulation", note.articulation().token());
            }
            written.add(entry);
        }
        json.add("notes", written);
        return json;
    }

    private static JsonObject normalized(JsonElement element) {
        if (element.isJsonObject()) {
            return element.getAsJsonObject();
        }
        if (!element.isJsonArray()) {
            throw new IllegalArgumentException("Expect a note object or compact note array.");
        }
        JsonArray row = element.getAsJsonArray();
        if (row.size() < 4) {
            throw new IllegalArgumentException("A compact note needs bar, beat, duration and pitch.");
        }
        String[] fields = {"bar", "beat", "duration", "pitch", "velocity", "voice", "articulation"};
        JsonObject note = new JsonObject();
        for (int index = 0; index < row.size() && index < fields.length; index++) {
            note.add(fields[index], row.get(index));
        }
        return note;
    }

    private static double numeric(JsonObject object, String name, double fallback, String... aliases) {
        JsonElement value = object.get(name);
        if (value == null) {
            for (String alias : aliases) {
                value = object.get(alias);
                if (value != null) {
                    break;
                }
            }
        }
        if (value == null || value.isJsonNull()) {
            if (Double.isNaN(fallback)) {
                throw new IllegalArgumentException("Missing required field: " + name);
            }
            return fallback;
        }
        double read = value.getAsDouble();
        if (!Double.isFinite(read)) {
            throw new IllegalArgumentException("Non-finite " + name);
        }
        if (("pitch".equals(name) || "velocity".equals(name)) && Math.rint(read) != read) {
            throw new IllegalArgumentException("Require an integer " + name);
        }
        return read;
    }

    private static String optional(JsonObject json, String key) {
        JsonElement value = json.get(key);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }
}
