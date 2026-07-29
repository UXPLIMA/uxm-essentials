package com.uxplima.uxmessentials.shared.adapter.outbound.claim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.adapter.outbound.claim.KingdomsClaimProvider.KingdomsClaimLookup;
import com.uxplima.uxmessentials.shared.adapter.outbound.claim.KingdomsClaimProvider.LandView;
import com.uxplima.uxmessentials.shared.application.port.ClaimProvider.ClaimLookup;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * The KingdomsX provider in the two states a test classpath can reach, plus its trust rule.
 *
 * <p>KingdomsX is closed source and no {@code org.kingdoms} class resolves here, so the reflective half is pinned
 * on what actually decides behaviour on a real server: absent means inactive without naming a KingdomsX type, and
 * present-but-unreachable degrades {@code claimAt} to empty rather than throwing into a home or warp placement.
 * The collective trust rule ("your kingdom holds this land") runs against the pure {@link LandView} seam, where
 * both the match and the two ways it must deny (no kingdom of your own, an unreadable kingdom on the land) are
 * exercised directly.
 */
class KingdomsClaimProviderTest {

    private static final Object RED = "red-kingdom";
    private static final Object BLUE = "blue-kingdom";
    private static final UUID MEMBER = UUID.randomUUID();
    private static final UUID RIVAL = UUID.randomUUID();
    private static final UUID LONER = UUID.randomUUID();

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
    void active_isFalse_whenKingdomsAbsent() {
        assertThat(provider().active()).isFalse();
    }

    @Test
    void claimAt_returnsEmpty_withoutThrowing_whenKingdomsAbsent() {
        assertThatCode(() -> assertThat(provider().claimAt(world(), 5, 7)).isEmpty())
                .doesNotThrowAnyException();
    }

    @Test
    void claimAt_returnsEmpty_withoutThrowing_whenKingdomsIsPresentButItsApiIsUnreachable() {
        // The version-drift shape: the plugin is installed, so the present-guard passes, but the SDK behind it
        // cannot be reached. A claim gate must read that as "unclaimed" rather than fail the placement.
        MockBukkit.createMockPlugin("Kingdoms");
        KingdomsClaimProvider provider = provider();

        assertThat(provider.active()).isTrue();
        assertThatCode(() -> assertThat(provider.claimAt(world(), 5, 7)).isEmpty())
                .doesNotThrowAnyException();
    }

    @Test
    void aMemberOfTheHoldingKingdomIsTrusted() {
        ClaimLookup lookup = lookupOf(RED, Map.of(MEMBER, RED, RIVAL, BLUE));

        assertThat(lookup.isTrusted(MEMBER)).isTrue();
        assertThat(lookup.isTrusted(RIVAL)).as("a rival kingdom is not trust").isFalse();
        assertThat(lookup.isTrusted(LONER)).as("no kingdom is not trust").isFalse();
    }

    @Test
    void landWhoseKingdomCannotBeReadTrustsNobody() {
        // The strict direction for the one unverifiable step of the chain: a land we cannot attribute is closed,
        // not open.
        ClaimLookup lookup = new KingdomsClaimLookup(new FakeLand(Optional.empty(), Map.of(MEMBER, RED)));

        assertThat(lookup.isTrusted(MEMBER)).isFalse();
    }

    @Test
    void kingdomsLandHasNoSingleOwnerAndNobodyIsBannedFromIt() {
        ClaimLookup lookup = lookupOf(RED, Map.of(MEMBER, RED));

        assertThat(lookup.owner()).as("kingdoms hold land collectively").isEmpty();
        // isOwner falls back to the port's owner-or-member default, so a member owns collectively held land.
        assertThat(lookup.isOwner(MEMBER)).isTrue();
        assertThat(lookup.isOwner(RIVAL)).isFalse();
        assertThat(lookup.isBanned(RIVAL)).as("KingdomsX has no per-land ban").isFalse();
    }

    private KingdomsClaimProvider provider() {
        return new KingdomsClaimProvider(plugin, server, noOpLog());
    }

    private static WorldRef world() {
        return new WorldRef(UUID.randomUUID(), "world");
    }

    private static ClaimLookup lookupOf(Object holder, Map<UUID, Object> memberships) {
        return new KingdomsClaimLookup(new FakeLand(Optional.of(holder), memberships));
    }

    /** A {@link LandView} over a plain holder token and a membership map, standing in for a real KingdomsX land. */
    private record FakeLand(Optional<Object> kingdom, Map<UUID, Object> memberships) implements LandView {

        @Override
        public Optional<Object> kingdomOf(UUID player) {
            return Optional.ofNullable(memberships.get(player));
        }
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
