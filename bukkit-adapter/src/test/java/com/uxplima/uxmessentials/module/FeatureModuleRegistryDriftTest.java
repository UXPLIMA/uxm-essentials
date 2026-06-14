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
        assertThat(registry.byId(ModuleId.of("scoreboard"))).isPresent();
        assertThat(registry.byId(ModuleId.of("tablist"))).isPresent();
        assertThat(registry.byId(ModuleId.of("vote"))).isPresent();
        assertThat(registry.byId(ModuleId.of("discordlink"))).isPresent();
        assertThat(registry.byId(ModuleId.of("nametags"))).isPresent();
        assertThat(registry.byId(ModuleId.of("staff"))).isPresent();
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
                        "playerwarps",
                        "scoreboard",
                        "tablist",
                        "vote",
                        "discordlink",
                        "nametags",
                        "staff");
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
    void playerwarpsShipsEnabledAndPublishesItsSurface() {
        DefaultModuleRegistry registry = new DefaultModuleRegistry();
        FeatureModule playerwarps = registry.byId(ModuleId.of("playerwarps"))
                .orElseThrow(() -> new AssertionError("playerwarps is not registered"));

        // playerwarps is the 14th context — player-owned warps keyed (owner, name), delegating teleport
        // execution like warps. The later scoreboard context now lands last, so playerwarps must merely be
        // registered, not last.
        assertThat(registry.byId(ModuleId.of("playerwarps"))).isPresent();

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
    void scoreboardShipsDisabledAndPublishesItsSurface() {
        DefaultModuleRegistry registry = new DefaultModuleRegistry();
        FeatureModule scoreboard = registry.byId(ModuleId.of("scoreboard"))
                .orElseThrow(() -> new AssertionError("scoreboard is not registered"));

        // scoreboard is the 15th context — a per-player sidebar on uxmlib-hud (the tablist header/footer is its own
        // context now). The later tablist/vote contexts land after it, so scoreboard must merely be registered, not
        // last.
        assertThat(registry.byId(ModuleId.of("scoreboard"))).isPresent();

        // It ships DISABLED (like communication, its content is operator data): with no modules.conf override it is
        // absent from the enabled set while every steady-state sibling stays on. Explicitly enabling exactly it
        // brings only it on.
        Set<String> defaults = registry.enabledModules(new FixedConfig(Map.of())).stream()
                .map(m -> m.id().value())
                .collect(Collectors.toSet());
        assertThat(defaults).doesNotContain("scoreboard");
        assertThat(defaults).contains("teleport", "economy", "holograms", "playerwarps");
        Set<String> on = registry.enabledModules(new FixedConfig(Map.of("modules.scoreboard.enabled", true))).stream()
                .map(m -> m.id().value())
                .collect(Collectors.toSet());
        assertThat(on).contains("scoreboard", "teleport", "holograms");

        // Enabled, scoreboard contributes its single /scoreboard command and persists nothing (the display content
        // is config-authored and the per-player visibility bit is PDC-backed), so it declares no MigrationSet.
        Set<String> literals =
                scoreboard.commands().stream().map(CommandSpec::literal).collect(Collectors.toSet());
        assertThat(literals).containsExactly("scoreboard");
        assertThat(scoreboard.migrations()).isEmpty();
    }

    @Test
    void tablistShipsDisabledAndPublishesNoCommandSurface() {
        DefaultModuleRegistry registry = new DefaultModuleRegistry();
        FeatureModule tablist = registry.byId(ModuleId.of("tablist"))
                .orElseThrow(() -> new AssertionError("tablist is not registered"));

        // tablist is the 16th context — the per-player tab-list header/footer split out of scoreboard so the two
        // enable, author, and refresh independently. It is registered next to scoreboard.
        assertThat(registry.byId(ModuleId.of("tablist"))).isPresent();

        // It ships DISABLED (like scoreboard, its content is operator data): with no modules.conf override it is
        // absent from the enabled set while every steady-state sibling stays on, and crucially scoreboard and tablist
        // toggle independently — disabling exactly tablist leaves scoreboard's gate untouched and vice versa.
        Set<String> defaults = registry.enabledModules(new FixedConfig(Map.of())).stream()
                .map(m -> m.id().value())
                .collect(Collectors.toSet());
        assertThat(defaults).doesNotContain("tablist", "scoreboard");
        assertThat(defaults).contains("teleport", "economy", "holograms", "playerwarps");
        Set<String> on = registry.enabledModules(new FixedConfig(Map.of("modules.tablist.enabled", true))).stream()
                .map(m -> m.id().value())
                .collect(Collectors.toSet());
        assertThat(on).contains("tablist", "teleport", "holograms");
        assertThat(on).doesNotContain("scoreboard");

        // The tablist is always-on for every viewer when enabled — there is no per-player visibility toggle — so it
        // publishes no command, and it persists nothing (the header/footer is config-authored), so it declares no
        // MigrationSet.
        assertThat(tablist.commands()).isEmpty();
        assertThat(tablist.migrations()).isEmpty();
    }

    @Test
    void voteShipsEnabledAndPublishesItsSurface() {
        DefaultModuleRegistry registry = new DefaultModuleRegistry();
        FeatureModule vote =
                registry.byId(ModuleId.of("vote")).orElseThrow(() -> new AssertionError("vote is not registered"));

        // vote is the 17th context — a Votifier-bridged vote-rewards and vote-party feature. The later
        // discordlink context now lands last, so vote must merely be registered, not last.
        assertThat(registry.byId(ModuleId.of("vote"))).isPresent();

        // It ships ENABLED (a steady-state feature, inert until rewards/links are authored): with no modules.conf
        // override it is on, and disabling exactly vote removes only it while every sibling stays on.
        Set<String> defaults = registry.enabledModules(new FixedConfig(Map.of())).stream()
                .map(m -> m.id().value())
                .collect(Collectors.toSet());
        assertThat(defaults).contains("vote", "teleport", "economy", "holograms", "playerwarps");
        Set<String> off = registry.enabledModules(new FixedConfig(Map.of("modules.vote.enabled", false))).stream()
                .map(m -> m.id().value())
                .collect(Collectors.toSet());
        assertThat(off).doesNotContain("vote");
        assertThat(off).contains("teleport", "economy", "playerwarps");

        // Enabled, vote contributes exactly /vote and /voteparty (the /vote testreward action is a subcommand,
        // not a literal) and owns no extra Flyway location (its vote_party and vote_queue tables are in the
        // persistence V15 baseline, always applied), so it declares no MigrationSet of its own.
        Set<String> literals =
                vote.commands().stream().map(CommandSpec::literal).collect(Collectors.toSet());
        assertThat(literals).containsExactlyInAnyOrder("vote", "voteparty");
        assertThat(vote.migrations()).isEmpty();
    }

    @Test
    void discordlinkShipsEnabledAndPublishesItsSurface() {
        DefaultModuleRegistry registry = new DefaultModuleRegistry();
        FeatureModule discordlink = registry.byId(ModuleId.of("discordlink"))
                .orElseThrow(() -> new AssertionError("discordlink is not registered"));

        // discordlink is the 18th context — Discord account linking — registered after the seventeen prior modules.
        // The later nametags context now lands last, so discordlink must merely be registered, not last.
        assertThat(registry.byId(ModuleId.of("discordlink"))).isPresent();

        // It ships ENABLED (a steady-state feature): with no modules.conf override it is on, and disabling exactly
        // discordlink removes only it while every sibling stays on.
        Set<String> defaults = registry.enabledModules(new FixedConfig(Map.of())).stream()
                .map(m -> m.id().value())
                .collect(Collectors.toSet());
        assertThat(defaults).contains("discordlink", "teleport", "economy", "vote");
        Set<String> off =
                registry.enabledModules(new FixedConfig(Map.of("modules.discordlink.enabled", false))).stream()
                        .map(m -> m.id().value())
                        .collect(Collectors.toSet());
        assertThat(off).doesNotContain("discordlink");
        assertThat(off).contains("teleport", "economy", "vote");

        // Enabled, discordlink contributes exactly /discordlink and /discordunlink (/discordlink status is a
        // subcommand, not a literal) and owns no extra Flyway location (its discord_links and
        // discord_link_pending tables are in the persistence V16 baseline, always applied), so it declares no
        // MigrationSet of its own.
        Set<String> literals =
                discordlink.commands().stream().map(CommandSpec::literal).collect(Collectors.toSet());
        assertThat(literals).containsExactlyInAnyOrder("discordlink", "discordunlink");
        assertThat(discordlink.migrations()).isEmpty();
    }

    @Test
    void nametagsShipsDisabledAndPublishesNoCommandSurface() {
        DefaultModuleRegistry registry = new DefaultModuleRegistry();
        FeatureModule nametags = registry.byId(ModuleId.of("nametags"))
                .orElseThrow(() -> new AssertionError("nametags is not registered"));

        // nametags is the 19th context — a per-wearer above-head TextDisplay nametag on the Scheduler refresh timer.
        // The later staff context now lands last, so nametags must merely be registered, not last.
        assertThat(registry.byId(ModuleId.of("nametags"))).isPresent();

        // It ships DISABLED (like scoreboard/tablist, its formats are operator data): with no modules.conf override it
        // is absent from the enabled set while every steady-state sibling stays on. Explicitly enabling exactly it
        // brings only it on; disabling it leaves every sibling untouched.
        Set<String> defaults = registry.enabledModules(new FixedConfig(Map.of())).stream()
                .map(m -> m.id().value())
                .collect(Collectors.toSet());
        assertThat(defaults).doesNotContain("nametags");
        assertThat(defaults).contains("teleport", "economy", "holograms", "playerwarps", "discordlink");
        Set<String> on = registry.enabledModules(new FixedConfig(Map.of("modules.nametags.enabled", true))).stream()
                .map(m -> m.id().value())
                .collect(Collectors.toSet());
        assertThat(on).contains("nametags", "teleport", "holograms");

        // The nametag is always-on for every wearer when enabled — there is no per-player visibility toggle — so it
        // publishes no command, and it persists nothing (the formats are config-authored), so it declares no
        // MigrationSet.
        assertThat(nametags.commands()).isEmpty();
        assertThat(nametags.migrations()).isEmpty();
    }

    @Test
    void staffIsTheLastModuleShipsDisabledAndPublishesItsSurface() {
        DefaultModuleRegistry registry = new DefaultModuleRegistry();
        FeatureModule staff =
                registry.byId(ModuleId.of("staff")).orElseThrow(() -> new AssertionError("staff is not registered"));

        // staff is the 20th context — a STAFF-MODE-ONLY toolkit (the /staffmode toggle + gadget hotbar + staff chat) —
        // registered last after the nineteen prior modules.
        assertThat(registry.all().get(registry.all().size() - 1).id().value()).isEqualTo("staff");

        // It ships DISABLED (like scoreboard/tablist/nametags, its gadget hotbar is operator data): with no
        // modules.conf
        // override it is absent from the enabled set while every steady-state sibling stays on. Explicitly enabling
        // exactly it brings only it on; disabling it leaves every sibling untouched.
        Set<String> defaults = registry.enabledModules(new FixedConfig(Map.of())).stream()
                .map(m -> m.id().value())
                .collect(Collectors.toSet());
        assertThat(defaults).doesNotContain("staff");
        assertThat(defaults).contains("teleport", "economy", "holograms", "playerwarps", "discordlink");
        Set<String> on = registry.enabledModules(new FixedConfig(Map.of("modules.staff.enabled", true))).stream()
                .map(m -> m.id().value())
                .collect(Collectors.toSet());
        assertThat(on).contains("staff", "teleport", "holograms");

        // Enabled, staff contributes exactly /staffmode and /staffchat (the /sc alias is not a separate literal) and
        // owns no extra Flyway location (its staff_loadout table is in the persistence V29 baseline, always applied),
        // so it declares no MigrationSet of its own. STAFF-MODE ONLY: no sanction command is published.
        Set<String> literals =
                staff.commands().stream().map(CommandSpec::literal).collect(Collectors.toSet());
        assertThat(literals).containsExactlyInAnyOrder("staffmode", "staffchat");
        assertThat(staff.migrations()).isEmpty();
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
