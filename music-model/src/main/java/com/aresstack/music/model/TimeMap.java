package com.aresstack.music.model;

import java.util.List;

/**
 * How the time of a piece runs: ticks at {@link #PPQ} per quarter are the canonical time;
 * bars, beats and seconds are derived from the events here. There are lists of tempo, meter
 * and key events: a piece may change its tempo, suddenly or gliding (accelerando, ritardando),
 * its meter at the start of a bar, and its key.
 *
 * <p>A gliding tempo changes evenly per tick from its own value to the value of the next tempo
 * event, as score programs play an accelerando or ritardando; the time in seconds is the exact
 * integral of it. A player that knows only steps of tempo (a MIDI file) gets {@link #playedTempos()}:
 * steps of {@link #GLIDE_STEP_TICKS} ticks whose boundaries fall exactly on the exact times.
 */
public record TimeMap(List<Tempo> tempos, List<Meter> meters, List<KeyChange> keys) {
    /** Ticks per quarter note, as in every file the studio has written. */
    public static final int PPQ = 480;

    /**
     * How finely a gliding tempo is cut into steps for a player that knows steps only: a 32nd
     * note. Finer steps change nothing audible and only make the MIDI file longer; the step
     * boundaries lie exactly where the glide puts them, so the error never adds up.
     */
    public static final int GLIDE_STEP_TICKS = PPQ / 8;

    /**
     * From this tick on, this many quarters per minute.
     *
     * @param glide whether the tempo changes evenly from here to the next tempo event
     *              (accelerando if that is faster, ritardando if slower) instead of jumping there
     */
    public record Tempo(long tick, double bpm, boolean glide) {
        public Tempo {
            if (tick < 0 || !(bpm > 0) || Double.isInfinite(bpm)) {
                throw new IllegalArgumentException("Invalid tempo " + bpm + " at tick " + tick);
            }
        }

        /** A tempo that holds until the next one. */
        public Tempo(long tick, double bpm) {
            this(tick, bpm, false);
        }
    }

    /** From this tick on (the start of a bar), this meter. */
    public record Meter(long tick, int numerator, int denominator) {
        public Meter {
            if (tick < 0 || numerator < 1 || numerator > 64 || Integer.bitCount(denominator) != 1
                    || denominator > 64) {
                throw new IllegalArgumentException("Invalid meter " + numerator + "/" + denominator + " at tick " + tick);
            }
        }

        /** The length of one bar in ticks. */
        public long barTicks() {
            return (long) PPQ * 4 * numerator / denominator;
        }
    }

    /** From this tick on, this key. */
    public record KeyChange(long tick, Key key) {
        public KeyChange {
            if (tick < 0 || key == null) {
                throw new IllegalArgumentException("Invalid key change at tick " + tick);
            }
        }
    }

    public TimeMap {
        tempos = ordered(tempos, "tempo");
        meters = ordered(meters, "meter");
        keys = ordered(keys, "key");
        if (tempos.get(tempos.size() - 1).glide()) {
            throw new IllegalArgumentException("The last tempo has no tempo to glide to.");
        }
        for (int i = 1; i < meters.size(); i++) {
            Meter before = meters.get(i - 1);
            if ((meters.get(i).tick() - before.tick()) % before.barTicks() != 0) {
                throw new IllegalArgumentException("A meter changes only at the start of a bar: tick " + meters.get(i).tick());
            }
        }
    }

    /** One tempo, four quarters to the bar, C major: what every piece from before the model has. */
    public static TimeMap of(double bpm) {
        return new TimeMap(List.of(new Tempo(0, bpm)), List.of(new Meter(0, 4, 4)),
                List.of(new KeyChange(0, Key.C_MAJOR)));
    }

    /** The tempo at a tick; within a glide the tempo the glide has reached there. */
    public double tempoAt(long tick) {
        int i = indexAt(tick);
        Tempo tempo = tempos.get(i);
        if (!tempo.glide() || tick <= tempo.tick()) {
            return tempo.bpm();
        }
        Tempo next = tempos.get(i + 1);
        double share = (tick - tempo.tick()) / (double) (next.tick() - tempo.tick());
        return tempo.bpm() + (next.bpm() - tempo.bpm()) * share;
    }

