package com.aresstack.music.model;

/**
 * One voice within a part: a line of the score with its own stem direction.
 *
 * @param staff on a grand staff the system the voice is written on; {@code null} on a single staff
 * @param stem  the preferred stem direction
 */
public record Voice(String id, String name, Staff staff, Stem stem) {
    public enum Staff { UPPER, LOWER }

    public enum Stem { AUTO, UP, DOWN }

    public Voice {
        Ids.require(id, "a voice");
        stem = stem == null ? Stem.AUTO : stem;
    }
}
