package com.aresstack.music.model;

import java.util.Locale;

/**
 * How a single note is played, as a score marks it above or below the note head. The note keeps
 * its written length and velocity; what it sounds with says the {@link Dynamics.Articulations}
 * of the piece, which the user may change.
 */
public enum Articulation {
    /** Short, detached: a dot. */
    STACCATO("staccato", "Staccato"),
    /** Very short: a wedge. */
    STACCATISSIMO("staccatissimo", "Staccatissimo"),
    /** Held for its full value, slightly leaned on: a line. */
    TENUTO("tenuto", "Tenuto"),
    /** Struck harder: {@code >}. */
    ACCENT("accent", "Akzent"),
    /** Struck hard and short: {@code ^}. */
    MARCATO("marcato", "Marcato"),
    /** Bound to the next note without a gap: under a slur. */
    LEGATO("legato", "Legato");

    private final String token;
    private final String germanName;

    Articulation(String token, String germanName) {
        this.token = token;
        this.germanName = germanName;
    }

    /** As files and the AI write it: {@code "staccato"}. */
    public String token() {
        return token;
    }

    public String germanName() {
        return germanName;
    }

    /** The articulation of a word as a file or a model writes it, or {@code null} for none or an unknown word. */
    public static Articulation parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String wanted = text.trim().toLowerCase(Locale.ROOT);
        for (Articulation articulation : values()) {
            if (articulation.token.equals(wanted) || articulation.name().toLowerCase(Locale.ROOT).equals(wanted)) {
                return articulation;
            }
        }
        if (wanted.equals("accented") || wanted.equals("akzent")) {
            return ACCENT;
        }
        if (wanted.equals("slur") || wanted.equals("slurred")) {
            return LEGATO;
        }
        return null;
    }
}