    /** Whether the tempo glides at this tick (within an accelerando or ritardando). */
    public boolean glidingAt(long tick) {
        int i = indexAt(tick);
        return tempos.get(i).glide() && tick >= tempos.get(i).tick();
    }

    /** Whether the tempo stays the same between two ticks: no event between them and no glide. */
    public boolean steadyBetween(long fromTick, long toTick) {
        if (glidingAt(fromTick)) {
            return false;
        }
        for (Tempo tempo : tempos) {
            if (tempo.tick() > fromTick && tempo.tick() < toTick) {
                return false;
            }
        }
        return true;
    }

    /**
     * The tempos as steps, for a player that knows no glide: every gliding stretch is cut into
     * steps of {@link #GLIDE_STEP_TICKS}, each with the tempo that makes it last exactly as long
     * as the glide does there. Without a glide these are the tempos themselves.
     */
    public List<Tempo> playedTempos() {
        boolean any = false;
        for (Tempo tempo : tempos) {
            any |= tempo.glide();
        }
        if (!any) {
            return tempos;
        }
        List<Tempo> steps = new java.util.ArrayList<>();
        for (int i = 0; i < tempos.size(); i++) {
            Tempo tempo = tempos.get(i);
            if (!tempo.glide()) {
                steps.add(new Tempo(tempo.tick(), tempo.bpm()));
                continue;
            }
            long end = tempos.get(i + 1).tick();
            for (long from = tempo.tick(); from < end; from += GLIDE_STEP_TICKS) {
                long to = Math.min(end, from + GLIDE_STEP_TICKS);
                double lasting = seconds(to) - seconds(from);
                steps.add(new Tempo(from, (to - from) * 60.0 / (PPQ * lasting)));
            }
        }
        return List.copyOf(steps);
    }

    public Meter meterAt(long tick) {
        return at(meters, tick);
    }

    public Key keyAt(long tick) {
        return at(keys, tick).key();
    }

    /** Whether the piece has one tempo, one meter and one key only. */
    public boolean constant() {
        return tempos.size() == 1 && meters.size() == 1 && keys.size() == 1;
    }

    /** The tick at which a bar begins, bars counted from 0. */
    public long tickOfBar(int bar) {
        if (bar < 0) {
            throw new IllegalArgumentException("Bars are counted from 0: " + bar);
        }
        long tick = 0;
        int counted = 0;
        for (int i = 0; i < meters.size(); i++) {
            Meter meter = meters.get(i);
            long until = i + 1 < meters.size() ? meters.get(i + 1).tick() : Long.MAX_VALUE;
            long barsHere = until == Long.MAX_VALUE ? Long.MAX_VALUE : (until - meter.tick()) / meter.barTicks();
            if (bar - counted < barsHere) {
                return meter.tick() + (bar - counted) * meter.barTicks();
            }
            counted += (int) barsHere;
            tick = until;
        }
        return tick;
    }

    /** The bar a tick lies in, counted from 0. */
    public int barOf(long tick) {
        int bars = 0;
        for (int i = 0; i < meters.size(); i++) {
            Meter meter = meters.get(i);
            long until = i + 1 < meters.size() ? meters.get(i + 1).tick() : Long.MAX_VALUE;
            if (tick < until) {
                return bars + (int) ((tick - meter.tick()) / meter.barTicks());
            }
            bars += (int) ((until - meter.tick()) / meter.barTicks());
        }
        return bars;
    }

    /** The number of whole bars that cover the time up to this tick: at least one. */
    public int barsCovering(long endTick) {
        if (endTick <= 0) {
            return 1;
        }
        int bar = barOf(endTick - 1);
        return bar + 1;
    }

