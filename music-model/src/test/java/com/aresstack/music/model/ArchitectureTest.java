package com.aresstack.music.model;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.Test;

/**
 * Binding rule of the architecture (docs/architecture/2026-11-architekturpruefung.md, 2.1): the
 * model is independent of any AI generator and of files, windows and sound. Its sources import
 * nothing but the JDK's language and collection packages and the model itself, and name no
 * generator. The Gradle module has no dependency on any other module either (see its build file).
 */
public final class ArchitectureTest {
    private static final Path SOURCES = Paths.get("src", "main", "java");
    private static final Pattern IMPORT = Pattern.compile("^import\\s+(static\\s+)?([\\w.]+)", Pattern.MULTILINE);
    private static final List<String> ALLOWED = List.of(
            "java.util.", "java.security.SecureRandom", "java.security.MessageDigest",
            "java.security.NoSuchAlgorithmException", "java.nio.charset.StandardCharsets", "java.lang.",
            "com.aresstack.music.model.");
    private static final List<String> FORBIDDEN_WORDS = List.of("gemma", "kipitz", "openai", "studio.ai", "chatclient");

    @Test
    public void theModelImportsNothingButTheJdkAndItself() throws IOException {
        List<String> wrong = new ArrayList<>();
        for (Path file : sources()) {
            Matcher imports = IMPORT.matcher(read(file));
            while (imports.find()) {
                String imported = imports.group(2);
                if (ALLOWED.stream().noneMatch(imported::startsWith)) {
                    wrong.add(SOURCES.relativize(file) + " imports " + imported);
                }
            }
        }
        assertEquals("imports outside the model", List.of(), wrong);
    }

    @Test
    public void theModelNamesNoAiGenerator() throws IOException {
        List<String> wrong = new ArrayList<>();
        for (Path file : sources()) {
            String text = read(file).toLowerCase(Locale.ROOT);
            for (String word : FORBIDDEN_WORDS) {
                if (text.contains(word)) {
                    wrong.add(SOURCES.relativize(file) + " mentions " + word);
                }
            }
        }
        assertEquals("the model knows no generator", List.of(), wrong);
    }

    @Test
    public void theModuleHasNoProjectDependency() throws IOException {
        String build = read(Paths.get("build.gradle"));
        assertEquals("composition/build.gradle names no other module", false, build.contains("project("));
    }

    private static List<Path> sources() throws IOException {
        try (Stream<Path> files = Files.walk(SOURCES)) {
            List<Path> found = files.filter(file -> file.toString().endsWith(".java")).sorted().toList();
            if (found.size() < 10) {
                throw new AssertionError("Expected the sources of the model under " + SOURCES.toAbsolutePath());
            }
            return found;
        }
    }

    private static String read(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }
}
