package com.uxplima.uxmessentials.shared.adapter.outbound.claim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.adapter.outbound.claim.HuskClaimsClaimProvider.ClaimView;
import com.uxplima.uxmessentials.shared.adapter.outbound.claim.HuskClaimsClaimProvider.HuskClaimsClaimLookup;
import com.uxplima.uxmessentials.shared.application.port.ClaimProvider.ClaimLookup;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * The HuskClaims provider in the two states a test classpath can reach, plus its ownership and trust fold.
 *
 * <p>No {@code net.william278} class resolves here, so the reflective half is pinned on what actually decides
 * behaviour on a real server: absent means inactive without naming a HuskClaims type, and present-but-unreachable
 * degrades {@code claimAt} to empty rather than throwing into a home or warp placement. The decisions that need no
 * live HuskClaims (owner, a trusted-but-not-owner user, the admin claim nobody owns) run against the pure
 * {@link ClaimView} seam.
 */
class HuskClaimsClaimProviderTest {

    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID TRUSTED = UUID.randomUUID();
    private static final UUID STRANGER = UUID.randomUUID();

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
    void active_isFalse_whenHuskClaimsAbsent() {
        assertThat(provider().active()).isFalse();
    }

    @Test
    void claimAt_returnsEmpty_withoutThrowing_whenHuskClaimsAbsent() {
        assertThatCode(() -> assertThat(provider().claimAt(world(), 5, 7)).isEmpty())
                .doesNotThrowAnyException();
    }

    @Test
    void claimAt_returnsEmpty_withoutThrowing_whenHuskClaimsIsPresentButItsApiIsUnreachable() {
        MockBukkit.createMockPlugin("HuskClaims");
        HuskClaimsClaimProvider provider = provider();

        assertThat(provider.active()).isTrue();
        assertThatCode(() -> assertThat(provider.claimAt(world(), 5, 7)).isEmpty())
                .doesNotThrowAnyException();
    }

    @Test
    void theOwnerOwnsAndATrustedUserIsTrustedButNotAnOwner() {
        ClaimLookup lookup = new HuskClaimsClaimLookup(new FakeClaim(Optional.of(OWNER), Set.of(TRUSTED)));

        assertThat(lookup.isOwner(OWNER)).isTrue();
        assertThat(lookup.isTrusted(OWNER)).isTrue();
        assertThat(lookup.isTrusted(TRUSTED)).isTrue();
        assertThat(lookup.isOwner(TRUSTED))
                .as("any trust level is not ownership")
                .isFalse();
        assertThat(lookup.isTrusted(STRANGER)).isFalse();
        assertThat(lookup.owner()).contains(OWNER);
    }

    @Test
    void anAdminClaimHasNoOwnerButStillCarriesTrust() {
        ClaimLookup lookup = new HuskClaimsClaimLookup(new FakeClaim(Optional.empty(), Set.of(TRUSTED)));

        assertThat(lookup.owner()).isEmpty();
        assertThat(lookup.isOwner(OWNER)).isFalse();
        assertThat(lookup.isTrusted(TRUSTED)).isTrue();
    }

    @Test
    void nobodyIsBannedThroughThisProvider() {
        ClaimLookup lookup = new HuskClaimsClaimLookup(new FakeClaim(Optional.of(OWNER), Set.of()));

        assertThat(lookup.isBanned(STRANGER))
                .as("HuskClaims enforces its own bans; this gate reads trust only")
                .isFalse();
    }

    private HuskClaimsClaimProvider provider() {
        return new HuskClaimsClaimProvider(plugin, server, noOpLog());
    }

    private static WorldRef world() {
        return new WorldRef(UUID.randomUUID(), "world");
    }

    /** A {@link ClaimView} over a plain owner and trust set, standing in for a real HuskClaims claim. */
    private record FakeClaim(Optional<UUID> owner, Set<UUID> trusted) implements ClaimView {}

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
