package com.aresstack.music.model.edit;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/**
 * What a change may touch (architecture review 2.3, E5): which parts, tracks and clips, which
 * stretch of time, and which kinds of operation. Everything else stays as it is, and
 * {@link CompositionEditor} proves that it did.
 *
 * <p>A clip is a target if its id is named, its track is named, or the part that plays it is
 * named. A note belongs to the stretch of time in which it <em>begins</em>; a note that begins
 * before the stretch and sounds into it is outside and stays as it is. Objects that the change
 * itself creates (a new part or track) are targets if the scope allows to create them.
 *
 * <p>Ticks are ticks of the piece <em>after</em> any shift of the patch: for {@code INSERT} the
 * stretch is the new, empty room.
 *
 * @param anyObject       every part, track and clip is a target
 * @param fromTick        the first tick of the stretch
 * @param toTick          the first tick after it; {@link Long#MAX_VALUE} for "to the end"
 * @param kinds           the kinds of operation that are allowed
 * @param allowsShift     whether the patch may shift time ({@link EditOp.ShiftTime}); needs
 *                        {@link EditOp.Kind#SHIFT_TIME} in {@code kinds} as well
 * @param transitionTicks a zone around the stretch: notes that begin up to this many ticks
 *                        before it may change as well, and new notes may sound this far past
 *                        its end; 0 for none
 */
public record EditScope(boolean anyObject, Set<String> partIds, Set<String> trackIds, Set<String> clipIds,
                        long fromTick, long toTick, Set<EditOp.Kind> kinds, boolean allowsShift,
                        long transitionTicks) {
    public static final long END = Long.MAX_VALUE;

    /** What changing the notes and clips of a part needs, and no more. */
    public static final Set<EditOp.Kind> NOTES_AND_CLIPS = Set.copyOf(EnumSet.of(EditOp.Kind.ADD_CLIP,
            EditOp.Kind.REMOVE_CLIP, EditOp.Kind.MOVE_CLIP, EditOp.Kind.SPLIT_CLIP, EditOp.Kind.ADD_NOTES,
            EditOp.Kind.REMOVE_NOTES, EditOp.Kind.CHANGE_NOTE));

    public EditScope {
        partIds = Set.copyOf(partIds == null ? Set.of() : partIds);
        trackIds = Set.copyOf(trackIds == null ? Set.of() : trackIds);
        clipIds = Set.copyOf(clipIds == null ? Set.of() : clipIds);
        kinds = kinds == null || kinds.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(kinds));
        if (fromTick < 0 || toTick <= fromTick || transitionTicks < 0) {
            throw new IllegalArgumentException("A scope covers at least one tick from tick 0 on: " + fromTick + ".." + toTick);
        }
    }

    /**
     * Everything, every kind of operation, shifts included: what the user may do by hand to the
     * whole piece. The proof then has nothing to protect, but every operation is still checked.
     */
    public static EditScope everything() {
        return new EditScope(true, Set.of(), Set.of(), Set.of(), 0, END, EnumSet.allOf(EditOp.Kind.class), true, 0);
    }

    /** The notes and clips of one part over the whole piece: what composing a track anew needs. */
    public static EditScope part(String partId) {
        return parts(Set.of(partId), 0, END);
    }

    /** The notes and clips of some parts within a stretch of time. */
    public static EditScope parts(Set<String> partIds, long fromTick, long toTick) {
        return new EditScope(false, partIds, Set.of(), Set.of(), fromTick, toTick, NOTES_AND_CLIPS, false, 0);
    }

    /** The same scope with these kinds of operation allowed in addition. */
    public EditScope allowing(EditOp.Kind... more) {
        EnumSet<EditOp.Kind> all = kinds.isEmpty() ? EnumSet.noneOf(EditOp.Kind.class) : EnumSet.copyOf(kinds);
        all.addAll(Set.of(more));
        return new EditScope(anyObject, partIds, trackIds, clipIds, fromTick, toTick, all, allowsShift, transitionTicks);
    }

    /** The same scope, allowed to shift time. */
    public EditScope allowingShift() {
        return new EditScope(anyObject, partIds, trackIds, clipIds, fromTick, toTick, kinds, true, transitionTicks)
                .allowing(EditOp.Kind.SHIFT_TIME);
    }

    /** The same scope with a transition zone. */
    public EditScope withTransition(long ticks) {
        return new EditScope(anyObject, partIds, trackIds, clipIds, fromTick, toTick, kinds, allowsShift, ticks);
    }

    /** The same scope with these tracks as targets in addition. */
    public EditScope withTracks(Set<String> more) {
        return new EditScope(anyObject, partIds, union(trackIds, more), clipIds, fromTick, toTick, kinds, allowsShift,
                transitionTicks);
    }

    /** The same scope with these clips as targets in addition. */
    public EditScope withClips(Set<String> more) {
        return new EditScope(anyObject, partIds, trackIds, union(clipIds, more), fromTick, toTick, kinds, allowsShift,
                transitionTicks);
    }

    /** Whether an operation of this kind may be in the patch. */
    public boolean allows(EditOp.Kind kind) {
        return kinds.contains(kind) && (kind != EditOp.Kind.SHIFT_TIME || allowsShift);
    }

    /** The first tick at which a note may change: the start of the stretch less the transition zone. */
    public long editFrom() {
        return Math.max(0, fromTick - transitionTicks);
    }

    /** Whether a note that begins at this tick lies within the scope's time. */
    public boolean begins(long tick) {
        return tick >= editFrom() && tick < toTick;
    }

    /** The latest tick at which a note that may change may end. */
    public long noteEndLimit() {
        return toTick >= END - transitionTicks ? END : toTick + transitionTicks;
    }

    /** Whether a clip from {@code start} to {@code end} lies wholly within the scope's time. */
    public boolean covers(long start, long end) {
        return start >= editFrom() && end <= toTick;
    }

    private static Set<String> union(Set<String> a, Set<String> b) {
        Set<String> all = new HashSet<>(a);
        all.addAll(b);
        return all;
    }
}
