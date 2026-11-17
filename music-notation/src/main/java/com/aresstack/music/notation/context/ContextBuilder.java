package com.aresstack.music.notation.context;

import com.aresstack.music.model.Composition;
import com.aresstack.music.model.Composition.PlacedNote;
import com.aresstack.music.model.Key;
import com.aresstack.music.model.Meta;
import com.aresstack.music.model.Part;
import com.aresstack.music.model.TimeMap;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;

/**
 * Builds the {@link MusicalContext} of a piece: a pure function of the piece and the window,
 * independent of any model (architecture review 2.4, E4). Four layers:
 * <ol>
 * <li>the profile of the piece: title, genre, mood, brief, tempo, meter, key, length;</li>
 * <li>the form: every section with its bars, function, intensity, chords and instruction;</li>
 * <li>a summary of every part: instrument, role, range, notes per bar over the whole piece;</li>
 * <li>the notes of every part in the window, in full, with notes that sound into it from before
 *     marked as held.</li>
 * </ol>
 * The text is the same for the same piece and window, byte for byte: everything is in the order
 * of the model or sorted, numbers are written with {@link Locale#ROOT}, nothing depends on the
 * clock or on hash order.
 */
public final class ContextBuilder {
    private static final String[] NAMES = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
    private static final Map<Integer, String> DRUMS = Map.ofEntries(Map.entry(35, "kick2"), Map.entry(36, "kick"),
            Map.entry(37, "stick"), Map.entry(38, "snare"), Map.entry(39, "clap"), Map.entry(40, "snare2"),
            Map.entry(41, "tom-low2"), Map.entry(42, "hihat-closed"), Map.entry(43, "tom-low"),
            Map.entry(44, "hihat-pedal"), Map.entry(45, "tom-mid2"), Map.entry(46, "hihat-open"),
            Map.entry(47, "tom-mid"), Map.entry(48, "tom-high2"), Map.entry(49, "crash"), Map.entry(50, "tom-high"),
            Map.entry(51, "ride"), Map.entry(52, "china"), Map.entry(53, "ride-bell"), Map.entry(54, "tambourine"),
            Map.entry(55, "splash"), Map.entry(56, "cowbell"), Map.entry(57, "crash2"), Map.entry(59, "ride2"));

    private ContextBuilder() {
    }

    /**
     * A region that is to be written anew ({@code REPLACE_REGION}, {@code VARIATE_REGION}): the
     * notes it selects are left out of the fourth layer, which names the region in their place;
     * notes of its parts that begin before it and sound into it are marked as held into it.
     *
     * @param partIds  the parts whose notes in the region are written anew
     * @param rewritten which notes those are
     */
    public record Region(long fromTick, long toTick, Set<String> partIds, Predicate<PlacedNote> rewritten) {
        public Region {
            partIds = Set.copyOf(partIds);
            if (fromTick < 0 || toTick <= fromTick) {
                throw new IllegalArgumentException("A region covers at least one tick: " + fromTick + ".." + toTick);
            }
        }
    }

    /**
     * The context of a piece for a change within {@code fromTick..toTick}, with the notes of
     * {@code aroundBars} bars before and after given in full as well.
     *
     * @param highlightPartId a part to mark as the source of motifs, or {@code null}
     */
    public static MusicalContext build(Composition piece, long fromTick, long toTick, int aroundBars,
                                       String highlightPartId) {
        return build(piece, fromTick, toTick, aroundBars, highlightPartId, null);
    }

    /**
     * The context of a piece for writing a region anew: the window is the region with
     * {@code aroundBars} bars before <em>and</em> after it, so that the model sees where the
     * region comes from and where it must lead to; the notes to be rewritten are not shown.
     */
    public static MusicalContext build(Composition piece, Region region, int aroundBars) {
        return build(piece, region.fromTick(), region.toTick(), aroundBars, null, region);
    }

    private static MusicalContext build(Composition piece, long fromTick, long toTick, int aroundBars,
                                        String highlightPartId, Region region) {
        if (fromTick < 0 || toTick <= fromTick || aroundBars < 0) {
            throw new IllegalArgumentException("A window covers at least one tick: " + fromTick + ".." + toTick);
        }
        TimeMap time = piece.timeMap();
        int pieceBars = time.barsCovering(Math.max(piece.endTick(), 1));
        int firstBar = Math.max(0, time.barOf(fromTick) - aroundBars);
        int endBar = Math.min(pieceBars, time.barsCovering(toTick) + aroundBars);
        long windowFrom = time.tickOfBar(firstBar);
        long windowTo = Math.max(windowFrom + 1, time.tickOfBar(Math.max(endBar, firstBar + 1)));

        StringBuilder text = new StringBuilder();
        profile(text, piece, pieceBars);
        form(text, piece);
        parts(text, piece, pieceBars, highlightPartId, 1, 0);
        notes(text, piece, windowFrom, windowTo, highlightPartId, region);
        String written = text.toString();
        return new MusicalContext(written, sha256(written), windowFrom, windowTo);
    }

