package com.uxplima.uxmessentials.shared.adapter.outbound.integration;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.NullMarked;

/**
 * Every third-party plugin uxmEssentials integrates with, declared once.
 *
 * <p>This builds nothing. Each family keeps the registry that constructs its providers ({@code ClaimProviders},
 * {@code CurrencyBackends}, {@code MapMarkerPublishers}, {@code IconProviders}, {@code Hooks}); what lives here
 * is the answer to "which plugins do we integrate with, and where does each one enter the code". Three surfaces
 * read it instead of keeping their own copy: {@code paper-plugin.yml} (kept in exact bijection with this list by
 * {@code IntegrationCatalogDriftTest}), the {@code /uxmess doctor} soft-dependency check, and the integrations
 * page in the published documentation.
 *
 * <p>Adding an integration is therefore one edit here plus the manifest entry the guard demands, and the
 * operator surface follows. Removing one is the same edit in reverse: an integration whose code is deleted but
 * whose declaration is left behind fails the build rather than quietly telling operators we still support it.
 */
@NullMarked
public final class IntegrationCatalog {

    private IntegrationCatalog() {}

    private static final List<Integration> ENTRIES = List.of(
            new Integration(
                    "LuckPerms",
                    IntegrationFamily.PERMISSIONS,
                    "com.uxplima.uxmessentials.shared.adapter.outbound.permission.LuckPermsAccess",
                    "group and meta lookups behind the quota and tier permission nodes"),
            new Integration(
                    "Vault",
                    IntegrationFamily.ECONOMY,
                    "com.uxplima.uxmessentials.economy.adapter.ForeignEconomyProviders",
                    "runs the economy through an existing Vault provider, and answers permission queries"),
            new Integration(
                    "Treasury",
                    IntegrationFamily.ECONOMY,
                    "com.uxplima.uxmessentials.economy.adapter.ForeignEconomyProviders",
                    "runs the economy through an existing Treasury provider"),
            new Integration(
                    "PlayerPoints",
                    IntegrationFamily.ECONOMY,
                    "com.uxplima.uxmessentials.economy.adapter.outbound.backend.PlayerPointsCurrencyBackend",
                    "points as a currency menus and requirements can charge"),
            new Integration(
                    "CoinsEngine",
                    IntegrationFamily.ECONOMY,
                    "com.uxplima.uxmessentials.economy.adapter.outbound.backend.CoinsEngineCurrencyBackend",
                    "CoinsEngine currencies menus and requirements can charge"),
            new Integration(
                    "zEssentials",
                    IntegrationFamily.ECONOMY,
                    "com.uxplima.uxmessentials.economy.adapter.outbound.backend.ZEssentialsCurrencyBackend",
                    "zEssentials currencies menus and requirements can charge"),
            new Integration(
                    "EconomyShopGUI",
                    IntegrationFamily.ECONOMY,
                    "com.uxplima.uxmessentials.economy.adapter.outbound.ShopWorthSource",
                    "prices unpriced items for /worth and /sell from the shop's own sell prices"),
            new Integration(
                    "EconomyShopGUI-Premium",
                    IntegrationFamily.ECONOMY,
                    "com.uxplima.uxmessentials.economy.adapter.outbound.ShopWorthSource",
                    "the premium edition of the same shop, under its own plugin name"),
            new Integration(
                    "PlaceholderAPI",
                    IntegrationFamily.PLACEHOLDERS,
                    "com.uxplima.uxmessentials.shared.adapter.outbound.papi.PlaceholderApiSupport",
                    "expands third-party placeholders in our text, and publishes the uxmessentials expansion"),
            new Integration(
                    "MiniPlaceholders",
                    IntegrationFamily.PLACEHOLDERS,
                    "com.uxplima.uxmessentials.shared.adapter.outbound.miniplaceholders.MiniPlaceholdersSupport",
                    "MiniMessage-native global tags inside our catalog lines"),
            new Integration(
                    "HeadDatabase",
                    IntegrationFamily.ITEMS,
                    "com.uxplima.uxmessentials.shared.adapter.outbound.hooks.HeadDatabaseHook",
                    "head-database heads as menu icons"),
            new Integration(
                    "ItemsAdder",
                    IntegrationFamily.ITEMS,
                    "com.uxplima.uxmlib.menu.providers.ItemsAdderIconProvider",
                    "ItemsAdder custom items as menu icons"),
            new Integration(
                    "Oraxen",
                    IntegrationFamily.ITEMS,
                    "com.uxplima.uxmlib.menu.providers.OraxenIconProvider",
                    "Oraxen custom items as menu icons"),
            new Integration(
                    "Nexo",
                    IntegrationFamily.ITEMS,
                    "com.uxplima.uxmlib.menu.providers.NexoIconProvider",
                    "Nexo custom items as menu icons"),
            new Integration(
                    "CraftEngine",
                    IntegrationFamily.ITEMS,
                    "com.uxplima.uxmlib.menu.providers.CraftEngineIconProvider",
                    "CraftEngine custom items as menu icons"),
            new Integration(
                    "MMOItems",
                    IntegrationFamily.ITEMS,
                    "com.uxplima.uxmlib.menu.providers.MMOItemsIconProvider",
                    "MMOItems items as menu icons"),
            new Integration(
                    "ExecutableItems",
                    IntegrationFamily.ITEMS,
                    "com.uxplima.uxmlib.menu.providers.ExecutableItemsIconProvider",
                    "ExecutableItems items as menu icons"),
            new Integration(
                    "Lands",
                    IntegrationFamily.CLAIMS,
                    "com.uxplima.uxmessentials.shared.adapter.outbound.claim.LandsClaimProvider",
                    "Lands claims gate homes and warps"),
            new Integration(
                    "GriefPrevention",
                    IntegrationFamily.CLAIMS,
                    "com.uxplima.uxmessentials.shared.adapter.outbound.claim.GriefPreventionClaimProvider",
                    "GriefPrevention claims gate homes and warps"),
            new Integration(
                    "GriefDefender",
                    IntegrationFamily.CLAIMS,
                    "com.uxplima.uxmessentials.shared.adapter.outbound.claim.GriefDefenderClaimProvider",
                    "GriefDefender claims gate homes and warps"),
            new Integration(
                    "ExcellentClaims",
                    IntegrationFamily.CLAIMS,
                    "com.uxplima.uxmessentials.shared.adapter.outbound.claim.ExcellentClaimsClaimProvider",
                    "ExcellentClaims claims gate homes and warps"),
            new Integration(
                    "SimpleClaimSystem",
                    IntegrationFamily.CLAIMS,
                    "com.uxplima.uxmessentials.shared.adapter.outbound.claim.SimpleClaimSystemClaimProvider",
                    "SimpleClaimSystem claims gate homes and warps"),
            new Integration(
                    "RClaim",
                    IntegrationFamily.CLAIMS,
                    "com.uxplima.uxmessentials.shared.adapter.outbound.claim.RClaimClaimProvider",
                    "RClaim claims gate homes and warps"),
            new Integration(
                    "XClaim",
                    IntegrationFamily.CLAIMS,
                    "com.uxplima.uxmessentials.shared.adapter.outbound.claim.XClaimClaimProvider",
                    "XClaim claims gate homes and warps"),
            new Integration(
                    "Homestead",
                    IntegrationFamily.CLAIMS,
                    "com.uxplima.uxmessentials.shared.adapter.outbound.claim.HomesteadClaimProvider",
                    "Homestead claims gate homes and warps"),
            new Integration(
                    "Towny",
                    IntegrationFamily.CLAIMS,
                    "com.uxplima.uxmessentials.shared.adapter.outbound.claim.TownyClaimProvider",
                    "town plots gate homes and warps"),
            new Integration(
                    "Kingdoms",
                    IntegrationFamily.CLAIMS,
                    "com.uxplima.uxmessentials.shared.adapter.outbound.claim.KingdomsClaimProvider",
                    "KingdomsX land gates homes and warps"),
            new Integration(
                    "HuskClaims",
                    IntegrationFamily.CLAIMS,
                    "com.uxplima.uxmessentials.shared.adapter.outbound.claim.HuskClaimsClaimProvider",
                    "HuskClaims claims gate homes and warps"),
            new Integration(
                    "HuskTowns",
                    IntegrationFamily.CLAIMS,
                    "com.uxplima.uxmessentials.shared.adapter.outbound.claim.HuskTownsClaimProvider",
                    "HuskTowns town claims gate homes and warps"),
            new Integration(
                    "Factions",
                    IntegrationFamily.CLAIMS,
                    "com.uxplima.uxmessentials.shared.adapter.outbound.claim.FactionsClaimProvider",
                    "FactionsUUID or SaberFactions territory gates homes and warps"),
            new Integration(
                    "BentoBox",
                    IntegrationFamily.CLAIMS,
                    "com.uxplima.uxmessentials.shared.adapter.outbound.claim.BentoBoxClaimProvider",
                    "BentoBox islands gate homes and warps"),
            new Integration(
                    "Residence",
                    IntegrationFamily.CLAIMS,
                    "com.uxplima.uxmessentials.shared.adapter.outbound.claim.ResidenceClaimProvider",
                    "residences gate homes and warps"),
            new Integration(
                    "PlotSquared",
                    IntegrationFamily.CLAIMS,
                    "com.uxplima.uxmessentials.shared.adapter.outbound.claim.PlotSquaredClaimProvider",
                    "plots gate homes and warps"),
            new Integration(
                    "SuperiorSkyblock2",
                    IntegrationFamily.CLAIMS,
                    "com.uxplima.uxmessentials.shared.adapter.outbound.claim.SuperiorSkyblockClaimProvider",
                    "SuperiorSkyblock islands gate homes and warps"),
            new Integration(
                    "WorldGuard",
                    IntegrationFamily.REGIONS,
                    "com.uxplima.uxmessentials.shared.adapter.outbound.worldguard.WorldGuardReflection",
                    "region membership and flags gate teleports, poses and menu requirements"),
            new Integration(
                    "WorldEdit",
                    IntegrationFamily.REGIONS,
                    "com.uxplima.uxmessentials.regions.adapter.inbound.command.WorldEditRegionSelection",
                    "defines a region from your current WorldEdit selection"),
            new Integration(
                    "dynmap",
                    IntegrationFamily.MAPS,
                    "com.uxplima.uxmessentials.shared.adapter.outbound.mapmarker.MapMarkerPublishers",
                    "publishes spawn and warp markers to the dynmap web map"),
            new Integration(
                    "squaremap",
                    IntegrationFamily.MAPS,
                    "com.uxplima.uxmessentials.shared.adapter.outbound.mapmarker.MapMarkerPublishers",
                    "publishes spawn and warp markers to the squaremap web map"),
            new Integration(
                    "BlueMap",
                    IntegrationFamily.MAPS,
                    "com.uxplima.uxmessentials.shared.adapter.outbound.mapmarker.MapMarkerPublishers",
                    "publishes spawn and warp markers to the BlueMap web map"),
            new Integration(
                    "Votifier",
                    IntegrationFamily.VOTE,
                    "com.uxplima.uxmessentials.vote.adapter.inbound.listener.VotifierListener",
                    "feeds votes from vote sites into the vote module"),
            // Both Bedrock seams name the wiring site rather than a class of ours. The detector and the form
            // screen live in uxmLib (com.uxplima.uxmlib.bedrock), which owns the present-guard now; PluginModule
            // is where this plugin asks for them, so it is the file to read when the integration misbehaves.
            new Integration(
                    "floodgate",
                    IntegrationFamily.BEDROCK,
                    "com.uxplima.uxmessentials.bootstrap.di.PluginModule",
                    "shows Bedrock players native forms instead of chest menus"),
            new Integration(
                    "Geyser-Spigot",
                    IntegrationFamily.BEDROCK,
                    "com.uxplima.uxmessentials.bootstrap.di.PluginModule",
                    "names Bedrock players on networks running Geyser without Floodgate"),
            new Integration(
                    "AuthMe",
                    IntegrationFamily.LOGIN,
                    "com.uxplima.uxmessentials.security.adapter.LoginPluginHandoff",
                    "holds our security prompts until AuthMe has authenticated the player"),
            new Integration(
                    "nLogin",
                    IntegrationFamily.LOGIN,
                    "com.uxplima.uxmessentials.security.adapter.LoginPluginHandoff",
                    "holds our security prompts until nLogin has authenticated the player"),
            new Integration(
                    "SuperVanish",
                    IntegrationFamily.VANISH,
                    "com.uxplima.uxmessentials.vanish.adapter.outbound.ForeignVanishStore",
                    "players SuperVanish has hidden are vanished for our tab list, nametags and /msg too"),
            new Integration(
                    "PremiumVanish",
                    IntegrationFamily.VANISH,
                    "com.uxplima.uxmessentials.vanish.adapter.outbound.ForeignVanishStore",
                    "players PremiumVanish has hidden are vanished for our tab list, nametags and /msg too"),
            new Integration(
                    "CombatLogX",
                    IntegrationFamily.COMBAT,
                    "com.uxplima.uxmessentials.teleport.adapter.outbound.ForeignCombatGate",
                    "a CombatLogX combat tag blocks self-teleports out of a fight"),
            new Integration(
                    "PvPManager",
                    IntegrationFamily.COMBAT,
                    "com.uxplima.uxmessentials.teleport.adapter.outbound.ForeignCombatGate",
                    "a PvPManager combat tag blocks self-teleports out of a fight"),
            new Integration(
                    "ViaVersion",
                    IntegrationFamily.PROTOCOL,
                    "com.uxplima.uxmessentials.shared.adapter.outbound.protocol.ViaVersionClientProtocol",
                    "reports what protocol version a translated client actually speaks"),
            new Integration(
                    "Jobs",
                    IntegrationFamily.CONDITIONS,
                    "com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.IntegrationConditions",
                    "job and level conditions in menu requirements"),
            new Integration(
                    "mcMMO",
                    IntegrationFamily.CONDITIONS,
                    "com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.McMmoIntegration",
                    "skill-level and power-level conditions in menu requirements"));

    /** Every catalogued integration, in declaration order. */
    public static List<Integration> all() {
        return ENTRIES;
    }

    /** Every catalogued plugin name, in declaration order. */
    public static List<String> plugins() {
        return ENTRIES.stream().map(Integration::plugin).toList();
    }

    /** The integrations grouped by family, families in declaration order and members in catalog order. */
    public static Map<IntegrationFamily, List<Integration>> byFamily() {
        Map<IntegrationFamily, List<Integration>> grouped = new EnumMap<>(IntegrationFamily.class);
        for (Integration integration : ENTRIES) {
            grouped.computeIfAbsent(integration.family(), family -> new ArrayList<>())
                    .add(integration);
        }
        grouped.replaceAll((family, members) -> List.copyOf(members));
        return grouped;
    }

    /** The catalogued integration for {@code plugin}, or empty when that plugin is not one of ours. */
    public static Optional<Integration> byPlugin(String plugin) {
        Objects.requireNonNull(plugin, "plugin");
        return ENTRIES.stream()
                .filter(integration -> integration.plugin().equals(plugin))
                .findFirst();
    }
}
