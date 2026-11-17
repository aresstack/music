package com.aresstack.music.model;

import java.util.Arrays;
import java.util.Locale;

/**
 * A chord symbol as a harmony plan writes it, for example {@code "Dm"}, {@code "A7"},
 * {@code "Bbmaj7"}, {@code "F#dim"}, {@code "Gsus4"}, {@code "C/E"}: a root and the pitch classes
 * it is made of. Only what a repair of notes needs: which pitch classes belong to the chord.
 *
 * @param symbol the symbol as written
 * @param root   the pitch class of the root, 0 = C
 * @param tones  the pitch classes of the chord, root first, without repetition
 */
public record Chord(String symbol, int root, int[] tones) {
    public Chord {
        tones = tones.clone();
    }

    @Override
    public int[] tones() {
        return tones.clone();
    }

    /** Whether a pitch (any octave) is one of the tones of the chord. */
    public boolean contains(int pitch) {
        int pc = Math.floorMod(pitch, 12);
        for (int tone : tones) {
            if (tone == pc) {
                return true;
            }
        }
        return false;
    }

    /**
     * Reads a chord symbol; root letters A to G, then {@code #} or {@code b}, then the kind:
     * nothing or {@code maj} (major), {@code m}/{@code min}/{@code -} (minor), {@code dim}/{@code °},
     * {@code aug}/{@code +}, {@code sus2}, {@code sus4}/{@code sus}, {@code 5}; then an optional
     * extension {@code 6}, {@code 69}, {@code 7}, {@code 9}, {@code 11}, {@code 13}, each also after
     * {@code maj} for a major seventh ({@code Cmaj9} = C E G B D); then added tones and alterations
     * ({@code add9}, {@code add4}, {@code b5}, {@code #5}, {@code b9}, {@code #9}, {@code #11},
     * {@code b13}); a bass after {@code /} is added as a tone. Anything else is refused, so that a
     * repair falls back to the scale instead of enforcing a wrong chord.
     *
     * @throws IllegalArgumentException if the text is no chord symbol
     */
    public static Chord parse(String symbol) {
        if (symbol == null || symbol.trim().isEmpty()) {
            throw new IllegalArgumentException("No chord given.");
        }
        String text = symbol.trim();
        String bass = null;
        int slash = text.indexOf('/');
        if (slash > 0) {
            bass = text.substring(slash + 1).trim();
            text = text.substring(0, slash).trim();
        }
        int[] parsed = root(text, symbol);
        int root = parsed[0];
        String kind = text.substring(parsed[1]).trim();
        String lower = kind.toLowerCase(Locale.ROOT);
        int third = 4;
        int fifth = 7;
        boolean noThird = false;
        boolean diminished = false;
        boolean halfDiminished = false;
        if (lower.startsWith("ø") || lower.startsWith("m7b5") || lower.startsWith("min7b5")) {
            third = 3;
            fifth = 6;
            halfDiminished = true;
            lower = lower.startsWith("ø") ? lower.substring(1) : lower.substring(lower.indexOf("b5") + 2);
            if (lower.isEmpty()) {
                lower = "7";
            }
        } else if (lower.startsWith("maj") || lower.startsWith("ma") || lower.startsWith("δ")) {
            // "maj" alone is the major triad; "maj7", "maj9", "Δ7" are read with the extension below
            if (lower.startsWith("δ")) {
                lower = "maj" + (lower.length() > 1 ? lower.substring(1) : "7");
            } else if (lower.startsWith("ma") && !lower.startsWith("maj")) {
                lower = "maj" + lower.substring(2);
            }
            if (!lower.matches("maj(7|9|11|13).*")) {
                lower = lower.substring(3);
            }
        } else if (lower.startsWith("min") || (kind.startsWith("m") && !kind.startsWith("maj")) || lower.startsWith("-")) {
            third = 3;
            lower = lower.startsWith("min") ? lower.substring(3) : lower.substring(1);
        } else if (lower.startsWith("dim") || lower.startsWith("°") || lower.startsWith("o")) {
            third = 3;
            fifth = 6;
            diminished = true;
            lower = lower.startsWith("dim") ? lower.substring(3) : lower.substring(1);
        } else if (lower.startsWith("aug") || lower.startsWith("+")) {
            fifth = 8;
            lower = lower.startsWith("aug") ? lower.substring(3) : lower.substring(1);
        }
        if (lower.startsWith("sus2")) {
            noThird = true;
            third = 2;
            lower = lower.substring(4);
        } else if (lower.startsWith("sus")) {
            noThird = true;
            third = 5;
            lower = lower.substring(lower.startsWith("sus4") ? 4 : 3);
        }
        java.util.List<Integer> added = new java.util.ArrayList<>();
        if (lower.equals("5")) {
            third = -1;
            lower = "";
        }
        // the extension: 6, 69, 7, 9, 11, 13, each optionally after "maj" (a major seventh)
        java.util.regex.Matcher extension = java.util.regex.Pattern.compile("^(maj)?(69|6|7|9|11|13)").matcher(lower);
        if (extension.find()) {
            boolean major = extension.group(1) != null;
            String degree = extension.group(2);
            lower = lower.substring(extension.end());
            if (degree.startsWith("6")) {
                if (major) {
                    throw new IllegalArgumentException("Not a chord: \"" + symbol + "\".");
                }
                added.add(9);
                if (degree.equals("69")) {
                    added.add(2);
                }
            } else {
                added.add(major ? 11 : diminished && !halfDiminished ? 9 : 10);
                if (!degree.equals("7")) {
                    added.add(2);
                }
                if (degree.equals("11")) {
                    added.add(5);
                }
                if (degree.equals("13")) {
                    added.add(9);
                }
            }
        }
        if (!noThird && third >= 0 && lower.startsWith("sus")) { // C7sus4, C9sus4
            noThird = true;
            third = lower.startsWith("sus2") ? 2 : 5;
            lower = lower.substring(lower.startsWith("sus2") || lower.startsWith("sus4") ? 4 : 3);
        }
        // added tones and alterations: add9, add2, add4, add11, b5, #5, b9, #9, #11, b13, also in parentheses
        java.util.regex.Matcher more = java.util.regex.Pattern.compile("^[(,\\s]*(add(9|2|4|11)|(b|#|\\+|-)(5|9|11|13))[),\\s]*")
                .matcher(lower);
        while (!lower.isEmpty() && more.reset(lower).find()) {
            String tone = more.group(1);
            if (tone.startsWith("add")) {
                added.add(tone.endsWith("4") || tone.endsWith("11") ? 5 : 2);
            } else {
                boolean flat = tone.charAt(0) == 'b' || tone.charAt(0) == '-';
                int degree = Integer.parseInt(tone.substring(1));
                if (degree == 5) {
                    fifth = flat ? 6 : 8;
                } else {
                    int natural = degree == 9 ? 2 : degree == 11 ? 5 : 9;
                    added.remove(Integer.valueOf(natural));
                    added.add(Math.floorMod(natural + (flat ? -1 : 1), 12));
                }
            }
            lower = lower.substring(more.end());
        }
        if (!lower.isEmpty()) {
            throw new IllegalArgumentException("Not a chord: \"" + symbol + "\".");
        }
        java.util.List<Integer> all = new java.util.ArrayList<>();
        all.add(root);
        if (third >= 0) {
            all.add((root + third) % 12);
        }
        all.add((root + fifth) % 12);
        for (int interval : added) {
            all.add((root + interval) % 12);
        }
        if (bass != null) {
            all.add(root(bass, symbol)[0]);
        }
        int[] distinct = all.stream().mapToInt(Integer::intValue).distinct().toArray();
        if (noThird && distinct.length < 2) {
            throw new IllegalArgumentException("Not a chord: \"" + symbol + "\".");
        }
        return new Chord(symbol.trim(), root, distinct);
    }