    /**
     * The context of a piece told in tiers ({@link ContextTiers}): profile, form and part summaries
     * as always, then the notes of each window in full, each under its label. Same piece and same
     * tiers give the same bytes.
     */
    public static MusicalContext build(Composition piece, ContextTiers tiers, String highlightPartId) {
        return build(piece, tiers, highlightPartId, 0);
    }

    /** Create the context with an explicit history window; zero preserves complete summaries. */
    public static MusicalContext build(Composition piece, ContextTiers tiers, String highlightPartId,
                                       int lookBackBars) {
        if (lookBackBars < 0) {
            throw new IllegalArgumentException("Look-back bars must not be negative.");
        }
        TimeMap time = piece.timeMap();
        int pieceBars = time.barsCovering(Math.max(piece.endTick(), 1));
        StringBuilder text = new StringBuilder();
        profile(text, piece, pieceBars);
        // A long piece: form and summaries from here on only, so that the context stays as long as it is
        // however far the piece has grown (bar counted from 0; 0 tells everything).
        int back = lookBackBars; // Zero keeps the complete history in the summaries.
        int near = tiers.compact() && back > 0 && pieceBars > back
                ? (pieceBars - back) / ContextTiers.COMPACT_BARS * ContextTiers.COMPACT_BARS : 0;
        form(text, piece, near);
        parts(text, piece, pieceBars, highlightPartId, tiers.compact() ? ContextTiers.COMPACT_BARS : 1, near);
        for (ContextTiers.Window window : tiers.windows()) {
            if (window.label() != null) {
                text.append("# ").append(window.label().toUpperCase(Locale.ROOT)).append('\n');
            }
            notes(text, piece, window.fromTick(), window.toTick(), highlightPartId, null);
        }
        String written = text.toString();
        ContextTiers.Window end = tiers.end();
        return new MusicalContext(written, sha256(written), end.fromTick(), end.toTick());
    }

    private static void profile(StringBuilder text, Composition piece, int bars) {
        Meta meta = piece.meta();
        TimeMap time = piece.timeMap();
        text.append("# PIECE\n");
        line(text, "title", meta.title());
        line(text, "genre", meta.genre());
        line(text, "mood", meta.mood());
        line(text, "brief", meta.brief());
        line(text, "remarks", meta.remarks());
        text.append("tempo: ");
        join(text, time.tempos(), t -> "bar " + (time.barOf(t.tick()) + 1) + " " + number(t.bpm()) + " BPM"
                + (t.glide() ? " gliding to the next tempo" : ""));
        text.append("\nmeter: ");
        join(text, time.meters(), m -> "bar " + (time.barOf(m.tick()) + 1) + " " + m.numerator() + "/" + m.denominator());
        text.append("\nkey: ");
        join(text, time.keys(), k -> "bar " + (time.barOf(k.tick()) + 1) + " " + key(k.key()));
        // Only a piece with dynamics says them, so that the context of every other piece stays as it was.
        if (!piece.dynamics().isEmpty()) {
            text.append("\ndynamics: ").append(dynamicsLine(piece));
        }
        text.append("\nlength: ").append(bars).append(" bars\n");
        text.append("time: bar counts from 1 at the start of the piece; beat is the offset within the bar in "
                + "quarter notes, from 0; dur is in quarter notes; pitch is the MIDI number with its name "
                + "(60 = C4); vel is the MIDI velocity.\n\n");
    }

    private static void form(StringBuilder text, Composition piece) {
        form(text, piece, 0);
    }