    /** The first tick at or after a time in seconds from the start of the piece. */
    public long tickAt(double seconds) {
        if (seconds <= 0) {
            return 0;
        }
        double passed = 0;
        for (int i = 0; i < tempos.size(); i++) {
            Tempo tempo = tempos.get(i);
            boolean last = i + 1 == tempos.size();
            double ticksPerSecond = PPQ * tempo.bpm() / 60.0;
            double length = last ? Double.MAX_VALUE : tempo.glide() ? stretch(i, tempos.get(i + 1).tick())
                    : (tempos.get(i + 1).tick() - tempo.tick()) / ticksPerSecond;
            if (seconds - passed <= length) {
                return tempo.tick() + (long) Math.ceil(ticksInto(i, seconds - passed) - 1e-9);
            }
            passed += length;
        }
        return 0;
    }

    /** The time from the start of the piece to a tick, in seconds. */
    public double seconds(long tick) {
        double seconds = 0;
        for (int i = 0; i < tempos.size(); i++) {
            Tempo tempo = tempos.get(i);
            long until = i + 1 < tempos.size() ? Math.min(tick, tempos.get(i + 1).tick()) : tick;
            if (until <= tempo.tick()) {
                break;
            }
            seconds += stretch(i, until);
        }
        return seconds;
    }

    /** The seconds from tempo event {@code i} to a tick up to the next event. */
    private double stretch(int i, long until) {
        Tempo tempo = tempos.get(i);
        long ticks = until - tempo.tick();
        if (!tempo.glide()) {
            return ticks / (double) PPQ * 60.0 / tempo.bpm();
        }
        Tempo next = tempos.get(i + 1);
        double b0 = tempo.bpm();
        double rise = next.bpm() - b0;
        long span = next.tick() - tempo.tick();
        if (Math.abs(rise) < 1e-12) {
            return ticks / (double) PPQ * 60.0 / b0;
        }
        // The tempo grows evenly per tick: b(x) = b0 + rise * x / span; the time is the integral of 60 / (PPQ * b).
        double reached = b0 + rise * ticks / span;
        return 60.0 / PPQ * span / rise * Math.log(reached / b0);
    }

    /** The ticks after tempo event {@code i} at which this many seconds have passed. */
    private double ticksInto(int i, double seconds) {
        Tempo tempo = tempos.get(i);
        if (!tempo.glide()) {
            return seconds * (PPQ * tempo.bpm() / 60.0);
        }
        Tempo next = tempos.get(i + 1);
        double b0 = tempo.bpm();
        double rise = next.bpm() - b0;
        long span = next.tick() - tempo.tick();
        if (Math.abs(rise) < 1e-12) {
            return seconds * PPQ * b0 / 60.0;
        }
        double reached = b0 * Math.exp(seconds * PPQ * rise / (60.0 * span));
        return (reached - b0) * span / rise;
    }

    private int indexAt(long tick) {
        int found = 0;
        for (int i = 0; i < tempos.size(); i++) {
            if (tempos.get(i).tick() <= tick) {
                found = i;
            }
        }
        return found;
    }

    private static <T extends Record> List<T> ordered(List<T> events, String what) {
        if (events == null || events.isEmpty()) {
            throw new IllegalArgumentException("A time map needs at least one " + what + " event.");
        }
        List<T> copy = List.copyOf(events);
        long before = -1;
        for (T event : copy) {
            long tick = tick(event);
            if (before < 0 && tick != 0) {
                throw new IllegalArgumentException("The first " + what + " event is at tick 0.");
            }
            if (tick <= before) {
                throw new IllegalArgumentException("The " + what + " events are in order, one per tick.");
            }
            before = tick;
        }
        return copy;
    }

    private static long tick(Record event) {
        if (event instanceof Tempo t) {
            return t.tick();
        }
        if (event instanceof Meter m) {
            return m.tick();
        }
        return ((KeyChange) event).tick();
    }

    private static <T extends Record> T at(List<T> events, long tick) {
        T found = events.get(0);
        for (T event : events) {
            if (tick(event) <= tick) {
                found = event;
            }
        }
        return found;
    }
}
