package com.uxplima.uxmessentials.shared.adapter.outbound.claim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Set;
import java.util.UUID;

import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.adapter.outbound.claim.HuskTownsClaimProvider.HuskTownsClaimLookup;
import com.uxplima.uxmessentials.shared.adapter.outbound.claim.HuskTownsClaimProvider.TownView;
import com.uxplima.uxmessentials.shared.application.port.ClaimProvider.ClaimLookup;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * The HuskTowns provider in the two states a test classpath can reach, plus its collective trust rule.
 *
 * <p>No {@code net.william278} class resolves here, so the reflective half is pinned on what actually decides
 * behaviour on a real server: absent means inactive without naming a HuskTowns type, and present-but-unreachable
 * degrades {@code claimAt} to empty rather than throwing into a home or warp placement. The membership rule runs
 * against the pure {@link TownView} seam, including that a town claim has no single owner.
 */
class HuskTownsClaimProviderTest {

    private static final UUID MEMBER = UUID.randomUUID();
    private static final UUID OUTSIDER = UUID.randomUUID();

    private ServerMock server;
    private Plugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void active_isFalse_whenHuskTownsAbsent() {
        assertThat(provider().active()).isFalse();
    }

    @Test
    void claimAt_returnsEmpty_withoutThrowing_whenHuskTownsAbsent() {
        assertThatCode(() -> assertThat(provider().claimAt(world(), 5, 7)).isEmpty())
                .doesNotThrowAnyException();
    }

    @Test
    void claimAt_returnsEmpty_withoutThrowing_whenHuskTownsIsPresentButItsApiIsUnreachable() {
        MockBukkit.createMockPlugin("HuskTowns");
        HuskTownsClaimProvider provider = provider();

        assertThat(provider.active()).isTrue();
        assertThatCode(() -> assertThat(provider.claimAt(world(), 5, 7)).isEmpty())
                .doesNotThrowAnyException();
    }

    @Test
    void aTownMemberIsTrustedAndAnOutsiderIsNot() {
        ClaimLookup lookup = new HuskTownsClaimLookup(() -> Set.of(MEMBER));

        assertThat(lookup.isTrusted(MEMBER)).isTrue();
        assertThat(lookup.isTrusted(OUTSIDER)).isFalse();
    }

    @Test
    void aTownClaimHasNoSingleOwnerAndNobodyIsBannedFromIt() {
        ClaimLookup lookup = new HuskTownsClaimLookup(() -> Set.of(MEMBER));

        assertThat(lookup.owner()).as("a town holds its land collectively").isEmpty();
        // isOwner falls back to the port's owner-or-member default, so a member owns collectively held land.
        assertThat(lookup.isOwner(MEMBER)).isTrue();
        assertThat(lookup.isOwner(OUTSIDER)).isFalse();
        assertThat(lookup.isBanned(OUTSIDER)).isFalse();
    }

    private HuskTownsClaimProvider provider() {
        return new HuskTownsClaimProvider(plugin, server, noOpLog());
    }

    private static WorldRef world() {
        return new WorldRef(UUID.randomUUID(), "world");
    }

    private static Logger noOpLog() {
        return new Logger() {
            @Override
            public void info(String message, Object... args) {}

            @Override
            public void warn(String message, Object... args) {}

            @Override
            public void error(String message, Throwable cause) {}

            @Override
            public void debug(String message, Object... args) {}
        };
    }
}
