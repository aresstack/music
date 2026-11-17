package com.aresstack.music.model.mix;

import com.aresstack.music.model.Ids;
import java.util.List;

/**
 * A parameter of a channel over time. Points are placed in ticks of the piece, not in bars or
 * seconds, so that they stay with the music when tempo or meter change. Between two points the
 * value runs in a straight line; before the first point it holds the value of the first, after
 * the last the value of the last. Two points at the same tick make a jump.
 *
 * <p>The parameters (architecture review, work package D3):
 * <ul>
 * <li>{@link #LEVEL}: dB above or below the level of the channel, which itself stays in the
 * channel. The level curve of the old stage ({@code dynamics}) is this lane.</li>
 * <li>{@link #X}, {@link #DEPTH}: the place of the channel on the stage, as in {@link MixChannel}.
 * While a channel has such a lane, the lane says where it stands and the value in the channel is
 * where it stands when nothing moves; a lane on both makes the channel a moving source.</li>
 * <li>{@link #REVERB}: the share of the channel sent to the room, as in {@link MixChannel}.</li>
 * <li>{@link #HEIGHT} (work package D4): how high the channel stands above the ears of the
 * listener, 0 (ear height) to 1 (as high as the stage reaches). A channel without this lane stands
 * at the height of its placement.</li>
 * </ul>
 * Other names are kept as they come, so that a newer file loses nothing, but are not heard; the
 * {@link MixValidator} names them.
 *
 * @param channelId the channel, or {@code null} for the master
 * @param parameter what is automated, one of the names above
 */
public record AutomationLane(String id, String channelId, String parameter, List<Point> points) {
    /** dB above or below the level of the channel. */
    public static final String LEVEL = "level";
    /** Between -1 (far left) and +1 (far right), as {@link MixChannel#x()}. */
    public static final String X = "x";
    /** Between 0 (front edge) and 1 (back wall), as {@link MixChannel#depth()}. */
    public static final String DEPTH = "depth";
    /** Share sent to the room, 0 to 1, as {@link MixChannel#reverb()}. */
    public static final String REVERB = "reverb";
    /** Between 0 (ear height) and 1 (top of the stage); work package D4. */
    public static final String HEIGHT = "height";
    /** The parameters the studio knows, in this order. */
    public static final List<String> PARAMETERS = List.of(LEVEL, X, DEPTH, REVERB, HEIGHT);

    public record Point(long tick, double value) {
        public Point {
            if (tick < 0 || Double.isNaN(value) || Double.isInfinite(value)) {
                throw new IllegalArgumentException("Invalid automation point at tick " + tick);
            }
        }
    }

    public AutomationLane {
        if (!Ids.valid(id) || (channelId != null && !Ids.valid(channelId))) {
            throw new IllegalArgumentException("Invalid automation lane: " + id);
        }
        if (parameter == null || parameter.isBlank()) {
            throw new IllegalArgumentException("An automation lane names its parameter: " + id);
        }
        points = List.copyOf(points == null ? List.of() : points);
        for (int i = 1; i < points.size(); i++) {
            if (points.get(i).tick() < points.get(i - 1).tick()) {
                throw new IllegalArgumentException("The points of an automation lane are in order: " + id);
            }
        }
        double least = least(parameter);
        double most = most(parameter);
        for (Point point : points) {
            if (point.value() < least || point.value() > most) {
                throw new IllegalArgumentException("The " + parameter + " of automation lane " + id
                        + " lies between " + least + " and " + most + ": " + point.value() + " at tick " + point.tick());
            }
        }
    }

    /** Whether the studio knows what this lane moves. */
    public boolean known() {
        return PARAMETERS.contains(parameter);
    }

    /**
     * The value at a tick, between two points in a straight line. A lane without points has no
     * value; ask {@link #points()} first.
     */
    public double valueAt(long tick) {
        if (points.isEmpty()) {
            throw new IllegalStateException("Automation lane " + id + " has no points.");
        }
        Point before = points.get(0);
        if (tick <= before.tick()) {
            return before.value();
        }
        for (int i = 1; i < points.size(); i++) {
            Point point = points.get(i);
            if (tick <= point.tick()) {
                long span = point.tick() - before.tick();
                return span <= 0 ? point.value()
                        : before.value() + (point.value() - before.value()) * (tick - before.tick()) / span;
            }
            before = point;
        }
        return before.value();
    }

    /** The smallest value a parameter may take. */
    public static double least(String parameter) {
        return switch (parameter) {
            case X -> -1;
            case DEPTH, REVERB, HEIGHT -> 0;
            default -> Double.NEGATIVE_INFINITY;
        };
    }

    /** The largest value a parameter may take. */
    public static double most(String parameter) {
        return switch (parameter) {
            case X, DEPTH, REVERB, HEIGHT -> 1;
            default -> Double.POSITIVE_INFINITY;
        };
    }
}
