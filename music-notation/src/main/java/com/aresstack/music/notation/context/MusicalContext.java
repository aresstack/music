package com.aresstack.music.notation.context;

/**
 * What a model is told about a piece before it writes into it (architecture review E4, report
 * 8.3): a text in four fixed layers and a digest of it. The same piece and the same window give
 * the same bytes, so the digest can go into the cache key of every answer built on it.
 *
 * @param text     the context as the model reads it
 * @param digest   SHA-256 of {@code text}, in hex
 * @param fromTick the first tick whose notes are given in full
 * @param toTick   the first tick after them
 */
public record MusicalContext(String text, String digest, long fromTick, long toTick) {
    /** The first twelve signs of the digest, enough to tell runs apart in file names. */
    public String shortDigest() {
        return digest.substring(0, 12);
    }
}
