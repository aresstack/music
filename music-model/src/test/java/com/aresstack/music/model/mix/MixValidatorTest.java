package com.aresstack.music.model.mix;

import static org.junit.Assert.assertEquals;

import java.util.List;
import org.junit.Test;

/** Work package D3: what a mix may hold, beyond what each record checks. */
public final class MixValidatorTest {
    private static final MixChannel MELODY = new MixChannel("melody-ch", MixChannel.Owner.part("melody"), 0, 0.3, 0, 1,
            0.3, false, false);
    private static final MixChannel BASS = new MixChannel("bass-ch", MixChannel.Owner.part("bass"), 0, 0.5, -2, 0.4,
            0.2, false, false);

    private static AutomationLane lane(String id, String channel, String parameter) {
        return new AutomationLane(id, channel, parameter, List.of(new AutomationLane.Point(0, 0)));
    }

    private static List<String> problems(List<MixChannel> channels, AutomationLane... lanes) {
        MixScene mix = new MixScene(new Room("hall", 2, 0.3), 0, LevelMode.FIXED, channels, List.of(lanes));
        return MixValidator.validate(mix).stream().map(MixValidator.Violation::toString).toList();
    }

    @Test
    public void aMovingSourceWithLevelAndRoomShareIsAllowed() {
        assertEquals(List.of(), problems(List.of(MELODY, BASS),
                lane("l1", "melody-ch", AutomationLane.LEVEL), lane("l2", "melody-ch", AutomationLane.X),
                lane("l3", "melody-ch", AutomationLane.DEPTH), lane("l4", "melody-ch", AutomationLane.REVERB),
                lane("l5", "bass-ch", AutomationLane.LEVEL), lane("l6", null, AutomationLane.LEVEL)));
    }

    @Test
    public void everyLaneMovesOneKnownParameterOfAChannelThatIsThere() {
        assertEquals(List.of(
                        "l2: a second lane for x of melody-ch",
                        "l3: unknown channel gone-ch",
                        "l4: unknown parameter brightness",
                        "l5: the master has a level only, not x",
                        "l6: id used twice"),
                problems(List.of(MELODY),
                        lane("l1", "melody-ch", AutomationLane.X), lane("l2", "melody-ch", AutomationLane.X),
                        lane("l3", "gone-ch", AutomationLane.LEVEL), lane("l4", "melody-ch", "brightness"),
                        lane("l5", null, AutomationLane.X), lane("l6", "melody-ch", AutomationLane.LEVEL),
                        lane("l6", "melody-ch", AutomationLane.REVERB)));
    }

    @Test
    public void aPartHasOneChannel() {
        MixChannel again = new MixChannel("melody-2", MixChannel.Owner.part("melody"), 0.5, 0.3, 0, 1, 0.3, false,
                false);
        assertEquals(List.of("melody-2: a second channel of melody"), problems(List.of(MELODY, again)));
    }
}
