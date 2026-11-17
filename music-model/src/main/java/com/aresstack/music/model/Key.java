package com.aresstack.music.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * A key: its tonic as a pitch class (0 = C, 1 = C sharp or D flat, ... 11 = B) and its mode.
 * The key decides how a pitch is written: its key signature ({@link #fifths()}) and the
 * spelling of every pitch ({@link #spell(int)}), so that D minor writes B flat and C sharp, not
 * A sharp and D flat.
 *
 * <p>Where two signatures are possible for a tonic (six sharps or six flats), the key says which
 * one it is ({@code flats}): G flat major and F sharp major are the same pitches written apart, as
 * are E flat minor and D sharp minor. Without saying, the major key takes the sharps (F sharp
 * major) and the minor key the flats (E flat minor). The other enharmonic keys get the signature
 * with fewer accidentals (D flat major, not C sharp major); for them {@code flats} follows from
 * the tonic.
 *
 * @param flats for a tonic of six sharps or six flats, whether the key is written with the flats
 */
public record Key(int tonic, Mode mode, boolean flats) {
    public enum Mode { MAJOR, MINOR }

    /** C major, the key of every piece from before the model. */
    public static final Key C_MAJOR = new Key(0, Mode.MAJOR);

    /** The letters of the staff, from C: a {@link Spelling#letter()} indexes this. */
    private static final String LETTERS = "CDEFGAB";
    /** The pitch class of each letter without accidental. */
    private static final int[] NATURAL = {0, 2, 4, 5, 7, 9, 11};
    /** The steps of the scales above the tonic, one per letter. */
    private static final int[] MAJOR_STEPS = {0, 2, 4, 5, 7, 9, 11};
    private static final int[] MINOR_STEPS = {0, 2, 3, 5, 7, 8, 10};
    /** Sharps are added in this order of letters (F C G D A E B), flats in the reverse one. */
    private static final int[] SHARP_ORDER = {3, 0, 4, 1, 5, 2, 6};

    public Key {
        if (tonic < 0 || tonic > 11) {
            throw new IllegalArgumentException("A tonic is a pitch class from 0 to 11: " + tonic);
        }
        if (mode == null) {
            throw new IllegalArgumentException("A key needs a mode.");
        }
        int sharps = sharps(tonic, mode);
        if (sharps != 6) {
            flats = sharps > 6;
        }
    }

    /** The key of a tonic and mode, with six sharps for a major and six flats for a minor key where both are possible. */
    public Key(int tonic, Mode mode) {
        this(tonic, mode, mode == Mode.MINOR);
    }

    /** Whether the key has six sharps or six flats, and so could be written either way. */
    public boolean enharmonicTie() {
        return sharps(tonic, mode) == 6;
    }

    /** The sharps of the key counted round the circle of fifths, 0 to 11. */
    private static int sharps(int tonic, Mode mode) {
        int major = mode == Mode.MAJOR ? tonic : (tonic + 3) % 12;
        return Math.floorMod(major * 7, 12);
    }

    /** A pitch as it is written: a letter (0 = C ... 6 = B), an alteration in semitones and an octave. */
    public record Spelling(int letter, int alteration, int octave) {
        /** The letter as written, for example {@code 'B'}. */
        public char letterName() {
            return LETTERS.charAt(letter);
        }

        /** For example {@code "Bb3"}, {@code "C#4"}, {@code "Fx5"} (double sharp). */
        public String name() {
            return letterName() + accidental(alteration) + octave;
        }

        /** The place on a staff, in diatonic steps: middle C (C4) is 35, each letter one more. */
        public int diatonic() {
            return octave * 7 + letter;
        }
    }

    /** All 24 keys: the twelve major keys from C, then the twelve minor keys from C. */
    public static List<Key> all() {
        List<Key> keys = new ArrayList<>();
        for (Mode mode : Mode.values()) {
            for (int tonic = 0; tonic < 12; tonic++) {
                keys.add(new Key(tonic, mode));
            }
        }
        return Collections.unmodifiableList(keys);
    }

    /**
     * The key signature as a position on the circle of fifths: sharps are positive (1 = one
     * sharp), flats negative (-3 = three flats), from -6 to 6.
     */
    public int fifths() {
        int fifths = sharps(tonic, mode);
        return fifths > 6 || (fifths == 6 && flats) ? fifths - 12 : fifths;
    }

    /** The alteration the key signature gives each letter, C to B: +1 sharp, -1 flat, 0 none. */
    public int[] signature() {
        int fifths = fifths();
        int[] signature = new int[7];
        for (int i = 0; i < Math.abs(fifths); i++) {
            int letter = fifths > 0 ? SHARP_ORDER[i] : SHARP_ORDER[6 - i];
            signature[letter] = fifths > 0 ? 1 : -1;
        }
        return signature;
    }

    /** The letter of the tonic as the key signature writes it (D for D minor, G for G flat major). */
    public int tonicLetter() {
        int[] signature = signature();
        for (int letter = 0; letter < 7; letter++) {
            if (Math.floorMod(NATURAL[letter] + signature[letter], 12) == tonic) {
                return letter;
            }
        }
        throw new IllegalStateException("No letter for the tonic of " + this);
    }

    /** The seven pitch classes of the scale from the tonic up: the natural minor scale for minor keys. */
    public int[] scale() {
        int[] steps = mode == Mode.MAJOR ? MAJOR_STEPS : MINOR_STEPS;
        int[] scale = new int[7];
        for (int i = 0; i < 7; i++) {
            scale[i] = (tonic + steps[i]) % 12;
        }
        return scale;
    }

    /**
     * Whether a pitch belongs to the key: to its scale, and in a minor key also the raised sixth
     * and seventh degree of the melodic and harmonic minor (B and C sharp in D minor).
     */
    public boolean contains(int pitch) {
        int step = Math.floorMod(pitch - tonic, 12);
        int[] steps = mode == Mode.MAJOR ? MAJOR_STEPS : MINOR_STEPS;
        for (int s : steps) {
            if (s == step) {
                return true;
            }
        }
        return mode == Mode.MINOR && (step == 9 || step == 11);
    }

    /**
     * How a pitch is written in this key. A pitch of the scale is written as the key signature
     * has it. The other five follow the usual practice: in major keys the raised first, fourth
     * and fifth and the lowered third and seventh degree (in C major: C sharp, F sharp, G sharp,
     * E flat, B flat); in minor keys the lowered second and the raised third, fourth, sixth and
     * seventh degree (in D minor: E flat, F sharp, G sharp, B, C sharp).
     */
    public Spelling spell(int pitch) {
        if (pitch < 0 || pitch > 127) {
            throw new IllegalArgumentException("A MIDI pitch runs from 0 to 127: " + pitch);
        }
        int step = Math.floorMod(pitch - tonic, 12);
        int[] steps = mode == Mode.MAJOR ? MAJOR_STEPS : MINOR_STEPS;
        int degree = -1;
        int direction = 0;
        for (int i = 0; i < 7; i++) {
            if (steps[i] == step) {
                degree = i;
            }
        }
        if (degree < 0) {
            int[][] chromatic = mode == Mode.MAJOR
                    ? new int[][]{{1, 0, 1}, {3, 2, -1}, {6, 3, 1}, {8, 4, 1}, {10, 6, -1}}
                    : new int[][]{{1, 1, -1}, {4, 2, 1}, {6, 3, 1}, {9, 5, 1}, {11, 6, 1}};
            for (int[] entry : chromatic) {
                if (entry[0] == step) {
                    degree = entry[1];
                    direction = entry[2];
                }
            }
        }
        int letter = (tonicLetter() + degree) % 7;
        int alteration = signature()[letter] + direction;
        int octave = Math.floorDiv(pitch - alteration - NATURAL[letter], 12) - 1;
        return new Spelling(letter, alteration, octave);
    }

    /** The tonic as written, for example {@code "D"}, {@code "F#"}, {@code "Bb"}. */
    public String tonicName() {
        int letter = tonicLetter();
        return LETTERS.charAt(letter) + accidental(signature()[letter]);
    }

    /** The name in English, as the prompts and files write it: {@code "D minor"}, {@code "Bb major"}. */
    public String name() {
        return tonicName() + (mode == Mode.MAJOR ? " major" : " minor");
    }

    /** The name in German, as the studio shows it: {@code "d-Moll"}, {@code "B-Dur"}, {@code "Fis-Dur"}. */
    public String germanName() {
        int letter = tonicLetter();
        int alteration = signature()[letter];
        String base = String.valueOf(LETTERS.charAt(letter));
        String name;
        if (alteration == 0) {
            name = "B".equals(base) ? "H" : base;
        } else if (alteration > 0) {
            name = ("B".equals(base) ? "H" : base) + "is";
        } else if ("B".equals(base)) {
            name = "B";
        } else if ("E".equals(base) || "A".equals(base)) {
            name = base + "s";
        } else {
            name = base + "es";
        }
        return mode == Mode.MAJOR ? name + "-Dur" : name.toLowerCase(Locale.ROOT) + "-Moll";
    }

    @Override
    public String toString() {
        return name();
    }

    /**
     * Reads a key as people write it: {@code "D minor"}, {@code "d-Moll"}, {@code "Dm"},
     * {@code "F# major"}, {@code "Bb"}, {@code "Fis-Dur"}, {@code "es-moll"}. A tonic without a mode
     * is major, unless it is a lower-case German name ({@code "d"}).
     *
     * @throws IllegalArgumentException if the text names no key
     */
    public static Key parse(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("No key given.");
        }
        String t = text.trim().replace('♯', '#').replace('♭', 'b').replace('\t', ' ');
        Mode mode = null;
        String lower = t.toLowerCase(Locale.ROOT);
        String[][] modes = {{"minor", "MINOR"}, {"moll", "MINOR"}, {"major", "MAJOR"}, {"dur", "MAJOR"},
                {"min", "MINOR"}, {"maj", "MAJOR"}};
        for (String[] candidate : modes) {
            if (lower.endsWith(candidate[0])) {
                mode = Mode.valueOf(candidate[1]);
                t = t.substring(0, t.length() - candidate[0].length()).trim();
                if (t.endsWith("-")) {
                    t = t.substring(0, t.length() - 1).trim();
                }
                break;
            }
        }
        if (mode == null && t.length() >= 2 && t.endsWith("m")) {
            mode = Mode.MINOR; // "Dm", "F#m", "Bbm"
            t = t.substring(0, t.length() - 1);
        }
        if (t.isEmpty()) {
            throw new IllegalArgumentException("No tonic in the key \"" + text + "\".");
        }
        boolean lowerCaseTonic = Character.isLowerCase(t.charAt(0));
        int tonic = tonic(t, text);
        if (mode == null) {
            mode = lowerCaseTonic && t.length() <= 3 && !t.contains("#") ? Mode.MINOR : Mode.MAJOR;
        }
        Key key = new Key(tonic, mode);
        if (key.enharmonicTie()) {
            // As written: "Gb major" has the flats, "D# minor" the sharps.
            return new Key(tonic, mode, writtenFlat(t, text));
        }
        return key;
    }

    /** The pitch class of a tonic: English ({@code C#}, {@code Bb}) or German ({@code Cis}, {@code B}, {@code H}, {@code Es}). */
    private static int tonic(String written, String text) {
        String t = written.trim();
        char first = Character.toUpperCase(t.charAt(0));
        String rest = t.substring(1).toLowerCase(Locale.ROOT);
        int base;
        if (first == 'H') {
            base = 11;
        } else {
            int letter = LETTERS.indexOf(first);
            if (letter < 0) {
                throw new IllegalArgumentException("Not a key: \"" + text + "\".");
            }
            base = NATURAL[letter];
        }
        // German: "B" alone is B flat; "Bes" is B double flat.
        if (first == 'B' && (rest.isEmpty() || rest.equals("es")) && looksGerman(text)) {
            return Math.floorMod(10 - (rest.isEmpty() ? 0 : 1), 12);
        }
        int alteration = 0;
        switch (rest) {
            case "": break;
            case "#": case "is": case "sharp": alteration = 1; break;
            case "##": case "x": case "isis": alteration = 2; break;
            case "b": case "es": case "s": case "flat": alteration = -1; break;
            case "bb": case "eses": case "ses": alteration = -2; break;
            default: throw new IllegalArgumentException("Not a key: \"" + text + "\".");
        }
        return Math.floorMod(base + alteration, 12);
    }

    /** Whether a tonic is written with a flat: {@code Gb}, {@code Ges}, {@code Es}, German {@code B}. */
    private static boolean writtenFlat(String written, String text) {
        String rest = written.trim().substring(1).toLowerCase(Locale.ROOT);
        char first = Character.toUpperCase(written.trim().charAt(0));
        return rest.startsWith("b") || rest.startsWith("es") || rest.equals("s") || rest.startsWith("flat")
                || (first == 'B' && rest.isEmpty() && looksGerman(text));
    }

    private static boolean looksGerman(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("dur") || lower.contains("moll");
    }

    private static String accidental(int alteration) {
        switch (alteration) {
            case -2: return "bb";
            case -1: return "b";
            case 1: return "#";
            case 2: return "x";
            default: return "";
        }
    }
}
