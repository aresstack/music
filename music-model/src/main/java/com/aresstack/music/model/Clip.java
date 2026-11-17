package com.aresstack.music.model;

/**
 * A stretch of a track. Clips are stored with start and length only; that clips of one track
 * do not overlap is a rule of the validator, not of the format.
 */
public sealed interface Clip permits MidiClip, AudioClip {
    String id();

    long startTick();

    /** The kind of track the clip belongs on. */
    Track.Kind kind();
}
