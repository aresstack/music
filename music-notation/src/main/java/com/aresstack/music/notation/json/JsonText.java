package com.aresstack.music.notation.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

/**
 * JSON as the files of a piece are written: indented, but an object or array that holds only
 * plain values stays on one line, so that a note is one line and a diff of two versions of a
 * piece shows which notes changed. Files are written atomically.
 */
final class JsonText {
    private static final Gson PLAIN = new GsonBuilder().disableHtmlEscaping().create();

    private JsonText() {
    }

    static String write(JsonElement element) {
        StringBuilder text = new StringBuilder();
        write(element, text, "");
        return text.append('\n').toString();
    }

    static JsonObject read(Path file) throws IOException {
        try {
            return JsonParser.parseString(new String(Files.readAllBytes(file), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (RuntimeException invalid) {
            throw new IOException("Not a readable JSON object: " + file + " (" + invalid.getMessage() + ")", invalid);
        }
    }

    /** Writes the bytes to a file of a folder through a temporary file, so that it is whole or not there. */
    static void atomically(Path target, byte[] bytes) throws IOException {
        Path folder = target.toAbsolutePath().getParent();
        Files.createDirectories(folder);
        Path temporary = Files.createTempFile(folder, target.getFileName().toString(), ".tmp");
        try {
            Files.write(temporary, bytes);
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException notAtomic) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static void atomically(Path target, JsonElement element) throws IOException {
        atomically(target, write(element).getBytes(StandardCharsets.UTF_8));
    }

    private static boolean flat(JsonElement element) {
        if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                if (entry.getValue().isJsonObject() || entry.getValue().isJsonArray()) {
                    return false;
                }
            }
            return true;
        }
        if (element.isJsonArray()) {
            for (JsonElement item : element.getAsJsonArray()) {
                if (item.isJsonObject() || item.isJsonArray()) {
                    return false;
                }
            }
            return true;
        }
        return true;
    }

    private static void inline(JsonElement element, StringBuilder text) {
        if (element.isJsonObject()) {
            text.append('{');
            boolean first = true;
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                text.append(first ? "" : ", ").append(PLAIN.toJson(entry.getKey())).append(": ")
                        .append(PLAIN.toJson(entry.getValue()));
                first = false;
            }
            text.append('}');
        } else if (element.isJsonArray()) {
            text.append('[');
            JsonArray array = element.getAsJsonArray();
            for (int i = 0; i < array.size(); i++) {
                text.append(i == 0 ? "" : ", ").append(PLAIN.toJson(array.get(i)));
            }
            text.append(']');
        } else {
            text.append(PLAIN.toJson(element));
        }
    }

    private static void write(JsonElement element, StringBuilder text, String indent) {
        if (flat(element)) {
            inline(element, text);
            return;
        }
        String inner = indent + "  ";
        if (element.isJsonObject()) {
            text.append("{\n");
            boolean first = true;
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                text.append(first ? "" : ",\n").append(inner).append(PLAIN.toJson(entry.getKey())).append(": ");
                write(entry.getValue(), text, inner);
                first = false;
            }
            text.append('\n').append(indent).append('}');
        } else {
            JsonArray array = element.getAsJsonArray();
            text.append("[\n");
            for (int i = 0; i < array.size(); i++) {
                text.append(i == 0 ? "" : ",\n").append(inner);
                write(array.get(i), text, inner);
            }
            text.append('\n').append(indent).append(']');
        }
    }
}