    /** The chord of a symbol, or {@code null} if it is none. */
    public static Chord tryParse(String symbol) {
        try {
            return parse(symbol);
        } catch (IllegalArgumentException notAChord) {
            return null;
        }
    }

    /** Root pitch class and the number of characters it took. */
    private static int[] root(String text, String symbol) {
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Not a chord: \"" + symbol + "\".");
        }
        int letter = "CDEFGAB".indexOf(Character.toUpperCase(text.charAt(0)));
        if (letter < 0) {
            throw new IllegalArgumentException("Not a chord: \"" + symbol + "\".");
        }
        int pc = new int[]{0, 2, 4, 5, 7, 9, 11}[letter];
        int used = 1;
        while (used < text.length() && (text.charAt(used) == '#' || text.charAt(used) == '♯'
                || text.charAt(used) == 'b' || text.charAt(used) == '♭')) {
            char c = text.charAt(used);
            pc += c == '#' || c == '♯' ? 1 : -1;
            used++;
        }
        return new int[]{Math.floorMod(pc, 12), used};
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Chord c && c.symbol.equals(symbol) && c.root == root && Arrays.equals(c.tones, tones);
    }

    @Override
    public int hashCode() {
        return symbol.hashCode() * 31 + Arrays.hashCode(tones);
    }

    @Override
    public String toString() {
        return symbol;
    }
}
