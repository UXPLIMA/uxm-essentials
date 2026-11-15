package com.uxplima.uxmessentials.shared.application.hop;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.application.hop.port.PendingHop;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.message.MessageKey;
import com.uxplima.uxmessentials.testing.RecordingLogger;
import com.uxplima.uxmessentials.testing.RecordingNotifier;
import org.junit.jupiter.api.Test;

/** A kind that hears about its failed hops, and one whose hearing throws. */
class HopFailureReportTest {

    private final RecordingNotifier notifier = new RecordingNotifier();
    private final RecordingLogger log = new RecordingLogger();
    private final HopCharges charges = new HopCharges(notifier.notifier(), log);

    @Test
    void aFailedHopIsReportedToItsKindAfterThePlayerIsTold() {
        Reporting kind = new Reporting(false);

        charges.fail(kind, ALEX, Optional.empty(), HopNotice.TIMED_OUT, Map.of(), "cell");

        assertThat(kind.reported).isEqualTo("Alex cell TIMED_OUT");
        assertThat(notifier.keys()).containsExactly("hop.timed_out");
    }

    @Test
    void aReportThatThrowsIsLoggedAndTheFailureStillEnds() {
        charges.fail(new Reporting(true), ALEX, Optional.empty(), HopNotice.FAILED, Map.of(), "cell");

        assertThat(notifier.keys()).containsExactly("hop.failed");
        assertThat(log.warnings()).anyMatch(line -> line.contains("cross_server_hop_report_failed"));
    }

    /** A jail-like kind: no money, every notice its own key, and a report it either keeps or throws on. */
    private static final class Reporting implements HopResolver {

        private final boolean throwing;
        String reported = "";

        Reporting(boolean throwing) {
            this.throwing = throwing;
        }

        @Override
        public HopKind kind() {
            return HopKind.JAIL;
        }

        @Override
        public HopNotices notices() {
            Map<HopNotice, MessageKey> keys = new EnumMap<>(HopNotice.class);
            for (HopNotice notice : HopNotice.values()) {
                String key = "hop." + notice.name().toLowerCase(java.util.Locale.ROOT);
                keys.put(notice, () -> key);
            }
            return new HopNotices("jail", keys);
        }

        @Override
        public String label(PendingHop hop) {
            return hop.target();
        }

        @Override
        public HopResolution resolve(PendingHop hop) {
            return HopResolution.unreachable(hop.target());
        }

        @Override
        public boolean refund(PlayerRef who, HopCharge charge) {
            return false;
        }

        @Override
        public boolean settle(PlayerRef who, HopSettlement settlement, HopCharge charge) {
            return false;
        }

        @Override
        public void failed(PlayerRef who, String label, HopNotice reason) {
            if (throwing) {
                throw new IllegalStateException("staff channel down");
            }
            reported = who.name() + " " + label + " " + reason;
        }
    }
}
