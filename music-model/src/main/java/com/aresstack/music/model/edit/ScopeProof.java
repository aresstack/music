package com.aresstack.music.model.edit;

import com.aresstack.music.model.Asset;
import com.aresstack.music.model.AudioClip;
import com.aresstack.music.model.Clip;
import com.aresstack.music.model.Composition;
import com.aresstack.music.model.MidiClip;
import com.aresstack.music.model.Note;
import com.aresstack.music.model.Part;
import com.aresstack.music.model.Track;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * The third safeguard of the architecture review (E5, "nachweisend"): a fingerprint of
 * everything a scope does not reach. A change within its scope leaves that fingerprint as it was;
 * after an approved shift it is the fingerprint of the original <em>with the shift applied</em>
 * (see {@link TimeShift}). The fingerprint is a sorted list of entries, one per object, so that a
 * difference can be named and not only noticed.
 *
 * <p>Notes are described by their part, their tick in the piece, length, pitch, velocity and
 * voice: what sounds and how it is written, not in which clip it is stored.
 */
public final class ScopeProof {
    private ScopeProof() {
    }

    /** The hash over everything outside the scope ({@code hashOutside} of the acceptance plan, 7.1). */
    public static String hashOutside(Composition piece, EditScope scope) {
        return hashOutside(piece, scope, piece);
    }

    /**
     * The hash over everything outside the scope, where objects that {@code before} does not have
     * count as created by the change: a new part, track or clip the scope allows to create is not
     * outside. Before and after a change within the scope, with the original as {@code before},
     * the hash is the same.
     */
    public static String hashOutside(Composition piece, EditScope scope, Composition before) {
        return hash(outside(piece, new Reach(scope, before)));
    }

    /**
     * What differs outside the scope between the reference (the original, with approved shifts
     * applied) and the result of a change, one line per object; empty if nothing does.
     */
    public static List<String> differences(Composition reference, Composition result, EditScope scope) {
        Reach reach = new Reach(scope, reference);
        SortedMap<String, String> before = outside(reference, reach);
        SortedMap<String, String> after = outside(result, reach);
        List<String> found = new ArrayList<>();
        for (Map.Entry<String, String> entry : before.entrySet()) {
            String now = after.get(entry.getKey());
            if (now == null) {
                found.add(entry.getKey() + " removed");
            } else if (!now.equals(entry.getValue())) {
                found.add(entry.getKey() + " changed from [" + entry.getValue() + "] to [" + now + "]");
            }
        }
        for (Map.Entry<String, String> entry : after.entrySet()) {
            if (!before.containsKey(entry.getKey())) {
                found.add(entry.getKey() + " added [" + entry.getValue() + "]");
            }
        }
        return found;
    }

    static SortedMap<String, String> outside(Composition piece, Reach reach) {
        EditScope scope = reach.scope();
        SortedMap<String, String> entries = new TreeMap<>();
        if (!scope.allows(EditOp.Kind.SET_META)) {
            entries.put("meta", piece.meta().toString());
        }
        if (!scope.allows(EditOp.Kind.SET_TIME_MAP)) {
            entries.put("timeMap", piece.timeMap().toString());
        }
        if (!scope.allows(EditOp.Kind.SET_DYNAMICS)) {
            entries.put("dynamics", piece.dynamics().toString());
        }
        if (!scope.allows(EditOp.Kind.SET_SECTIONS)) {
            entries.put("sections", piece.sections().toString());
        }
        List<String> partOrder = new ArrayList<>();
        for (Part part : piece.parts()) {
            if (!reach.newPart(part.id())) {
                partOrder.add(part.id());
            }
            if (!reach.partInScope(part.id())) {
                entries.put("part:" + part.id(), part.toString());
            }
        }
        entries.put("partOrder", partOrder.toString());
        List<String> trackOrder = new ArrayList<>();
        for (Track track : piece.tracks()) {
            boolean created = reach.newTrack(track.id());
            if (!created) {
                trackOrder.add(track.id());
            }
            if (!(created && scope.allows(EditOp.Kind.ADD_TRACK))) {
                entries.put("track:" + track.id(), track.name() + " " + track.kind() + " part=" + track.defaultPartId());
            }
            for (Clip clip : track.clips()) {
                if (!reach.clipInScope(track, clip)) {
                    entries.put("clip:" + clip.id(), "track=" + track.id() + " "
                            + (reach.endInScope(track, clip) ? frameUpTo((MidiClip) clip, scope.editFrom()) : frame(clip)));
                }
                if (clip instanceof MidiClip midi) {
                    String part = track.partOf(midi);
                    for (Note note : midi.notes()) {
                        long tick = midi.startTick() + note.tick();
                        if (!reach.noteInScope(track, clip, tick)) {
                            entries.put("note:" + note.id(), "part=" + part + " tick=" + tick + " length=" + note.length()
                                    + " pitch=" + note.pitch() + " velocity=" + note.velocity() + " voice=" + note.voiceId());
                        }
                    }
                }
            }
        }
        entries.put("trackOrder", trackOrder.toString());
        for (Asset asset : piece.assets()) {
            if (!reach.newAssetAllowed(asset.id())) {
                entries.put("asset:" + asset.id(), asset.toString());
            }
        }
        return entries;
    }

    /** The frame of a MIDI clip whose end lies in the scope: where it ends only as "from here on". */
    private static String frameUpTo(MidiClip clip, long editFrom) {
        return "midi start=" + clip.startTick() + " end>=" + editFrom + " part=" + clip.partId();
    }

    private static String frame(Clip clip) {
        if (clip instanceof MidiClip m) {
            return "midi start=" + m.startTick() + " length=" + m.lengthTicks() + " part=" + m.partId();
        }
        AudioClip a = (AudioClip) clip;
        return "audio start=" + a.startTick() + " samples=" + a.lengthSamples() + " asset=" + a.assetId() + " gain="
                + a.gainDb() + " fades=" + a.fadeInSamples() + "/" + a.fadeOutSamples();
    }

    static String hash(SortedMap<String, String> entries) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                sha.update((entry.getKey() + "=" + entry.getValue() + "\n").getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(sha.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
