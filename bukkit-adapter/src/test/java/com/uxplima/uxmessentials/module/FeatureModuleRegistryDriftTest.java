package com.uxplima.uxmessentials.module;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.uxplima.uxmessentials.bootstrap.di.DefaultModuleRegistry;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ListModuleRegistry;
import com.uxplima.uxmessentials.shared.application.module.ListenerFactory;
import com.uxplima.uxmessentials.shared.application.module.MigrationSet;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import com.uxplima.uxmessentials.shared.application.module.ModuleRegistry;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.junit.jupiter.api.Test;

/**
 * Skeleton of the headline drift guard for first-class feature modules.
 *
 * <p>The shipped guard enforces a four-way set-equality (registry ⇔ context packages ⇔ {@code
 * modules.conf} switches ⇔ this document's per-context rows) and boots under MockBukkit to prove a
 * disabled module registers zero commands and zero listeners. No bounded context has shipped yet, so
 * here we (1) assert the registry contract on the real {@link DefaultModuleRegistry} and (2) prove the
 * independently-disableable property the four-way guard relies on, using stand-in modules. Each real
 * context plugs into the same property by adding itself to {@link DefaultModuleRegistry}.
 */
class FeatureModuleRegistryDriftTest {

    @Test
    void defaultRegistryExposesAnImmutableDeduplicatedSet() {
        DefaultModuleRegistry registry = new DefaultModuleRegistry();

        // The real registry is empty in this phase but already a valid, immutable, ordered set.
        assertThat(registry.all()).isEmpty();
        assertThat(registry.byId(ModuleId.of("homes"))).isEmpty();
        assertThatThrownBy(() -> registry.all().add(new FakeModule("x")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void registryRejectsDuplicateIds() {
        ModuleRegistry registry = new ListModuleRegistry().register(new FakeModule("homes"));

        assertThatThrownBy(() -> registry.register(new FakeModule("homes")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate module id");
    }

    @Test
    void lookupResolvesRegisteredModulesAndPreservesOrder() {
        FakeModule economy = new FakeModule("economy");
        FakeModule homes = new FakeModule("homes");
        ModuleRegistry registry = new ListModuleRegistry().register(economy).register(homes);

        assertThat(registry.all()).containsExactly(economy, homes); // dependency-first order preserved
        assertThat(registry.byId(ModuleId.of("homes"))).contains(homes);
        assertThat(registry.byId(ModuleId.of("warps"))).isEmpty();
    }

    @Test
    void everyRegisteredModuleIsIndependentlyDisableable() {
        List<String> ids = List.of("economy", "teleport", "homes", "warps");
        ModuleRegistry registry = new ListModuleRegistry();
        ids.forEach(id -> registry.register(new FakeModule(id)));

        // Disabling exactly one module removes only that module from the enabled set; the rest stay.
        for (String disabled : ids) {
            ConfigStore config = new FixedConfig(Map.of("modules." + disabled + ".enabled", false));
            Set<String> enabled = idsOf(registry.enabledModules(config));

            assertThat(enabled).doesNotContain(disabled);
            assertThat(enabled).containsExactlyInAnyOrderElementsOf(others(ids, disabled));
        }
    }

    @Test
    void disablingEveryModuleWiresNothing() {
        List<String> ids = List.of("economy", "homes", "warps");
        ModuleRegistry registry = new ListModuleRegistry();
        ids.forEach(id -> registry.register(new FakeModule(id)));

        Map<String, Object> allOff =
                ids.stream().collect(Collectors.toMap(id -> "modules." + id + ".enabled", id -> false));

        assertThat(registry.enabledModules(new FixedConfig(allOff))).isEmpty();
    }

    @Test
    void missingSwitchDefaultsToEnabled() {
        ModuleRegistry registry = new ListModuleRegistry().register(new FakeModule("kits"));

        // No key for modules.kits.enabled — the module defaults to on (operators opt out).
        assertThat(idsOf(registry.enabledModules(new FixedConfig(Map.of())))).containsExactly("kits");
    }

    private static Set<String> idsOf(List<FeatureModule> modules) {
        return modules.stream().map(m -> m.id().value()).collect(Collectors.toSet());
    }

    private static Set<String> others(List<String> ids, String excluded) {
        return ids.stream().filter(id -> !id.equals(excluded)).collect(Collectors.toSet());
    }

    /** A minimal {@link FeatureModule} that contributes nothing — enough to exercise the contract. */
    private static final class FakeModule implements FeatureModule {
        private final ModuleId id;

        FakeModule(String id) {
            this.id = ModuleId.of(id);
        }

        @Override
        public ModuleId id() {
            return id;
        }

        @Override
        public String configRoot() {
            return id.configRoot();
        }

        @Override
        public List<CommandSpec> commands() {
            return List.of();
        }

        @Override
        public List<ListenerFactory> listeners() {
            return List.of();
        }

        @Override
        public List<MigrationSet> migrations() {
            return List.of();
        }

        @Override
        public boolean enabled(ConfigStore config) {
            return config.getBoolean(configRoot() + ".enabled", true);
        }

        @Override
        public void start(ModuleContext ctx) {
            // No-op: a stand-in module acquires nothing.
        }

        @Override
        public void stop() {
            // No-op: nothing to release.
        }
    }

    /** A map-backed {@link ConfigStore} for driving enable gates in tests. */
    private record FixedConfig(Map<String, Object> values) implements ConfigStore {
        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return values.get(path) instanceof Boolean b ? b : fallback;
        }

        @Override
        public String getString(String path, String fallback) {
            return values.get(path) instanceof String s ? s : fallback;
        }

        @Override
        public int getInt(String path, int fallback) {
            return values.get(path) instanceof Integer i ? i : fallback;
        }
    }
}