    /** The form; sections that end at or before bar {@code near} (counted from 0) are told in one line together. */
    private static void form(StringBuilder text, Composition piece, int near) {
        TimeMap time = piece.timeMap();
        text.append("# FORM\n");
        if (piece.sections().isEmpty()) {
            text.append("(no sections)\n\n");
            return;
        }
        int left = 0;
        int leftUntil = 0;
        for (var section : piece.sections()) {
            if (near > 0 && time.barsCovering(section.endTick()) <= near) {
                left++;
                leftUntil = Math.max(leftUntil, time.barsCovering(section.endTick()));
            }
        }
        if (left > 0) {
            text.append("(bars 1-").append(leftUntil).append(": ").append(left)
                    .append(" earlier sections, not listed; the piece is long)\n");
        }
        for (var section : piece.sections()) {
            int first = time.barOf(section.startTick()) + 1;
            int last = time.barsCovering(section.endTick());
            if (near > 0 && last <= near) {
                continue;
            }
            text.append("section ").append(section.id()).append(" \"").append(clean(section.name())).append("\" bars ")
                    .append(first).append('-').append(last);
            if (section.function() != null) {
                text.append(" function=").append(clean(section.function()));
            }
            if (section.intensity() != null) {
                text.append(" intensity=").append(number(section.intensity()));
            }
            text.append('\n');
            if (!section.harmony().isEmpty()) {
                text.append("  chords by bar: ").append(String.join(" | ", section.harmony())).append('\n');
            }
            if (section.instruction() != null && !section.instruction().isBlank()) {
                text.append("  instruction: ").append(clean(section.instruction())).append('\n');
            }
        }
        text.append('\n');
    }

    private static void parts(StringBuilder text, Composition piece, int bars, String highlight, int group,
                              int near) {
        TimeMap time = piece.timeMap();
        text.append("# PARTS\n");
        for (Part part : piece.parts()) {
            List<PlacedNote> notes = piece.notesOf(part.id());
            text.append("part ").append(part.id()).append(" \"").append(clean(part.name())).append("\" instrument=")
                    .append(clean(part.instrument() == null ? part.name() : part.instrument())).append(" role=")
                    .append(part.role() == null ? "-" : part.role()).append(" program=").append(part.program());
            if (part.id().equals(highlight)) {
                text.append(" MOTIF-SOURCE");
            }
            text.append('\n');
            if (notes.isEmpty()) {
                text.append("  silent\n");
                continue;
            }
            if (part.drums()) {
                TreeMap<Integer, Integer> used = new TreeMap<>();
                for (PlacedNote n : notes) {
                    used.merge(n.note().pitch(), 1, Integer::sum);
                }
                text.append("  sounds:");
                used.forEach((pitch, count) -> text.append(' ').append(drum(pitch)).append('x').append(count));
                text.append('\n');
            } else {
                int low = notes.stream().mapToInt(n -> n.note().pitch()).min().orElse(0);
                int high = notes.stream().mapToInt(n -> n.note().pitch()).max().orElse(0);
                text.append("  range: ").append(pitch(low)).append('-').append(pitch(high)).append('\n');
            }
            int[] perBar = new int[(bars + group - 1) / group];
            for (PlacedNote n : notes) {
                int bar = time.barOf(n.tick());
                if (bar < bars) {
                    perBar[bar / group]++;
                }
            }
            text.append(group == 1 ? "  notes per bar" : "  notes per " + group + " bars")
                    .append(near > 0 ? " from bar " + (near + 1) + ":" : ":");
            for (int i = near / group; i < perBar.length; i++) {
                text.append(' ').append(perBar[i]);
            }
            text.append('\n');
        }
        text.append('\n');
    }

    private static void notes(StringBuilder text, Composition piece, long from, long to, String highlight,
                              Region region) {
        TimeMap time = piece.timeMap();
        text.append("# NOTES bars ").append(time.barOf(from) + 1).append('-').append(time.barsCovering(to))
                .append(" (bar beat dur pitch vel)\n");
        for (Part part : piece.parts()) {
            List<PlacedNote> inWindow = new ArrayList<>();
            int rewritten = 0;
            for (PlacedNote n : piece.notesOf(part.id())) {
                if (region != null && region.rewritten().test(n)) {
                    rewritten++;
                } else if (n.tick() < to && n.endTick() > from) {
                    inWindow.add(n);
                }
            }
            inWindow.sort(Comparator.comparingLong(PlacedNote::tick).thenComparingInt(n -> n.note().pitch())
                    .thenComparingLong(n -> n.note().length()).thenComparingInt(n -> n.note().velocity()));
            text.append("part ").append(part.id());
            if (part.id().equals(highlight)) {
                text.append(" MOTIF-SOURCE");
            }
            text.append('\n');
            boolean target = region != null && region.partIds().contains(part.id());
            if (target) {
                text.append("  REGION bars ").append(time.barOf(region.fromTick()) + 1).append('-')
                        .append(time.barsCovering(region.toTick())).append(": ").append(rewritten)
                        .append(" notes to be written anew, not shown\n");
            }
            if (inWindow.isEmpty()) {
                text.append("  rests\n");
                continue;
            }
            for (PlacedNote n : inWindow) {
                int bar = time.barOf(n.tick());
                long barStart = time.tickOfBar(bar);
                boolean heldIn = target && n.tick() < region.fromTick() && n.endTick() > region.fromTick();
                text.append(n.tick() < from ? "  held " : heldIn ? "  held-into-region " : "  ").append(bar + 1).append(' ')
                        .append(quarters(n.tick() - barStart)).append(' ').append(quarters(n.note().length())).append(' ')
                        .append(part.drums() ? drum(n.note().pitch()) : pitch(n.note().pitch())).append('/')
                        .append(n.note().pitch()).append(' ').append(n.note().velocity())
                        .append(n.note().articulation() == null ? "" : " " + n.note().articulation().token()).append('\n');
            }
        }
    }

