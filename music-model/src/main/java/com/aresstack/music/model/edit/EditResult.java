package com.aresstack.music.model.edit;

import com.aresstack.music.model.Composition;
import com.aresstack.music.model.CompositionValidator.Violation;
import java.util.List;

/**
 * What {@link CompositionEditor#apply} made of a patch: the changed piece, or why the patch was
 * refused. A refused patch changes nothing.
 *
 * @param result     the changed piece, {@code null} if the patch was refused
 * @param violations why it was refused, empty if it was not
 */
public record EditResult(Composition result, List<Violation> violations) {
    public EditResult {
        violations = List.copyOf(violations == null ? List.of() : violations);
        if ((result == null) == violations.isEmpty()) {
            throw new IllegalArgumentException("A patch is either applied or refused with reasons.");
        }
    }

    public boolean applied() {
        return result != null;
    }
}
