package com.aresstack.music.model.edit;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Undo and redo of a session (architecture review E5): a stack of unchangeable states, usually
 * a {@link com.aresstack.music.model.Composition} or a state that holds one. Since a
 * changed piece shares everything it did not change with the one before, keeping them is cheap.
 * A new change after an undo forgets what could have been redone.
 *
 * @param <S> the kind of state; it must be immutable and compare by value
 */
public final class EditHistory<S> {
    /** A state and what led to it. */
    public record Step<S>(S state, String description) {
    }

    /** How many steps are kept by default. */
    public static final int DEFAULT_LIMIT = 500;

    private final int limit;
    private final Deque<Step<S>> undo = new ArrayDeque<>();
    private final Deque<Step<S>> redo = new ArrayDeque<>();
    private Step<S> current;

    public EditHistory(S initial) {
        this(initial, DEFAULT_LIMIT);
    }

    public EditHistory(S initial, int limit) {
        if (initial == null || limit < 1) {
            throw new IllegalArgumentException("A history starts with a piece and keeps at least one step.");
        }
        this.limit = limit;
        this.current = new Step<>(initial, "");
    }

    public synchronized S current() {
        return current.state();
    }

    /** Records a new state; does nothing if it is the current one. */
    public synchronized void push(S next, String description) {
        if (next.equals(current.state())) {
            return;
        }
        undo.push(current);
        while (undo.size() > limit) {
            undo.removeLast();
        }
        redo.clear();
        current = new Step<>(next, description == null ? "" : description);
    }

    /**
     * Corrects the current state without making a step of it: what the state is, as it turns out
     * to be (for example the stage the studio kept beside the model, taken over just before a step
     * that changes it, so that undoing that step brings back what the user saw). An undo goes back
     * to the state before, as it would have; a redo still waiting is forgotten, since it was made
     * from the uncorrected state.
     */
    public synchronized void amend(S state) {
        if (state == null) {
            throw new IllegalArgumentException("A state of the history is never null.");
        }
        if (state.equals(current.state())) {
            return;
        }
        redo.clear();
        current = new Step<>(state, current.description());
    }

    /** Starts again from a state that is not a step, for example a piece loaded anew; forgets both stacks. */
    public synchronized void reset(S state) {
        undo.clear();
        redo.clear();
        current = new Step<>(state, "");
    }

    public synchronized boolean canUndo() {
        return !undo.isEmpty();
    }

    public synchronized boolean canRedo() {
        return !redo.isEmpty();
    }

    /** What an undo would take back, or {@code null}. */
    public synchronized String undoDescription() {
        return undo.isEmpty() ? null : current.description();
    }

    /** What a redo would do again, or {@code null}. */
    public synchronized String redoDescription() {
        return redo.isEmpty() ? null : redo.peek().description();
    }

    /** Goes one step back and gives the state there. */
    public synchronized S undo() {
        if (undo.isEmpty()) {
            throw new IllegalStateException("Nothing to undo.");
        }
        redo.push(current);
        current = undo.pop();
        return current.state();
    }

    /** Goes one step forward again and gives the state there. */
    public synchronized S redo() {
        if (redo.isEmpty()) {
            throw new IllegalStateException("Nothing to redo.");
        }
        undo.push(current);
        current = redo.pop();
        return current.state();
    }
}
