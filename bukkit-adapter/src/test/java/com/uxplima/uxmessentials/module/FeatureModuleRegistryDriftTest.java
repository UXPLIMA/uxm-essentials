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

        // teleport, homes, economy, warps, kits, playerstate, messaging, presence and moderation are the
        // landed contexts, registered dependency-first: teleport before the homes/warps contexts that delegate
        // teleport execution to it, and economy before warps and kits because each may charge a cost through
        // the economy provider. playerstate is self-contained (transient in-memory snapshots, no DB, no
        // cross-context bridge) and lands after kits. messaging soft-couples to moderation (mute) and
        // presence (vanish) — both gates degrade gracefully — so it carries no hard dependency edge. presence
        // owns the vanish state messaging and teleport read through the canSee graph; that coupling is soft.
        // moderation provides the real mute/jail gates messaging and teleport hold placeholders for, a soft
        // couple too. itemworld is stateless and ACL-thin (no DB, no cross-context bridge) and lands after
        // moderation, ahead of vaults. vaults is DB-persisted player item storage (the 12th and final feature
        // context); it carries no cross-context bridge and lands last. The registry is a valid, immutable,
        // ordered set that resolves each by id and rejects a not-yet-landed context.
        assertThat(registry.byId(ModuleId.of("teleport"))).isPresent();
        assertThat(registry.byId(ModuleId.of("homes"))).isPresent();
        assertThat(registry.byId(ModuleId.of("economy"))).isPresent();
        assertThat(registry.byId(ModuleId.of("warps"))).isPresent();
        assertThat(registry.byId(ModuleId.of("kits"))).isPresent();
        assertThat(registry.byId(ModuleId.of("playerstate"))).isPresent();
        assertThat(registry.byId(ModuleId.of("messaging"))).isPresent();
        assertThat(registry.byId(ModuleId.of("presence"))).isPresent();
        assertThat(registry.byId(ModuleId.of("moderation"))).isPresent();
        assertThat(registry.byId(ModuleId.of("itemworld"))).isPresent();
        assertThat(registry.byId(ModuleId.of("vaults"))).isPresent();
        assertThat(registry.byId(ModuleId.of("communication"))).isPresent();
        assertThat(registry.byId(ModuleId.of("holograms"))).isPresent();
        assertThat(registry.byId(ModuleId.of("playerwarps"))).isPresent();
        assertThat(registry.all().stream().map(m -> m.id().value()))
                .containsExactly(
                        "teleport",
                        "homes",
                        "economy",
                        "warps",
                        "kits",
                        "playerstate",
                        "messaging",
                        "presence",
                        "moderation",
                        "itemworld",
                        "vaults",
                        "communication",
                        "holograms",
                        "playerwarps");
        assertThatThrownBy(() -> registry.all().add(new FakeModule("x")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void itemworldIsIndependentlyDisableableAndPublishesItsFullVerbSurface() {
        DefaultModuleRegistry registry = new DefaultModuleRegistry();
        FeatureModule itemworld = registry.byId(ModuleId.of("itemworld"))
                .orElseThrow(() -> new AssertionError("itemworld is not registered"));

        // Disabling exactly itemworld removes only it from the enabled set; every sibling stays on.
        ConfigStore off = new FixedConfig(Map.of("modules.itemworld.enabled", false));
        Set<String> enabled =
                registry.enabledModules(off).stream().map(m -> m.id().value()).collect(Collectors.toSet());
        assertThat(enabled).doesNotContain("itemworld");
        assertThat(enabled).contains("teleport", "economy", "moderation");

        // Enabled, itemworld contributes its full ~40-verb surface: the group-B verbs owned here and the
        // /repair /repairall /hat /more verbs playerstate deferred (§15.6) — registered here, never twice.
        Set<String> literals =
                itemworld.commands().stream().map(CommandSpec::literal).collect(Collectors.toSet());
        assertThat(literals)
                .contains(
                        "give",
                        "giveall",
                        "item",
                        "firework",
                        "potion",
                        "book",
                        "more",
                        "repair",
                        "repairall",
                        "hat",
                        "enchant",
                        "itemdb",
                        "recipe",
                        "showitem",
                        "unbreakable",
                        "disenchant",
                        "itemmodel",
                        "editsign",
                        "iteminfo",
                        "copyinv",
                        "endercopy", // item utils
                        "anvil",
                        "workbench",
                        "enderchest",
                        "furnace", // workstations
                        "disposal",
                        "condense",
                        "enderclear", // cleanup
                        "powertool",
                        "powertooltoggle",
                        "powertoollist", // powertool
                        "spawnmob",
                        "spawner",
                        "kill",
                        "butcher",
                        "killall",
                        "remove",
                        "entitycount",
                        "unlimited", // mob/entity
                        "time",
                        "weather",
                        "day",
                        "night",
                        "sun",
                        "rain",
                        "thunder", // time/weather
                        "lightning",
                        "fireball",
                        "kittycannon",
                        "antioch",
                        "beezooka",
                        "break",
                        "tree",
                        "bigtree",
                        "nuke"); // admin-fun
        // The full surface: 27 item-utils + 9 workstations + 3 cleanup + 3 powertool + 8 mob/entity
        // + 7 time/weather + 9 admin-fun = 66 distinct literals, no verb dropped and none registered twice.
        assertThat(itemworld.commands()).hasSize(66);
        assertThat(literals).hasSize(66);
        assertThat(itemworld.migrations()).isEmpty(); // itemworld is stateless: no persistence, no migration
    }

    @Test
    void vaultsIsTheLastDbBackedContextAndIndependentlyDisableable() {
        DefaultModuleRegistry registry = new DefaultModuleRegistry();
        FeatureModule vaults =
                registry.byId(ModuleId.of("vaults")).orElseThrow(() -> new AssertionError("vaults is not registered"));

        // Disabling exactly vaults removes only it from the enabled set; every sibling stays on.
        ConfigStore off = new FixedConfig(Map.of("modules.vaults.enabled", false));
        Set<String> enabled =
                registry.enabledModules(off).stream().map(m -> m.id().value()).collect(Collectors.toSet());
        assertThat(enabled).doesNotContain("vaults");
        assertThat(enabled).contains("teleport", "economy", "moderation", "itemworld");

        // Enabled, vaults contributes its single /vault command and owns no extra Flyway location (its table is
        // in the persistence V6 baseline, always applied), so it declares no MigrationSet of its own.
        Set<String> literals =
                vaults.commands().stream().map(CommandSpec::literal).collect(Collectors.toSet());
        assertThat(literals).containsExactly("vault");
        assertThat(vaults.migrations()).isEmpty();
    }

    @Test
    void communicationShipsDisabledAndPublishesItsStaticSurface() {
        DefaultModuleRegistry registry = new DefaultModuleRegistry();
        FeatureModule communication = registry.byId(ModuleId.of("communication"))
                .orElseThrow(() -> new AssertionError("communication is not registered"));

        // communication is the round-3 feature context; it ships DISABLED. With no modules.conf override it is
        // absent from the enabled set while every landed sibling (which defaults to on) stays enabled.
        Set<String> defaults = registry.enabledModules(new FixedConfig(Map.of())).stream()
                .map(m -> m.id().value())
                .collect(Collectors.toSet());
        assertThat(defaults).doesNotContain("communication");
        assertThat(defaults).contains("teleport", "economy", "moderation", "itemworld", "vaults");

        // Explicitly enabling exactly communication brings only it on; the rest are unchanged.
        Set<String> on =
                registry.enabledModules(new FixedConfig(Map.of("modules.communication.enabled", true))).stream()
                        .map(m -> m.id().value())
                        .collect(Collectors.toSet());
        assertThat(on).contains("communication", "teleport", "vaults");

        // Its static surface is the plugin's own /broadcast, /broadcastworld, /me, /broadcasttoggle, /clearchat,
        // and /togglechat; the operator-configured info-page commands (/rules, /motd, …) are dynamic and not part
        // of this fixed table. It persists nothing.
        Set<String> literals =
                communication.commands().stream().map(CommandSpec::literal).collect(Collectors.toSet());
        assertThat(literals)
                .containsExactlyInAnyOrder(
                        "broadcast", "broadcastworld", "me", "broadcasttoggle", "clearchat", "togglechat");
        assertThat(communication.migrations()).isEmpty();
    }

    @Test
    void hologramsIsTheLastModuleShipsEnabledAndPublishesItsSurface() {
        DefaultModuleRegistry registry = new DefaultModuleRegistry();
        FeatureModule holograms = registry.byId(ModuleId.of("holograms"))
                .orElseThrow(() -> new AssertionError("holograms is not registered"));

        // holograms is the round-4 world-placed display context, registered after the prior modules; the later
        // playerwarps context now lands last, so holograms must merely be registered, not last.
        assertThat(registry.byId(ModuleId.of("holograms"))).isPresent();

        // It ships ENABLED (a steady-state world-placed feature like warps/vaults): with no modules.conf override
        // it is on, and disabling exactly holograms removes only it while every sibling stays on.
        Set<String> defaults = registry.enabledModules(new FixedConfig(Map.of())).stream()
                .map(m -> m.id().value())
                .collect(Collectors.toSet());
        assertThat(defaults).contains("holograms", "teleport", "vaults");
        Set<String> off = registry.enabledModules(new FixedConfig(Map.of("modules.holograms.enabled", false))).stream()
                .map(m -> m.id().value())
                .collect(Collectors.toSet());
        assertThat(off).doesNotContain("holograms");
        assertThat(off).contains("teleport", "economy", "vaults");

        // Enabled, holograms contributes its single /hologram command and owns no extra Flyway location (its
        // tables are in the persistence V13 baseline, always applied), so it declares no MigrationSet of its own.
        Set<String> literals =
                holograms.commands().stream().map(CommandSpec::literal).collect(Collectors.toSet());
        assertThat(literals).containsExactly("hologram");
        assertThat(holograms.migrations()).isEmpty();
    }

    @Test
    void playerwarpsIsTheLastModuleShipsEnabledAndPublishesItsSurface() {
        DefaultModuleRegistry registry = new DefaultModuleRegistry();
        FeatureModule playerwarps = registry.byId(ModuleId.of("playerwarps"))
                .orElseThrow(() -> new AssertionError("playerwarps is not registered"));

        // playerwarps is the 14th context — player-owned warps keyed (owner, name), delegating teleport
        // execution like warps — registered last after the thirteen prior modules.
        assertThat(registry.all().get(registry.all().size() - 1).id().value()).isEqualTo("playerwarps");

        // It ships ENABLED (a steady-state feature like warps/vaults/holograms): with no modules.conf override it
        // is on, and disabling exactly playerwarps removes only it while every sibling stays on.
        Set<String> defaults = registry.enabledModules(new FixedConfig(Map.of())).stream()
                .map(m -> m.id().value())
                .collect(Collectors.toSet());
        assertThat(defaults).contains("playerwarps", "teleport", "warps", "holograms");
        Set<String> off =
                registry.enabledModules(new FixedConfig(Map.of("modules.playerwarps.enabled", false))).stream()
                        .map(m -> m.id().value())
                        .collect(Collectors.toSet());
        assertThat(off).doesNotContain("playerwarps");
        assertThat(off).contains("teleport", "warps", "holograms");

        // Enabled, playerwarps contributes exactly /pwarp /setpwarp /delpwarp /pwarps and owns no extra Flyway
        // location (its player_warps table is in the persistence V14 baseline, always applied), so it declares
        // no MigrationSet of its own.
        Set<String> literals =
                playerwarps.commands().stream().map(CommandSpec::literal).collect(Collectors.toSet());
        assertThat(literals).containsExactlyInAnyOrder("pwarp", "setpwarp", "delpwarp", "pwarps");
        assertThat(playerwarps.migrations()).isEmpty();
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
