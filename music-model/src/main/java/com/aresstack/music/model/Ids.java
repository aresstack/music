package com.aresstack.music.model;

import java.security.SecureRandom;
import java.util.Random;
import java.util.function.Supplier;

/**
 * Short, random, unchangeable ids for the objects of a piece. An id begins with a letter and
 * goes on with letters and digits, so that it can also stand where a track id of the old
 * arrangement format stands. A seeded source gives the same ids for the same seed, so that an
 * import of the same old piece always gives the same model.
 */
public final class Ids implements Supplier<String> {
    /** The length of a new id. */
    public static final int LENGTH = 8;
    private static final String LETTERS = "abcdefghijkmnpqrstuvwxyz"; // no l and o, they look like 1 and 0
    private static final String SIGNS = LETTERS + "23456789";

    private final Random random;

    private Ids(Random random) {
        this.random = random;
    }

    /** Ids nobody can predict, for new objects. */
    public static Ids random() {
        return new Ids(new SecureRandom());
    }

    /** The same ids, in the same order, for the same seed. */
    public static Ids seeded(long seed) {
        return new Ids(new Random(seed));
    }

    /** The next id. */
    public synchronized String next() {
        StringBuilder id = new StringBuilder(LENGTH);
        id.append(LETTERS.charAt(random.nextInt(LETTERS.length())));
        while (id.length() < LENGTH) {
            id.append(SIGNS.charAt(random.nextInt(SIGNS.length())));
        }
        return id.toString();
    }

    @Override
    public String get() {
        return next();
    }

    /** Whether a text can be an id: a letter, then up to 63 letters, digits, '-' or '_'. */
    public static boolean valid(String id) {
        return id != null && id.matches("[a-z][a-z0-9_-]{0,63}");
    }

    static String require(String id, String what) {
        if (!valid(id)) {
            throw new IllegalArgumentException("Invalid id of " + what + ": " + id);
        }
        return id;
    }
}
