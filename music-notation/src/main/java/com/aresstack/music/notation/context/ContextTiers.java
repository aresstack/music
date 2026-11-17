package com.aresstack.music.notation.context;

import com.aresstack.music.model.Composition;
import com.aresstack.music.model.TimeMap;
import java.util.ArrayList;
import java.util.List;

/**
 * How much of a piece a model is told in full (report 8.3, architecture review E4: context in
 * fixed layers). Profile, form and a summary of every part are always there; what grows with the
 * length of the piece is the notes layer, so it is cut into windows:
 * <ul>
 * <li>{@link Tier#FULL}: up to {@value #FULL_BARS} bars, every note of the piece;</li>
 * <li>{@link Tier#WINDOWED}: up to {@value #WINDOWED_BARS} bars, the beginning of the piece (main
 *     theme, and the loop target of a loop) and the last bars before the continuation;</li>
 * <li>{@link Tier#LONG}: longer pieces, shorter windows, and the part summaries counted per
 *     {@value #COMPACT_BARS} bars instead of per bar.</li>
 * </ul>
 * The choice is a pure function of the piece and the request, so the context stays reproducible.
 *
 * @param compact whether the part summaries count notes per {@value #COMPACT_BARS} bars
 * @param windows the stretches whose notes are given in full, in the order of the piece
 */
public record ContextTiers(Tier tier, boolean compact, List<Window> windows) {
    public static final int FULL_BARS = 16;
    public static final int WINDOWED_BARS = 64;
    public static final int COMPACT_BARS = 4;
    /** Bars of the beginning and of the end given in full for a piece of middle length. */
    public static final int WINDOW_BARS = 8;
    /** The same for a long piece. */
    public static final int LONG_WINDOW_BARS = 4;
    public enum Tier { FULL, WINDOWED, LONG }

    /**
     * A stretch whose notes are given in full.
     *
     * @param label what the stretch is, for the model; {@code null} for the plain notes layer
     */
    public record Window(String label, long fromTick, long toTick) {
        public Window {
            if (fromTick < 0 || toTick <= fromTick) {
                throw new IllegalArgumentException("A window covers at least one tick: " + fromTick + ".." + toTick);
            }
        }
    }

    public ContextTiers {
        windows = List.copyOf(windows);
        if (windows.isEmpty()) {
            throw new IllegalArgumentException("A context gives at least one window of notes.");
        }
    }

    /**
     * The tiers for continuing a piece whose original ends at {@code newStart}.
     *
     * @param tailBars the bars at the end of the original that may be rewritten; the end window
     *                 always reaches two bars further back than these
     */
    public static ContextTiers forContinuation(Composition piece, long newStart, int tailBars, boolean loop) {
        TimeMap time = piece.timeMap();
        int bars = time.barsCovering(newStart);
        if (bars <= FULL_BARS) {
            return new ContextTiers(Tier.FULL, false, List.of(new Window(null, 0, newStart)));
        }
        boolean longPiece = bars > WINDOWED_BARS;
        int around = longPiece ? LONG_WINDOW_BARS : WINDOW_BARS;
        int endBars = Math.min(bars, Math.max(around, tailBars + 2));
        int endFrom = bars - endBars;
        int themeBars = Math.min(around, endFrom);
        List<Window> windows = new ArrayList<>();
        if (themeBars > 0) {
            windows.add(new Window(loop ? "beginning of the piece: main theme and loop target"
                    : "beginning of the piece: main theme", 0, time.tickOfBar(themeBars)));
        }
        windows.add(new Window("end of the original, where the continuation joins", time.tickOfBar(endFrom), newStart));
        return new ContextTiers(longPiece ? Tier.LONG : Tier.WINDOWED, longPiece, windows);
    }

    /**
     * The tiers for inserting new bars into a piece: {@code room} is the piece with the room
     * already made, the new bars run from {@code newStart} to {@code newEnd}. The music before and
     * after the room is what the insertion has to join, so both are given in full: for a short
     * piece all of it, for a longer one the beginning (main theme), the bars before the room
     * (reaching two bars further back than the bars that may be rewritten) and the bars after it.
     */
    public static ContextTiers forInsertion(Composition room, long newStart, long newEnd, int tailBars) {
        TimeMap time = room.timeMap();
        int beforeBars = time.barOf(newStart);
        int firstAfter = time.barOf(newEnd);
        int lastBar = Math.max(firstAfter, time.barsCovering(room.endTick()));
        int afterBars = lastBar - firstAfter;
        int original = beforeBars + afterBars;
        String beforeLabel = "before the inserted bars";
        String afterLabel = "after the inserted bars: the music the insertion leads into";
        List<Window> windows = new ArrayList<>();
        if (original <= FULL_BARS) {
            if (beforeBars > 0) {
                windows.add(new Window(beforeLabel, 0, newStart));
            }
            if (afterBars > 0) {
                windows.add(new Window(afterLabel, newEnd, time.tickOfBar(lastBar)));
            }
            return new ContextTiers(Tier.FULL, false, windows.isEmpty()
                    ? List.of(new Window(null, newStart, newEnd)) : windows);
        }
        boolean longPiece = original > WINDOWED_BARS;
        int around = longPiece ? LONG_WINDOW_BARS : WINDOW_BARS;
        int nearBefore = Math.min(beforeBars, Math.max(around, tailBars + 2));
        int nearFrom = beforeBars - nearBefore;
        int themeBars = Math.min(around, nearFrom);
        if (themeBars > 0) {
            windows.add(new Window("beginning of the piece: main theme", 0, time.tickOfBar(themeBars)));
        }
        if (nearBefore > 0) {
            windows.add(new Window(beforeLabel, time.tickOfBar(nearFrom), newStart));
        }
        int nearAfter = Math.min(afterBars, around);
        if (nearAfter > 0) {
            windows.add(new Window(afterLabel, newEnd, time.tickOfBar(firstAfter + nearAfter)));
        }
        return new ContextTiers(longPiece ? Tier.LONG : Tier.WINDOWED, longPiece, windows);
    }

    /** The last window: the end of the original (for an insertion, the music after the room). */
    public Window end() {
        return windows.get(windows.size() - 1);
    }
}
