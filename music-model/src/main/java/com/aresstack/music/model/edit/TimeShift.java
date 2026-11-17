package com.aresstack.music.model.edit;

import com.aresstack.music.model.AudioClip;
import com.aresstack.music.model.Clip;
import com.aresstack.music.model.Composition;
import com.aresstack.music.model.MidiClip;
import com.aresstack.music.model.Note;
import com.aresstack.music.model.Section;
import com.aresstack.music.model.TimeMap;
import com.aresstack.music.model.Track;
import com.aresstack.music.model.mix.AutomationLane;
import com.aresstack.music.model.mix.MixScene;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The approved shift of time for {@code INSERT} (architecture review E5) and the cutting of a
 * clip in two. Both are deterministic: the same piece and the same shift give the same result,
 * ids of new clips included, which is what lets {@link CompositionEditor} apply a shift to the
 * original by itself and compare.
 *
 * <p>What moves by a shift from tick {@code f} by {@code d} ticks:
 * <ul>
 * <li>a MIDI clip that runs across {@code f} is first cut there ({@link #split}); then every clip
 *     that starts at {@code f} or later starts {@code d} ticks later. Notes stay where they are
 *     within their clip. An audio clip that runs across {@code f} stays where it is.</li>
 * <li>every section that starts at {@code f} or later starts {@code d} later; a section that runs
 *     across {@code f} becomes {@code d} longer. A shift at the border of two sections leaves a
 *     gap that the patch fills with a new section.</li>
 * <li>every tempo, meter and key event at {@code f} or later, except those at tick 0; a gliding
 *     tempo keeps gliding.</li>
 * <li>every dynamic mark at {@code f} or later, except those at tick 0; a hairpin that starts
 *     there moves, one that runs across {@code f} becomes {@code d} longer.</li>
 * <li>in the mix ({@link #shift(MixScene, long, long)}), every automation point at {@code f} or later.</li>
 * </ul>
 */
public final class TimeShift {
    private TimeShift() {
    }

    public static Composition shift(Composition piece, long fromTick, long ticks) {
        Set<String> clipIds = clipIds(piece);
        List<Track> tracks = new ArrayList<>();
        for (Track track : piece.tracks()) {
            List<Clip> clips = new ArrayList<>();
            for (Clip clip : track.clips()) {
                if (clip instanceof MidiClip midi && midi.startTick() < fromTick && fromTick < midi.endTick()) {
                    MidiClip[] halves = split(midi, fromTick, derivedId(midi.id(), clipIds));
                    clipIds.add(halves[1].id());
                    clips.add(halves[0]);
                    clips.add(moved(halves[1], halves[1].startTick() + ticks));
                } else if (clip.startTick() >= fromTick) {
                    clips.add(moved(clip, clip.startTick() + ticks));
                } else {
                    clips.add(clip);
                }
            }
            tracks.add(track.withClips(clips));
        }
        List<Section> sections = new ArrayList<>();
        for (Section s : piece.sections()) {
            if (s.startTick() >= fromTick) {
                sections.add(new Section(s.id(), s.name(), s.function(), s.startTick() + ticks, s.lengthTicks(),
                        s.intensity(), s.harmony(), s.instruction()));
            } else if (fromTick < s.endTick()) {
                sections.add(new Section(s.id(), s.name(), s.function(), s.startTick(), s.lengthTicks() + ticks,
                        s.intensity(), s.harmony(), s.instruction()));
            } else {
                sections.add(s);
            }
        }
        return new Composition(piece.meta(), shift(piece.timeMap(), fromTick, ticks), sections, piece.parts(), tracks,
                piece.assets(), piece.dynamics().shifted(fromTick, ticks));
    }

    public static TimeMap shift(TimeMap time, long fromTick, long ticks) {
        List<TimeMap.Tempo> tempos = new ArrayList<>();
        for (TimeMap.Tempo t : time.tempos()) {
            tempos.add(moves(t.tick(), fromTick) ? new TimeMap.Tempo(t.tick() + ticks, t.bpm(), t.glide()) : t);
        }
        List<TimeMap.Meter> meters = new ArrayList<>();
        for (TimeMap.Meter m : time.meters()) {
            meters.add(moves(m.tick(), fromTick) ? new TimeMap.Meter(m.tick() + ticks, m.numerator(), m.denominator()) : m);
        }
        List<TimeMap.KeyChange> keys = new ArrayList<>();
        for (TimeMap.KeyChange k : time.keys()) {
            keys.add(moves(k.tick(), fromTick) ? new TimeMap.KeyChange(k.tick() + ticks, k.key()) : k);
        }
        return new TimeMap(tempos, meters, keys);
    }

    /** Moves the automation points of a mix along with a shift of its piece. */
    public static MixScene shift(MixScene mix, long fromTick, long ticks) {
        List<AutomationLane> lanes = new ArrayList<>();
        for (AutomationLane lane : mix.automation()) {
            List<AutomationLane.Point> points = new ArrayList<>();
            for (AutomationLane.Point point : lane.points()) {
                points.add(point.tick() >= fromTick ? new AutomationLane.Point(point.tick() + ticks, point.value()) : point);
            }
            lanes.add(new AutomationLane(lane.id(), lane.channelId(), lane.parameter(), points));
        }
        return new MixScene(mix.room(), mix.masterDb(), mix.levelMode(), mix.channels(), lanes);
    }

    /**
     * Cuts a MIDI clip in two at a tick of the piece, strictly within the clip. Notes that begin
     * at the cut or later go to the second clip, counted from its start; a note that sounds
     * across the cut ends at it.
     */
    public static MidiClip[] split(MidiClip clip, long atTick, String newClipId) {
        if (atTick <= clip.startTick() || atTick >= clip.endTick()) {
            throw new IllegalArgumentException("A clip is cut strictly within itself: " + clip.id() + " at " + atTick);
        }
        long cut = atTick - clip.startTick();
        List<Note> first = new ArrayList<>();
        List<Note> second = new ArrayList<>();
        for (Note n : clip.notes()) {
            if (n.tick() >= cut) {
                second.add(new Note(n.id(), n.tick() - cut, n.length(), n.pitch(), n.velocity(), n.voiceId(), n.articulation()));
            } else if (n.endTick() > cut) {
                first.add(new Note(n.id(), n.tick(), cut - n.tick(), n.pitch(), n.velocity(), n.voiceId(), n.articulation()));
            } else {
                first.add(n);
            }
        }
        return new MidiClip[] {
                new MidiClip(clip.id(), clip.startTick(), cut, clip.partId(), first),
                new MidiClip(newClipId, atTick, clip.lengthTicks() - cut, clip.partId(), second)};
    }

    /** A clip at another start, otherwise the same. */
    static Clip moved(Clip clip, long startTick) {
        if (clip instanceof MidiClip m) {
            return new MidiClip(m.id(), startTick, m.lengthTicks(), m.partId(), m.notes());
        }
        AudioClip a = (AudioClip) clip;
        return new AudioClip(a.id(), startTick, a.lengthSamples(), a.assetId(), a.gainDb(), a.fadeInSamples(),
                a.fadeOutSamples());
    }

    /** The id of the second half of a clip cut by a shift: the clip's id with the first free number. */
    static String derivedId(String id, Set<String> taken) {
        String stem = id.length() > 56 ? id.substring(0, 56) : id;
        for (int n = 2; ; n++) {
            String candidate = stem + "-" + n;
            if (!taken.contains(candidate)) {
                return candidate;
            }
        }
    }

    static Set<String> clipIds(Composition piece) {
        Set<String> ids = new HashSet<>();
        for (Track track : piece.tracks()) {
            for (Clip clip : track.clips()) {
                ids.add(clip.id());
            }
        }
        return ids;
    }

    private static boolean moves(long tick, long fromTick) {
        return tick > 0 && tick >= fromTick;
    }
}
