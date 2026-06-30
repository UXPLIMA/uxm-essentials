package com.uxplima.uxmessentials.shared.adapter.outbound.hooks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * The optional-plugin hook SPI, proven on the worked PlaceholderAPI example in both states via the reusable
 * {@link HookHarness}.
 *
 * <p>PlaceholderAPI is a {@code compileOnly} soft-depend, so {@code me.clip.placeholderapi} is absent from the
 * test runtime classpath. That makes the absent path a genuine proof of the trap-avoidance: if {@link Hooks}
 * ever reached the real {@link PlaceholderApiExpander} when the plugin is not installed, loading that class
 * would surface as a {@link NoClassDefFoundError}. It must not — the no-op default carries none of those types,
 * so an absent integration is a silent, safe no-op.
 */
class HooksTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void absentHook_resolvesToTheNoOpDefault() {
        PlaceholderApiHook hook = new PlaceholderApiHook();

        PlaceholderApiQuery query = HookHarness.absent(hook);

        assertThat(query).isSameAs(hook.whenAbsent());
        assertThat(query.available()).isFalse();
    }

    @Test
    void absentHook_capabilityCallsAreSafeNoOps() {
        // With no PlaceholderAPI plugin installed and its SDK off the test classpath, calling the capability
        // returns the text unchanged and must NOT throw NoClassDefFoundError — proving the real expander (the
        // only class importing me.clip.placeholderapi) was never reached.
        PlaceholderApiQuery query = HookHarness.absent(new PlaceholderApiHook());
        UUID viewer = UUID.randomUUID();

        assertThatCode(() -> assertThat(query.expand(viewer, "%player_name%")).isEqualTo("%player_name%"))
                .doesNotThrowAnyException();
    }

    @Test
    void presentHook_resolvesToTheRealImplementation() {
        PlaceholderApiHook hook = new PlaceholderApiHook();

        PlaceholderApiQuery query = HookHarness.present(hook);

        // The real expander reports available; expand() itself is left uncalled because the live PlaceholderAPI
        // class is absent from the test classpath — resolving to the present branch is what this asserts.
        assertThat(query).isNotSameAs(hook.whenAbsent());
        assertThat(query.available()).isTrue();
    }

    @Test
    void resolve_isKeyedByCapabilityAndRejectsUnknownTypes() {
        Hooks hooks = Hooks.resolve(MockBukkit.getMock(), HookHarness.SILENT, List.of(new PlaceholderApiHook()));

        assertThat(hooks.provides(PlaceholderApiQuery.class)).isTrue();
        assertThat(hooks.provides(String.class)).isFalse();
        assertThat(hooks.capability(PlaceholderApiQuery.class)).isNotNull();
        assertThatThrownBy(() -> hooks.capability(String.class)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resolve_rejectsTwoHooksClaimingTheSameCapability() {
        assertThatThrownBy(() -> Hooks.resolve(
                        MockBukkit.getMock(),
                        HookHarness.SILENT,
                        List.of(new PlaceholderApiHook(), new PlaceholderApiHook())))
                .isInstanceOf(IllegalStateException.class);
    }
}
