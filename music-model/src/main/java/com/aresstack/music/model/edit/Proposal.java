package com.aresstack.music.model.edit;

import com.aresstack.music.model.Composition;

/**
 * A patch that was applied and passed every check but is not taken over yet (architecture review
 * 2.3): the piece it was made on, the piece it would give, and which parts sound different. The
 * application renders a preview from {@link #result()} without overwriting anything; accepting
 * makes the result the piece and a revision, discarding only throws the preview away.
 *
 * @param runId for a proposal of the AI the run it came from, else {@code null}
 */
public record Proposal(String id, Composition base, Patch patch, EditScope scope, Composition result,
                       ChangeSet changes, Revision.Author author, String runId) {
    public Proposal {
        if (id == null || base == null || patch == null || scope == null || result == null || changes == null
                || author == null) {
            throw new IllegalArgumentException("A proposal needs its id, both pieces, patch, scope, changes and author.");
        }
    }

    /**
     * Applies a patch for a proposal; empty result and the reasons if it is refused.
     *
     * @return the edit result and, if it applied, the proposal
     */
    public static Outcome make(String id, Composition base, Patch patch, EditScope scope, Revision.Author author,
                               String runId) {
        EditResult result = CompositionEditor.apply(base, patch, scope);
        if (!result.applied()) {
            return new Outcome(null, result);
        }
        return new Outcome(new Proposal(id, base, patch, scope, result.result(),
                ChangeSet.between(base, result.result()), author, runId), result);
    }

    /** A proposal, or {@code null} and the reasons why the patch was refused. */
    public record Outcome(Proposal proposal, EditResult result) {
        public boolean made() {
            return proposal != null;
        }
    }
}