    /**
     * The dynamic marks and hairpins of a piece in words, for example
     * {@code "bar 1 p; bars 5-8 crescendo to f (bass); bar 9 ff"}.
     */
    public static String dynamicsLine(Composition piece) {
        TimeMap time = piece.timeMap();
        com.aresstack.music.model.Dynamics dynamics = piece.dynamics();
        java.util.TreeMap<Long, java.util.List<String>> said = new java.util.TreeMap<>();
        for (com.aresstack.music.model.Dynamics.Mark mark : dynamics.marks()) {
            said.computeIfAbsent(mark.tick(), k -> new java.util.ArrayList<>()).add(at(time, mark.tick()) + " "
                    + mark.level().symbol() + (mark.partId() == null ? "" : " (" + mark.partId() + ")"));
        }
        for (com.aresstack.music.model.Dynamics.Hairpin pin : dynamics.hairpins()) {
            said.computeIfAbsent(pin.startTick(), k -> new java.util.ArrayList<>()).add(at(time, pin.startTick())
                    + " to " + at(time, pin.endTick()) + " " + (dynamics.swells(pin) ? "crescendo" : "decrescendo")
                    + " to " + pin.target().symbol() + (pin.partId() == null ? "" : " (" + pin.partId() + ")"));
        }
        java.util.List<String> all = new java.util.ArrayList<>();
        said.values().forEach(all::addAll);
        return String.join("; ", all);
    }

    /** {@code "bar 5"} at the start of a bar, else {@code "bar 5 beat 2.5"} (beats from 0 as in the notes). */
    private static String at(TimeMap time, long tick) {
        int bar = time.barOf(tick);
        long into = tick - time.tickOfBar(bar);
        return "bar " + (bar + 1) + (into == 0 ? "" : " beat " + quarters(into));
    }

    /** A MIDI pitch by name, 60 = C4, sharps only. */
    public static String pitch(int midi) {
        return NAMES[midi % 12] + (midi / 12 - 1);
    }

    /** A key in words, for example "D minor". */
    public static String key(Key key) {
        return NAMES[key.tonic()] + (key.mode() == Key.Mode.MAJOR ? " major" : " minor");
    }

    /** Ticks as quarter notes, as short as exact: 480 is "1", 120 is "0.25". */
    public static String quarters(long ticks) {
        return number(ticks / (double) TimeMap.PPQ);
    }

    static String number(double value) {
        String text = String.format(Locale.ROOT, "%.4f", value);
        text = text.replaceAll("0+$", "");
        return text.endsWith(".") ? text.substring(0, text.length() - 1) : text;
    }

    private static String drum(int pitch) {
        return DRUMS.getOrDefault(pitch, "drum" + pitch);
    }

    private static void line(StringBuilder text, String name, String value) {
        if (value != null && !value.isBlank()) {
            text.append(name).append(": ").append(clean(value)).append('\n');
        }
    }

    private static <T> void join(StringBuilder text, List<T> items, java.util.function.Function<T, String> show) {
        for (int i = 0; i < items.size(); i++) {
            text.append(i == 0 ? "" : ", ").append(show.apply(items.get(i)));
        }
    }

    /** One line, no quotes that would break the layout. */
    private static String clean(String value) {
        return value.replaceAll("\\s+", " ").replace('"', '\'').strip();
    }

    static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format(Locale.ROOT, "%02x", b & 255));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable.", impossible);
        }
    }
}
