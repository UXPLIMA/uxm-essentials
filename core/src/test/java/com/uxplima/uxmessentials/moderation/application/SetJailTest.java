package com.uxplima.uxmessentials.moderation.application;

import static com.uxplima.uxmessentials.testing.Fixtures.CLOCK;
import static com.uxplima.uxmessentials.testing.Fixtures.SAM;
import static com.uxplima.uxmessentials.testing.Fixtures.at;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import com.uxplima.uxmessentials.moderation.domain.StoredJail;
import com.uxplima.uxmessentials.testing.RecordingEvents;
import com.uxplima.uxmessentials.testing.RecordingNotifier;
import org.junit.jupiter.api.Test;

/** Which server a jail set with /setjail names: its own on a network, none on a standalone server. */
class SetJailTest {

    private final JailNetworkFakes.Locations locations = new JailNetworkFakes.Locations();

    private SetJail on(Optional<String> recordedServer) {
        return new SetJail(
                locations,
                new RecordingNotifier().notifier(),
                new SilentAudit(),
                new RecordingEvents(),
                CLOCK,
                recordedServer);
    }

    @Test
    void aJailSetOnANetworkedServerNamesThatServer() {
        on(Optional.of("prison")).set(SAM, "Cell", at(1, 64, 1));

        assertThat(locations.find("cell").flatMap(StoredJail::knownServer)).contains("prison");
    }

    @Test
    void aJailSetOnAStandaloneServerNamesNoServerSoANetworkedStartCanClaimIt() {
        on(Optional.empty()).set(SAM, "Cell", at(1, 64, 1));

        assertThat(locations.find("cell").orElseThrow().server()).isEmpty();
    }
}
