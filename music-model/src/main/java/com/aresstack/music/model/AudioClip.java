package com.aresstack.music.model;

/**
 * A sound file placed on an audio track. It is anchored in ticks and lasts a number of samples,
 * so it does not stretch when the tempo changes.
 */
public record AudioClip(String id, long startTick, long lengthSamples, String assetId, double gainDb,
                        long fadeInSamples, long fadeOutSamples) implements Clip {
    public AudioClip {
        Ids.require(id, "a clip");
        Ids.require(assetId, "the asset of a clip");
        if (startTick < 0 || lengthSamples < 1 || fadeInSamples < 0 || fadeOutSamples < 0
                || Double.isNaN(gainDb) || Double.isInfinite(gainDb)) {
            throw new IllegalArgumentException("Invalid audio clip: " + id);
        }
    }

    /**
     * The rate the lengths and fades of audio clips count in: samples at 44.1 kHz, the rate the studio
     * renders and mixes at. An asset of another rate is converted when it is played.
     */
    public static final int SAMPLE_RATE = 44100;

    @Override
    public Track.Kind kind() {
        return Track.Kind.AUDIO;
    }

    /** How long the clip lasts in seconds. */
    public double seconds() {
        return lengthSamples / (double) SAMPLE_RATE;
    }

    /**
     * The tick at which the clip ends at the tempo of the piece: an audio clip is anchored at its
     * start in ticks and keeps its length in time when the tempo changes (architecture review 2.2).
     */
    public long endTick(TimeMap timeMap) {
        return Math.max(startTick + 1, timeMap.tickAt(timeMap.seconds(startTick) + seconds()));
    }
}
