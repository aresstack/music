package com.aresstack.music.model.edit;

import com.aresstack.music.model.Clip;
import com.aresstack.music.model.Composition;
import com.aresstack.music.model.MidiClip;
import com.aresstack.music.model.Part;
import com.aresstack.music.model.Track;
import java.util.HashSet;
import java.util.Set;

/**
 * How far a scope reaches into a piece: which objects it targets, judged against the piece as it
 * was before the change, so that objects the change creates can be told from those it found.
 */
final class Reach {
    private final EditScope scope;
    private final Set<String> knownParts = new HashSet<>();
    private final Set<String> knownTracks = new HashSet<>();
    private final Set<String> knownAssets = new HashSet<>();
    private final com.aresstack.music.model.TimeMap timeMap;

    Reach(EditScope scope, Composition before) {
        this.scope = scope;
        this.timeMap = before.timeMap();
        for (com.aresstack.music.model.Asset asset : before.assets()) {
            knownAssets.add(asset.id());
        }
        for (Part part : before.parts()) {
            knownParts.add(part.id());
        }
        for (Track track : before.tracks()) {
            knownTracks.add(track.id());
        }
    }

    EditScope scope() {
        return scope;
    }

    boolean newPart(String partId) {
        return partId != null && !knownParts.contains(partId);
    }

    /** Whether an asset is one the change brings in, as the scope allows. */
    boolean newAssetAllowed(String assetId) {
        return !knownAssets.contains(assetId) && scope.allows(EditOp.Kind.ADD_ASSET);
    }

    boolean newTrack(String trackId) {
        return !knownTracks.contains(trackId);
    }

    /** Whether the settings of a part may change or the part may be created. */
    boolean partInScope(String partId) {
        return newPart(partId) ? scope.allows(EditOp.Kind.ADD_PART)
                : (scope.anyObject() || scope.partIds().contains(partId)) && scope.allows(EditOp.Kind.SET_PART);
    }

    /** Whether the scope names a part as a target (or the change creates it, as allowed). */
    boolean targetsPart(String partId) {
        if (partId == null) {
            return scope.anyObject();
        }
        return scope.anyObject() || scope.partIds().contains(partId)
                || (newPart(partId) && scope.allows(EditOp.Kind.ADD_PART));
    }

    /** Whether a clip on a track is a target. */
    boolean targetsClip(Track track, Clip clip) {
        if (scope.anyObject() || scope.clipIds().contains(clip.id()) || scope.trackIds().contains(track.id())) {
            return true;
        }
        if (newTrack(track.id()) && scope.allows(EditOp.Kind.ADD_TRACK)) {
            return true;
        }
        if (clip instanceof MidiClip midi) {
            String partId = track.partOf(midi);
            return partId != null && targetsPart(partId);
        }
        return false;
    }

    /** Whether a clip may be created, removed or moved here: a target, lying wholly in the scope's time. */
    boolean clipInScope(Track track, Clip clip) {
        return targetsClip(track, clip) && scope.covers(clip.startTick(), end(clip));
    }

    /**
     * Whether the end of a clip that lies partly outside the scope's time belongs to the scope:
     * the clip is a target, the scope allows {@link EditOp.Kind#RESIZE_CLIP}, and the clip ends
     * within the scope's time. Then its end may move within that time; its start may not.
     */
    boolean endInScope(Track track, Clip clip) {
        return clip instanceof MidiClip midi && scope.allows(EditOp.Kind.RESIZE_CLIP) && targetsClip(track, clip)
                && midi.endTick() >= scope.editFrom() && midi.endTick() <= scope.toTick();
    }

    /** Whether a note that begins at a tick of the piece, in a clip on a track, may change. */
    boolean noteInScope(Track track, Clip clip, long tick) {
        return targetsClip(track, clip) && scope.begins(tick);
    }

    /** Where a clip ends in ticks; an audio clip's length in samples is measured on the time map. */
    long end(Clip clip) {
        if (clip instanceof MidiClip midi) {
            return midi.endTick();
        }
        return ((com.aresstack.music.model.AudioClip) clip).endTick(timeMap);
    }
}
