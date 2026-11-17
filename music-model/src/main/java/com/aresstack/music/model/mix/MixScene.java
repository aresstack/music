package com.aresstack.music.model.mix;

import java.util.List;
import java.util.Objects;

/**
 * The mix of a piece: the room, the master level, how the level is set, a channel for each
 * part or audio track, and the automation of channels over time.
 *
 * @param masterDb how far below full level the finished mix is, 0 or less
 */
public record MixScene(Room room, double masterDb, LevelMode levelMode, List<MixChannel> channels,
                       List<AutomationLane> automation) {
    public MixScene {
        if (room == null || levelMode == null) {
            throw new IllegalArgumentException("A mix has a room and a level mode.");
        }
        if (!(masterDb <= 0) || Double.isInfinite(masterDb)) {
            throw new IllegalArgumentException("The master level is 0 dB or less: " + masterDb);
        }
        channels = List.copyOf(channels == null ? List.of() : channels);
        automation = List.copyOf(automation == null ? List.of() : automation);
    }

    /** The channel a part or audio track owns, or {@code null}. */
    public MixChannel channelOf(MixChannel.Owner owner) {
        for (MixChannel channel : channels) {
            if (channel.owner().equals(owner)) {
                return channel;
            }
        }
        return null;
    }

    /**
     * The lane that moves a parameter of a channel ({@code null} for the master), or {@code null}.
     * If a file holds more than one, the first counts; the {@link MixValidator} names the others.
     */
    public AutomationLane lane(String channelId, String parameter) {
        for (AutomationLane lane : automation) {
            if (Objects.equals(lane.channelId(), channelId) && lane.parameter().equals(parameter)) {
                return lane;
            }
        }
        return null;
    }
}
