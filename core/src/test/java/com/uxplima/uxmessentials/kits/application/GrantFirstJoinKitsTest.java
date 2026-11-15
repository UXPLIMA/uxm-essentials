package com.uxplima.uxmessentials.kits.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.uxplima.uxmessentials.kits.application.port.KitGranter;
import com.uxplima.uxmessentials.kits.application.port.KitRepository;
import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.kits.domain.KitId;
import com.uxplima.uxmessentials.kits.domain.KitItem;
import com.uxplima.uxmessentials.shared.application.ledger.NetworkClaims;
import com.uxplima.uxmessentials.shared.application.ledger.NetworkCooldowns;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.testing.FakePermissions;
import com.uxplima.uxmessentials.testing.MemoryClaimLedger;
import com.uxplima.uxmessentials.testing.MutableClock;
import com.uxplima.uxmessentials.testing.QueuedScheduler;
import com.uxplima.uxmessentials.testing.RecordingLogger;
import org.junit.jupiter.api.Test;

class GrantFirstJoinKitsTest {

    private static final KitDefinition STARTER = KitDefinition.builder()
            .id(KitId.of("starter"))
            .items(List.of(KitItem.of("STONE_SWORD", 1)))
            .oneTime(true)
            .firstJoin(true)
            .build();

    private final MutableClock clock = new MutableClock(NOW);
    private final MemoryClaimLedger ledger = new MemoryClaimLedger();
    private final FakePermissions permissions = new FakePermissions();
    private final List<String> received = new ArrayList<>();

    /** One backend of the network, with Alex joining it for the first time. */
    private void firstJoinOn(QueuedScheduler backend) {
        NetworkClaims claims = new NetworkClaims(ledger, backend, clock, new RecordingLogger());
        claims.joined(ALEX.uuid());
        KitAccess access = new KitAccess(
                permissions,
                new NetworkCooldowns(claims, permissions, clock),
                new NetworkKitClaims(claims),
                Optional.empty());
        new GrantFirstJoinKits(new OneKit(), new Hands(), access, new RecordingLogger()).grant(ALEX);
        backend.runAll();
    }

    @Test
    void aOneTimeStarterKitIsHandedOutOnceAcrossTheNetwork() {
        firstJoinOn(new QueuedScheduler());
        firstJoinOn(new QueuedScheduler());

        assertThat(received).containsExactly("Alex:starter");
    }

    private static final class OneKit implements KitRepository {

        @Override
        public Optional<KitDefinition> find(KitId id) {
            return id.equals(STARTER.id()) ? Optional.of(STARTER) : Optional.empty();
        }

        @Override
        public List<KitDefinition> all() {
            return List.of(STARTER);
        }

        @Override
        public boolean exists(KitId id) {
            return id.equals(STARTER.id());
        }

        @Override
        public void save(KitDefinition definition) {}

        @Override
        public void delete(KitId id) {}
    }

    private final class Hands implements KitGranter {

        @Override
        public Grant grant(PlayerRef recipient, KitDefinition kit) {
            received.add(recipient.name() + ":" + kit.id().value());
            return Grant.complete();
        }
    }
}
