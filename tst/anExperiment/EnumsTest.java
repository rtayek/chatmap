package anExperiment;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EnumsTest {

    @Test
    void channelsForReturnsCorrectChannels() {
        assertEquals(EnumSet.of(Enums.Channel.c1, Enums.Channel.c2), Enums.ModelTarget.channelsFor(Enums.Model.m1));
    }

    @Test
    void sourceIsDerivedFromChannel() {
        assertEquals(Enums.Channel.c1.source(), Enums.ModelTarget.m1c1.source());
    }

    @Test
    void guardLogicEnsuresNoDuplicatesExist() {
        long distinctPairs = java.util.Arrays.stream(Enums.ModelTarget.values())
                .map(target -> target.model.name() + ":" + target.channel.name())
                .distinct()
                .count();
        assertEquals(distinctPairs, Enums.ModelTarget.values().length,
                "ModelTarget values contain duplicate (model, channel) pairs");
    }
}
