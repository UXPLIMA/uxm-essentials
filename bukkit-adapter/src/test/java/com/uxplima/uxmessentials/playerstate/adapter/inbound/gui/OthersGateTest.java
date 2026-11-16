package com.uxplima.uxmessentials.playerstate.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.permission.PlayerstatePermissions;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Permissions.QuotaFamily;
import com.uxplima.uxmessentials.shared.application.port.Permissions.QuotaResult;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** A hub pick opens another player's storage only for a viewer the command would let name that player. */
class OthersGateTest {

    private static final PlayerRef STAFF =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-00000000005a"), "Staff");
    private static final PlayerRef ALEX =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-000000000a1e"), "Alex");

    @Test
    void aViewerMayAlwaysOpenTheirOwnStorage() {
        OthersGate gate = new OthersGate(new Granted(Set.of()), PlayerstatePermissions.INVSEE_OTHERS);

        assertThat(gate.allows(STAFF, STAFF)).isTrue();
    }

    @Test
    void anotherPlayerIsRefusedWithoutAnOthersNode() {
        OthersGate gate = new OthersGate(new Granted(Set.of()), PlayerstatePermissions.INVSEE_OTHERS);

        assertThat(gate.allows(STAFF, ALEX)).isFalse();
    }

    @Test
    void theVerbsOwnOthersNodeOpensAnotherPlayer() {
        OthersGate gate = new OthersGate(
                new Granted(Set.of(PlayerstatePermissions.INVSEE_OTHERS)), PlayerstatePermissions.INVSEE_OTHERS);

        assertThat(gate.allows(STAFF, ALEX)).isTrue();
    }

    @Test
    void theCrossCuttingPlayerstateNodeOpensAnotherPlayer() {
        OthersGate gate = new OthersGate(
                new Granted(Set.of(PlayerstatePermissions.PLAYERSTATE_OTHERS)), PlayerstatePermissions.ENDERSEE_OTHERS);

        assertThat(gate.allows(STAFF, ALEX)).isTrue();
    }

    @Test
    void anotherVerbsOthersNodeDoesNotCount() {
        OthersGate gate = new OthersGate(
                new Granted(Set.of(PlayerstatePermissions.INVSEE_OTHERS)), PlayerstatePermissions.ENDERSEE_OTHERS);

        assertThat(gate.allows(STAFF, ALEX)).isFalse();
    }

    /** Every viewer holds exactly the given nodes. */
    private record Granted(Set<String> nodes) implements Permissions {

        @Override
        public boolean has(PlayerRef who, String node) {
            return nodes.contains(node);
        }

        @Override
        public QuotaResult resolveQuota(
                PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long configDefault) {
            return QuotaResult.limited(configDefault);
        }
    }
}
