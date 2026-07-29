package com.uxplima.uxmessentials.shared.adapter.outbound.claim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.adapter.outbound.claim.FactionsClaimProvider.FactionsClaimLookup;
import com.uxplima.uxmessentials.shared.adapter.outbound.claim.FactionsClaimProvider.TerritoryView;
import com.uxplima.uxmessentials.shared.application.port.ClaimProvider.ClaimLookup;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * The Factions provider in the two states a test classpath can reach, plus its collective trust rule.
 *
 * <p>No {@code com.massivecraft} class resolves here, so the reflective half is pinned on what actually decides
 * behaviour on a real server: absent means inactive without naming a Factions type, and present-but-unreachable
 * degrades {@code claimAt} to empty rather than throwing into a home or warp placement. The one plugin name
 * {@code Factions} is what both maintained forks register under, so the present-guard covers FactionsUUID and
 * SaberFactions alike. The "same faction" rule runs against the pure {@link TerritoryView} seam.
 */
class FactionsClaimProviderTest {

    private static final Object RED = "red-faction";
    private static final Object BLUE = "blue-faction";
    private static final UUID MEMBER = UUID.randomUUID();
    private static final UUID RIVAL = UUID.randomUUID();
    private static final UUID FACTIONLESS = UUID.randomUUID();

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
    void active_isFalse_whenFactionsAbsent() {
        assertThat(provider().active()).isFalse();
    }

    @Test
    void claimAt_returnsEmpty_withoutThrowing_whenFactionsAbsent() {
        assertThatCode(() -> assertThat(provider().claimAt(world(), 5, 7)).isEmpty())
                .doesNotThrowAnyException();
    }

    @Test
    void claimAt_returnsEmpty_withoutThrowing_whenFactionsIsPresentButItsApiIsUnreachable() {
        MockBukkit.createMockPlugin("Factions");
        FactionsClaimProvider provider = provider();

        assertThat(provider.active()).isTrue();
        assertThatCode(() -> assertThat(provider.claimAt(world(), 5, 7)).isEmpty())
                .doesNotThrowAnyException();
    }

    @Test
    void aMemberOfTheHoldingFactionIsTrusted() {
        ClaimLookup lookup = lookupOf(RED, Map.of(MEMBER, RED, RIVAL, BLUE));

        assertThat(lookup.isTrusted(MEMBER)).isTrue();
        assertThat(lookup.isTrusted(RIVAL)).as("a rival faction is not trust").isFalse();
        assertThat(lookup.isTrusted(FACTIONLESS)).as("no faction is not trust").isFalse();
    }

    @Test
    void factionTerritoryHasNoSingleOwnerAndNobodyIsBannedFromIt() {
        ClaimLookup lookup = lookupOf(RED, Map.of(MEMBER, RED));

        assertThat(lookup.owner()).as("factions hold land collectively").isEmpty();
        // isOwner falls back to the port's owner-or-member default, so a member owns collectively held land.
        assertThat(lookup.isOwner(MEMBER)).isTrue();
        assertThat(lookup.isOwner(RIVAL)).isFalse();
        assertThat(lookup.isBanned(RIVAL)).as("Factions has no per-chunk ban").isFalse();
    }

    private FactionsClaimProvider provider() {
        return new FactionsClaimProvider(plugin, server, noOpLog());
    }

    private static WorldRef world() {
        return new WorldRef(UUID.randomUUID(), "world");
    }

    private static ClaimLookup lookupOf(Object holder, Map<UUID, Object> memberships) {
        return new FactionsClaimLookup(new FakeTerritory(Optional.of(holder), memberships));
    }

    /** A {@link TerritoryView} over a plain holder token and a membership map, standing in for real territory. */
    private record FakeTerritory(Optional<Object> faction, Map<UUID, Object> memberships) implements TerritoryView {

        @Override
        public Optional<Object> factionOf(UUID player) {
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
