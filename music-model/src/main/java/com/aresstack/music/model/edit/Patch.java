package com.aresstack.music.model.edit;

import java.util.ArrayList;
import java.util.List;

/**
 * A change of a piece as data: an ordered list of operations on ids, with a short description
 * for the user. A patch says what is to change; whether it may, says {@link CompositionEditor}
 * against an {@link EditScope}. Shifts ({@link EditOp.ShiftTime}) come before every other
 * operation, so that every tick after them is a tick of the changed piece.
 */
public record Patch(String description, List<EditOp> operations) {
    public Patch {
        description = description == null ? "" : description;
        operations = List.copyOf(operations == null ? List.of() : operations);
    }

    public static Patch of(String description, EditOp... operations) {
        return new Patch(description, List.of(operations));
    }

    public boolean isEmpty() {
        return operations.isEmpty();
    }

    /** This patch followed by the operations of another one. */
    public Patch then(Patch more) {
        List<EditOp> all = new ArrayList<>(operations);
        all.addAll(more.operations());
        return new Patch(description.isEmpty() ? more.description() : description, all);
    }

    /** The shifts of the patch, in their order. */
    public List<EditOp.ShiftTime> shifts() {
        List<EditOp.ShiftTime> shifts = new ArrayList<>();
        for (EditOp operation : operations) {
            if (operation instanceof EditOp.ShiftTime shift) {
                shifts.add(shift);
            }
        }
        return shifts;
    }
}
