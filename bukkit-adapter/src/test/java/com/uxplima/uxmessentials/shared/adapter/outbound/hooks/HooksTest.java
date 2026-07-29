package com.uxplima.uxmessentials.shared.adapter.outbound.hooks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.bukkit.Server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * The optional-plugin hook SPI itself, proven on a hook that integrates with nothing. The subject is the
 * contract (resolve once, real when present, no-op when absent, one capability per hook, a throwing hook
 * degrades rather than aborting bootstrap), so it is exercised against a test-only capability rather than
 * against whichever real integration happens to exist today.
 *
 * <p>The companion property, that an absent plugin's SDK classes are never loaded, is proven where it can be
 * proven honestly: over a real compileOnly SDK in {@link HeadDatabaseHookTest} and {@link VaultHooksTest},
 * whose types are genuinely off the test runtime classpath.
 */
class HooksTest {

    private static final String FAKE_PLUGIN = "ExampleIntegration";

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
        GreetingHook hook = new GreetingHook();

        Greeting greeting = HookHarness.absent(hook);

        assertThat(greeting).isSameAs(hook.whenAbsent());
        assertThat(greeting.available()).isFalse();
    }

    @Test
    void absentHook_capabilityCallsAreSafeNoOps() {
        Greeting greeting = HookHarness.absent(new GreetingHook());

        assertThatCode(() -> assertThat(greeting.greet("world")).isEqualTo("world"))
                .doesNotThrowAnyException();
    }

    @Test
    void presentHook_resolvesToTheRealImplementation() {
        GreetingHook hook = new GreetingHook();

        Greeting greeting = HookHarness.present(hook);

        assertThat(greeting).isNotSameAs(hook.whenAbsent());
        assertThat(greeting.available()).isTrue();
        assertThat(greeting.greet("world")).isEqualTo("hello world");
    }

    @Test
    void resolve_isKeyedByCapabilityAndRejectsUnknownTypes() {
        Hooks hooks = Hooks.resolve(MockBukkit.getMock(), HookHarness.SILENT, List.of(new GreetingHook()));

        assertThat(hooks.provides(Greeting.class)).isTrue();
        assertThat(hooks.provides(String.class)).isFalse();
        assertThat(hooks.capability(Greeting.class)).isNotNull();
        assertThatThrownBy(() -> hooks.capability(String.class)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resolve_rejectsTwoHooksClaimingTheSameCapability() {
        assertThatThrownBy(() -> Hooks.resolve(
                        MockBukkit.getMock(), HookHarness.SILENT, List.of(new GreetingHook(), new GreetingHook())))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aHookThatThrowsWhilePresentDegradesToItsNoOpDefault() {
        // The bootstrap contract: an incompatible SDK surfaces inside whenPresent, and one broken integration
        // must not take the server's whole enable with it.
        MockBukkit.createMockPlugin(FAKE_PLUGIN);
        Hooks hooks = Hooks.resolve(MockBukkit.getMock(), HookHarness.SILENT, List.of(new BrokenHook()));

        assertThat(hooks.capability(Greeting.class)).isSameAs(Greeting.ABSENT);
    }

    /** A capability that integrates with nothing: enough surface to exercise present, absent and no-op. */
    private interface Greeting {

        Greeting ABSENT = new Greeting() {
            @Override
            public boolean available() {
                return false;
            }

            @Override
            public String greet(String name) {
                return name;
            }
        };

        boolean available();

        String greet(String name);
    }

    /** The ordinary hook: resolves to a real greeting when its plugin is installed. */
    private static final class GreetingHook implements PluginHook<Greeting> {

        @Override
        public String pluginName() {
            return FAKE_PLUGIN;
        }

        @Override
        public Class<Greeting> capability() {
            return Greeting.class;
        }

        @Override
        public Greeting whenAbsent() {
            return Greeting.ABSENT;
        }

        @Override
        public Greeting whenPresent(Server server) {
            return new Greeting() {
                @Override
                public boolean available() {
                    return true;
                }

                @Override
                public String greet(String name) {
                    return "hello " + name;
                }
            };
        }
    }

    /** A hook whose real implementation cannot be built: stands in for an incompatible or partial SDK. */
    private static final class BrokenHook implements PluginHook<Greeting> {

        @Override
        public String pluginName() {
            return FAKE_PLUGIN;
        }

        @Override
        public Class<Greeting> capability() {
            return Greeting.class;
        }

        @Override
        public Greeting whenAbsent() {
            return Greeting.ABSENT;
        }

        @Override
        public Greeting whenPresent(Server server) {
            throw new NoClassDefFoundError("com/example/MissingSdkType");
        }
    }
}
