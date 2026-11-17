package com.aresstack.music.model.mix;

import com.aresstack.music.model.Ids;

/**
 * A channel strip: where its sound stands on the stage and how loud it is. Seen from the
 * listener, {@code x} runs from -1 (far left) to +1 (far right) and {@code depth} from 0 (front
 * edge) to 1 (back wall).
 *
 * @param owner whose sound the channel carries: a part, or an audio track
 */
public record MixChannel(String id, Owner owner, double x, double depth, double levelDb, double width, double reverb,
                         boolean muted, boolean locked) {
    /** The part or the audio track a channel belongs to. */
    public record Owner(Kind kind, String id) {
        public enum Kind { PART, AUDIO_TRACK }

        public Owner {
            if (kind == null) {
                throw new IllegalArgumentException("An owner is a part or an audio track.");
            }
            if (!Ids.valid(id)) {
                throw new IllegalArgumentException("Invalid id of the owner of a channel: " + id);
            }
        }

        public static Owner part(String partId) {
            return new Owner(Kind.PART, partId);
        }
    }

    public MixChannel {
        if (!Ids.valid(id)) {
            throw new IllegalArgumentException("Invalid id of a channel: " + id);
        }
        if (owner == null) {
            throw new IllegalArgumentException("A channel has an owner: " + id);
        }
        if (!(x >= -1 && x <= 1) || !(depth >= 0 && depth <= 1) || !(width >= 0 && width <= 1)
                || !(reverb >= 0 && reverb <= 1) || Double.isNaN(levelDb) || Double.isInfinite(levelDb)) {
            throw new IllegalArgumentException("Invalid channel values: " + id);
        }
    }
}
