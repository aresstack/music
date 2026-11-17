package com.aresstack.music.model.edit;

import com.aresstack.music.model.Composition;
import com.aresstack.music.model.mix.MixScene;

/**
 * A lasting state of a piece (architecture review E5): a full copy, not a log of operations, so
 * that an old revision stays readable whatever operations exist later. Revisions are made before
 * and after every accepted AI change, when the piece is saved, and when the user asks for one.
 * The first revision of a piece is its original and is never deleted.
 *
 * @param sequence    the number of the revision within its piece, from 1
 * @param parentId    the revision this one follows, {@code null} for the first
 * @param timeMillis  when it was made, in milliseconds since 1970
 * @param description what happened, in a few words for the user
 * @param runId       for a change by the AI the run it came from, else {@code null}
 * @param mix         the mix as it was with the piece (a shift moves its automation), or {@code null} if none
 */
public record Revision(String id, int sequence, String parentId, Author author, long timeMillis, String description,
                       String runId, Composition composition, MixScene mix) {
    /** Who made the state of a revision. */
    public enum Author {
        /** The user, by hand. */
        USER,
        /** An AI, by a proposal the user accepted. */
        AI,
        /** The state a piece had when it was first opened as a model: the original. */
        ORIGINAL
    }

    public Revision {
        if (id == null || id.isBlank() || sequence < 1 || author == null || composition == null) {
            throw new IllegalArgumentException("A revision has an id, a number from 1, an author and a piece.");
        }
        description = description == null ? "" : description;
    }

    /** Whether this is the original of the piece, which is never deleted. */
    public boolean original() {
        return sequence == 1;
    }
}
