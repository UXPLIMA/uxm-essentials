package com.uxplima.uxmessentials.shared.adapter.outbound.claim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

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
        // The probe constructs every candidate (uxmClaims, Lands, GriefPrevention) and calls active() on each.
        // None of those SDKs is on the test classpath, so this would throw NoClassDefFoundError if a provider
        // touched its SDK before the present-guard. It must not.
        assertThatCode(() -> ClaimProviders.detect(plugin, server, noOpLog())).doesNotThrowAnyException();
    }

    @Test
    void detect_claimAtReturnsEmpty_whenNoClaimPluginInstalled() {
        ClaimProvider provider = ClaimProviders.detect(plugin, server, noOpLog());
        WorldRef world = new WorldRef(UUID.randomUUID(), "world");
        assertThat(provider.claimAt(world, 10, 20)).isEmpty();
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
