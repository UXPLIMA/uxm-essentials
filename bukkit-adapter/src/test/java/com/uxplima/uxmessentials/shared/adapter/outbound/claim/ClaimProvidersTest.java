package com.uxplima.uxmessentials.shared.adapter.outbound.claim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import java.util.UUID;

import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.application.port.ClaimProvider;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * The claim-provider discoverer's plugin-present guard. With no claim plugin installed (the MockBukkit default,
 * and the case on the test classpath where the Lands/GriefPrevention SDKs are absent and uxmClaims is not
 * loaded) {@link ClaimProviders#detect} must bind an inactive provider whose {@code claimAt} is empty — and,
 * crucially, probing each candidate must not throw {@link NoClassDefFoundError}, proving each typed provider
 * keeps its SDK references behind its own present-guard so merely constructing and asking {@code active()}
 * never force-loads a claim-plugin class.
 */
class ClaimProvidersTest {

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
    void detect_bindsInactiveProvider_whenNoClaimPluginInstalled() {
        ClaimProvider provider = ClaimProviders.detect(plugin, server, noOpLog());
        assertThat(provider.active()).isFalse();
    }

    @Test
    void detect_doesNotThrow_whenNoClaimPluginInstalled() {
        // The probe constructs every candidate (uxmClaims, Lands, GriefPrevention, GriefDefender,
        // ExcellentClaims, SimpleClaimSystem, RClaim, XClaim, Homestead) and calls active() on each. None of
        // those SDKs is on the test classpath, so this would throw NoClassDefFoundError if a provider touched
        // its SDK before the present-guard. It must not.
        assertThatCode(() -> ClaimProviders.detect(plugin, server, noOpLog())).doesNotThrowAnyException();
    }

    @Test
    void detect_claimAtReturnsEmpty_whenNoClaimPluginInstalled() {
        ClaimProvider provider = ClaimProviders.detect(plugin, server, noOpLog());
        WorldRef world = new WorldRef(UUID.randomUUID(), "world");
        assertThat(provider.claimAt(world, 10, 20)).isEmpty();
    }

    @Test
    void everyCandidate_constructsAndProbesWithoutLoadingItsSdk() {
        // Each provider added in phase 2 must keep its SDK (typed compileOnly jar) or reflective API
        // references behind its plugin-present guard. With no claim plugin installed, constructing one and
        // asking active() / claimAt() must report inactive+empty and must NOT throw NoClassDefFoundError —
        // the same lazy-structure proof the discoverer relies on, asserted per provider.
        WorldRef world = new WorldRef(UUID.randomUUID(), "world");
        for (ClaimProvider provider : candidates()) {
            assertThatCode(() -> {
                        assertThat(provider.active()).isFalse();
                        assertThat(provider.claimAt(world, 10, 20)).isEmpty();
                    })
                    .as("provider %s", provider.getClass().getSimpleName())
                    .doesNotThrowAnyException();
        }
    }

    private List<ClaimProvider> candidates() {
        return List.of(
                new GriefDefenderClaimProvider(plugin, server, noOpLog()),
                new ExcellentClaimsClaimProvider(plugin, server, noOpLog()),
                new SimpleClaimSystemClaimProvider(plugin, server, noOpLog()),
                new RClaimClaimProvider(plugin, server, noOpLog()),
                new XClaimClaimProvider(plugin, server, noOpLog()),
                new HomesteadClaimProvider(plugin, server, noOpLog()));
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
