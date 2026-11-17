package com.aresstack.music.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * How a piece is played beyond its written notes, as a score says it: dynamic marks (pp, mf,
 * ff ...) from a tick on, and hairpins (crescendo, decrescendo) that lead from the level before
 * them to a level at their end. A mark or hairpin holds for one part or, without a part, for all
 * parts. How the {@link Articulation articulations} of single notes sound says
 * {@link Articulations}, kept here with the dynamic scale as the other half of the playing.
 *
 * <p>The notes keep the velocity they were written with: it is their accent within the level.
 * What they sound with is their velocity times the velocity of the level at their start, over
 * the velocity of the {@link Scale#reference() reference level}. A piece without marks
 * therefore sounds exactly as written. Which velocity a level stands for says the
 * {@link Scale} of the piece, which the user may change.
 */
public record Dynamics(List<Mark> marks, List<Hairpin> hairpins, Scale scale, Articulations articulations) {
    /** No marks: every note sounds as written. */
    public static final Dynamics NONE = new Dynamics(List.of(), List.of(), Scale.STANDARD, Articulations.STANDARD);

    public Dynamics(List<Mark> marks, List<Hairpin> hairpins, Scale scale) {
        this(marks, hairpins, scale, Articulations.STANDARD);
    }

    /**
     * How an articulated note sounds: its length and its velocity times a factor of its
     * articulation. A note without articulation sounds as written.
     *
     * @param lengths    the share of the written length an articulated note sounds, per articulation
     * @param velocities the factor on the velocity of an articulated note, per articulation
     */
    public record Articulations(Map<Articulation, Double> lengths, Map<Articulation, Double> velocities) {
        /** The longest a note may sound: twice its written value. */
        public static final double MAX_LENGTH = 2.0;
        /** The most an articulation may raise or lower the velocity. */
        public static final double MAX_VELOCITY = 2.0;

        public static final Articulations STANDARD = standard();

        public Articulations {
            lengths = complete(lengths, MAX_LENGTH, "Länge");
            velocities = complete(velocities, MAX_VELOCITY, "Anschlag");
        }

        public double length(Articulation articulation) {
            return articulation == null ? 1.0 : lengths.get(articulation);
        }

        public double velocity(Articulation articulation) {
            return articulation == null ? 1.0 : velocities.get(articulation);
        }

        public Articulations withLength(Articulation articulation, double factor) {
            EnumMap<Articulation, Double> changed = new EnumMap<>(lengths);
            changed.put(articulation, factor);
            return new Articulations(changed, velocities);
        }

        public Articulations withVelocity(Articulation articulation, double factor) {
            EnumMap<Articulation, Double> changed = new EnumMap<>(velocities);
            changed.put(articulation, factor);
            return new Articulations(lengths, changed);
        }

        private static Map<Articulation, Double> complete(Map<Articulation, Double> given, double most, String what) {
            if (given == null) {
                throw new IllegalArgumentException("Für jede Artikulation braucht es einen Faktor (" + what + ").");
            }
            EnumMap<Articulation, Double> copy = new EnumMap<>(Articulation.class);
            for (Articulation articulation : Articulation.values()) {
                Double factor = given.get(articulation);
                if (factor == null || !(factor > 0) || factor > most) {
                    throw new IllegalArgumentException(what + " von " + articulation.germanName()
                            + " liegt über 0 und höchstens bei " + most + ": " + factor);
                }
                copy.put(articulation, factor);
            }
            return java.util.Collections.unmodifiableMap(copy);
        }

        /** What score programs commonly play: staccato half as long, an accent a quarter harder, and so on. */
        private static Articulations standard() {
            EnumMap<Articulation, Double> lengths = new EnumMap<>(Articulation.class);
            EnumMap<Articulation, Double> velocities = new EnumMap<>(Articulation.class);
            put(lengths, velocities, Articulation.STACCATO, 0.5, 1.0);
            put(lengths, velocities, Articulation.STACCATISSIMO, 0.25, 1.0);
            put(lengths, velocities, Articulation.TENUTO, 1.0, 1.05);
            put(lengths, velocities, Articulation.ACCENT, 1.0, 1.25);
            put(lengths, velocities, Articulation.MARCATO, 0.75, 1.35);
            put(lengths, velocities, Articulation.LEGATO, 1.1, 1.0);
            return new Articulations(lengths, velocities);
        }

        private static void put(Map<Articulation, Double> lengths, Map<Articulation, Double> velocities,
                                Articulation articulation, double length, double velocity) {
            lengths.put(articulation, length);
            velocities.put(articulation, velocity);
        }
    }

    /** The levels of a score, from softest to loudest. */
    public enum Level {
        PPP("ppp"), PP("pp"), P("p"), MP("mp"), MF("mf"), F("f"), FF("ff"), FFF("fff");

        private final String symbol;

        Level(String symbol) {
            this.symbol = symbol;
        }

        /** As a score writes it: {@code "pp"}, {@code "mf"}. */
        public String symbol() {
            return symbol;
        }

        /** The level of a symbol as a score or a model writes it, case ignored. */
        public static Level parse(String text) {
            if (text != null) {
                String wanted = text.trim().toLowerCase(Locale.ROOT);
                for (Level level : values()) {
                    if (level.symbol.equals(wanted)) {
                        return level;
                    }
                }
            }
            throw new IllegalArgumentException("Kein Dynamikzeichen: " + text);
        }
    }

    /**
     * Which MIDI velocity each level stands for, and the level a written velocity is meant at.
     * The standard is what score programs and General MIDI players commonly use.
     */
    public record Scale(Map<Level, Integer> velocities, Level reference) {
        public static final Scale STANDARD = new Scale(standard(), Level.MF);

        public Scale {
            if (velocities == null || reference == null) {
                throw new IllegalArgumentException("A dynamic scale needs a velocity for every level and a reference.");
            }
            EnumMap<Level, Integer> copy = new EnumMap<>(Level.class);
            int before = 0;
            for (Level level : Level.values()) {
                Integer velocity = velocities.get(level);
                if (velocity == null || velocity < 1 || velocity > 127) {
                    throw new IllegalArgumentException("Die Anschlagstärke von " + level.symbol()
                            + " liegt zwischen 1 und 127: " + velocity);
                }
                if (velocity < before) {
                    throw new IllegalArgumentException("Die Stufen werden nach oben lauter: " + level.symbol());
                }
                before = velocity;
                copy.put(level, velocity);
            }
            velocities = java.util.Collections.unmodifiableMap(copy);
        }

        public int velocity(Level level) {
            return velocities.get(level);
        }

        public Scale with(Level level, int velocity) {
            EnumMap<Level, Integer> changed = new EnumMap<>(velocities);
            changed.put(level, velocity);
            return new Scale(changed, reference);
        }

        public Scale withReference(Level changed) {
            return new Scale(velocities, changed);
        }

        private static Map<Level, Integer> standard() {
            EnumMap<Level, Integer> map = new EnumMap<>(Level.class);
            int[] values = {16, 33, 49, 64, 80, 96, 112, 127};
            for (Level level : Level.values()) {
                map.put(level, values[level.ordinal()]);
            }
            return map;
        }
    }

    /**
     * From this tick on, this level.
     *
     * @param partId the part it holds for, or {@code null} for all parts
     */
    public record Mark(long tick, Level level, String partId) {
        public Mark {
            if (tick < 0 || level == null) {
                throw new IllegalArgumentException("A dynamic mark has a level and a tick from 0: " + tick);
            }
            partId = partId == null || partId.isBlank() ? null : partId;
        }

        public boolean holdsFor(String part) {
            return partId == null || partId.equals(part);
        }
    }

    /**
     * A crescendo or decrescendo: from {@code startTick} the level changes evenly to {@code target},
     * reached at {@code endTick} and held from there on. Whether it swells or fades follows from
     * the level before it.
     *
     * @param partId the part it holds for, or {@code null} for all parts
     */
    public record Hairpin(long startTick, long endTick, Level target, String partId) {
        public Hairpin {
            if (startTick < 0 || endTick <= startTick || target == null) {
                throw new IllegalArgumentException("A hairpin runs from a tick to a later one, to a level: "
                        + startTick + ".." + endTick);
            }
            partId = partId == null || partId.isBlank() ? null : partId;
        }

        public boolean holdsFor(String part) {
            return partId == null || partId.equals(part);
        }
    }

    public Dynamics {
        marks = sorted(marks, Comparator.comparingLong(Mark::tick));
        hairpins = sorted(hairpins, Comparator.comparingLong(Hairpin::startTick));
        scale = scale == null ? Scale.STANDARD : scale;
        articulations = articulations == null ? Articulations.STANDARD : articulations;
    }

    /** Whether there is no mark and no hairpin: then every note sounds as written. */
    public boolean isEmpty() {
        return marks.isEmpty() && hairpins.isEmpty();
    }

    /**
     * The velocity of the level a part plays at a tick, as a fraction of steps: the velocity of
     * the reference level before the first mark, the velocity of the last mark, or, within a
     * hairpin, the share of the way from where it began to its target.
     */
    public double levelVelocity(String partId, long tick) {
        double current = scale.velocity(scale.reference());
        int m = 0;
        int h = 0;
        // Marks and hairpins in the order they begin; on the same tick a mark comes first, so that
        // a hairpin starting at a mark leads away from that mark.
        while (m < marks.size() || h < hairpins.size()) {
            boolean markNext = h >= hairpins.size()
                    || (m < marks.size() && marks.get(m).tick() <= hairpins.get(h).startTick());
            if (markNext) {
                Mark mark = marks.get(m++);
                if (mark.tick() > tick) {
                    break;
                }
                if (mark.holdsFor(partId)) {
                    current = scale.velocity(mark.level());
                }
            } else {
                Hairpin pin = hairpins.get(h++);
                if (pin.startTick() > tick) {
                    break;
                }
                if (pin.holdsFor(partId)) {
                    double target = scale.velocity(pin.target());
                    if (tick >= pin.endTick()) {
                        current = target;
                    } else {
                        double share = (tick - pin.startTick()) / (double) (pin.endTick() - pin.startTick());
                        current = current + (target - current) * share;
                    }
                }
            }
        }
        return current;
    }

    /** The velocity a note written with {@code written} sounds with when it starts at this tick in this part. */
    public int velocity(String partId, long tick, int written) {
        return velocity(partId, tick, written, null);
    }

    /** The velocity of a note with an articulation: its level and its articulation applied. */
    public int velocity(String partId, long tick, int written, Articulation articulation) {
        if (isEmpty() && articulation == null) {
            return written;
        }
        // Within a hairpin a note starts at the loudest level the hairpin reaches; the expression
        // curve (see expression) brings it down to the level at its start and lets it swell or fade
        // while it is held.
        double factor = (isEmpty() ? 1.0 : levelVelocity(partId, tick) / expression(partId, tick)
                / scale.velocity(scale.reference())) * articulations.velocity(articulation);
        return (int) Math.max(1, Math.min(127, Math.round(written * factor)));
    }

    /** The hairpin that holds for a part at a tick (the one that began last), {@code null} if none. */
    public Hairpin hairpinAt(String partId, long tick) {
        Hairpin found = null;
        for (Hairpin pin : hairpins) {
            if (pin.holdsFor(partId) && pin.startTick() <= tick && tick < pin.endTick()) {
                found = pin;
            }
        }
        return found;
    }

    /** The loudest level velocity a hairpin reaches for a part: where it begins or its target. */
    public double peak(String partId, Hairpin pin) {
        return Math.max(levelVelocity(partId, pin.startTick()), scale.velocity(pin.target()));
    }

    /**
     * The share of the loudness a part has at a tick, from 0 to 1, as MIDI expression (controller
     * 11) plays it: within a hairpin the level there over the loudest level of the hairpin, so
     * that a held note swells or fades with it; 1 everywhere else.
     */
    public double expression(String partId, long tick) {
        Hairpin pin = hairpinAt(partId, tick);
        if (pin == null) {
            return 1.0;
        }
        double peak = peak(partId, pin);
        return peak <= 0 ? 1.0 : Math.min(1.0, levelVelocity(partId, tick) / peak);
    }

    /** A value of the expression curve: from this tick on, until the next point, this share of 127. */
    public record ExpressionPoint(long tick, int value) {
    }

    /**
     * The expression curve of a part in steps of {@code stepTicks}: the points where its value
     * changes, from the start of its first hairpin on, back to 127 where a hairpin ends. Empty
     * when no hairpin holds for the part: then nothing but the velocities shape its loudness.
     */
    public List<ExpressionPoint> expressionCurve(String partId, long stepTicks) {
        if (stepTicks < 1) {
            throw new IllegalArgumentException("The steps of an expression curve last at least one tick: " + stepTicks);
        }
        List<ExpressionPoint> points = new ArrayList<>();
        int last = 127;
        for (Hairpin pin : hairpins) {
            if (!pin.holdsFor(partId)) {
                continue;
            }
            for (long tick = pin.startTick(); tick < pin.endTick(); tick += stepTicks) {
                if (hairpinAt(partId, tick) != pin) {
                    continue;
                }
                int value = (int) Math.round(127 * expression(partId, tick));
                if (value != last) {
                    points.add(new ExpressionPoint(tick, value));
                    last = value;
                }
            }
            if (last != 127 && hairpinAt(partId, pin.endTick()) == null) {
                points.add(new ExpressionPoint(pin.endTick(), 127));
                last = 127;
            }
        }
        points.sort(Comparator.comparingLong(ExpressionPoint::tick));
        return points;
    }

    /** The ticks a note of this written length sounds with this articulation: at least one. */
    public long length(long written, Articulation articulation) {
        if (articulation == null) {
            return written;
        }
        return Math.max(1, Math.round(written * articulations.length(articulation)));
    }

    /** Whether a hairpin swells (crescendo) rather than fades (decrescendo): its target is louder than where it starts. */
    public boolean swells(Hairpin pin) {
        return scale.velocity(pin.target()) >= levelVelocity(pin.partId(), pin.startTick());
    }

    /** The marks and hairpins moved by {@code ticks} from {@code fromTick} on, for a passage put in. */
    public Dynamics shifted(long fromTick, long ticks) {
        if (isEmpty() || ticks == 0) {
            return this;
        }
        List<Mark> movedMarks = new ArrayList<>();
        for (Mark mark : marks) {
            movedMarks.add(mark.tick() > 0 && mark.tick() >= fromTick ? new Mark(mark.tick() + ticks, mark.level(), mark.partId()) : mark);
        }
        List<Hairpin> movedPins = new ArrayList<>();
        for (Hairpin pin : hairpins) {
            long start = pin.startTick() > 0 && pin.startTick() >= fromTick ? pin.startTick() + ticks : pin.startTick();
            long end = pin.endTick() > fromTick ? pin.endTick() + ticks : pin.endTick();
            movedPins.add(new Hairpin(start, end, pin.target(), pin.partId()));
        }
        return new Dynamics(movedMarks, movedPins, scale, articulations);
    }

    /** Without the marks and hairpins of a part that is gone. */
    public Dynamics withoutPart(String partId) {
        List<Mark> keptMarks = new ArrayList<>();
        for (Mark mark : marks) {
            if (!partId.equals(mark.partId())) {
                keptMarks.add(mark);
            }
        }
        List<Hairpin> keptPins = new ArrayList<>();
        for (Hairpin pin : hairpins) {
            if (!partId.equals(pin.partId())) {
                keptPins.add(pin);
            }
        }
        return new Dynamics(keptMarks, keptPins, scale, articulations);
    }

    public Dynamics withMarks(List<Mark> changed) {
        return new Dynamics(changed, hairpins, scale, articulations);
    }

    public Dynamics withHairpins(List<Hairpin> changed) {
        return new Dynamics(marks, changed, scale, articulations);
    }

    public Dynamics withScale(Scale changed) {
        return new Dynamics(marks, hairpins, changed, articulations);
    }

    public Dynamics withArticulations(Articulations changed) {
        return new Dynamics(marks, hairpins, scale, changed);
    }

    private static <T> List<T> sorted(List<T> items, Comparator<T> order) {
        List<T> copy = new ArrayList<>(items == null ? List.of() : items);
        copy.sort(order);
        return List.copyOf(copy);
    }
}
