package com.aresstack.music.model.mix;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Checks what the records of a mix cannot check one by one: that ids are unique, that every
 * channel belongs to someone only once, and that every automation lane moves a parameter the
 * studio knows, of a channel that is there, and no parameter twice. As with the composition,
 * these are rules of this validator, not of the format: a file may hold what they forbid.
 */
public final class MixValidator {
    /** One thing that is wrong, with the id of the object it concerns. */
    public record Violation(String objectId, String message) {
        @Override
        public String toString() {
            return objectId + ": " + message;
        }
    }

    private MixValidator() {
    }

    /** Everything that is wrong, in the order of the mix; empty if nothing is. */
    public static List<Violation> validate(MixScene mix) {
        List<Violation> found = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        Set<MixChannel.Owner> owners = new HashSet<>();
        Set<String> channels = new HashSet<>();
        for (MixChannel channel : mix.channels()) {
            if (!ids.add(channel.id())) {
                found.add(new Violation(channel.id(), "id used twice"));
            }
            if (!owners.add(channel.owner())) {
                found.add(new Violation(channel.id(), "a second channel of " + channel.owner().id()));
            }
            channels.add(channel.id());
        }
        Set<String> moved = new HashSet<>();
        for (AutomationLane lane : mix.automation()) {
            if (!ids.add(lane.id())) {
                found.add(new Violation(lane.id(), "id used twice"));
            }
            String target = lane.channelId() == null ? "master" : lane.channelId();
            if (!lane.known()) {
                found.add(new Violation(lane.id(), "unknown parameter " + lane.parameter()));
            } else if (lane.channelId() == null && !AutomationLane.LEVEL.equals(lane.parameter())) {
                found.add(new Violation(lane.id(), "the master has a level only, not " + lane.parameter()));
            }
            if (lane.channelId() != null && !channels.contains(lane.channelId())) {
                found.add(new Violation(lane.id(), "unknown channel " + lane.channelId()));
            }
            if (!moved.add(target + "\u0000" + lane.parameter())) {
                found.add(new Violation(lane.id(), "a second lane for " + lane.parameter() + " of " + target));
            }
        }
        return found;
    }
}
