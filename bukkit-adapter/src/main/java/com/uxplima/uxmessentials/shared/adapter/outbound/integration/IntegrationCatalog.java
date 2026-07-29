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
                    "LuckPermsAccess.java",
                    "group and meta lookups behind the quota and tier permission nodes"),
            new Integration(
                    "Vault",
                    IntegrationFamily.ECONOMY,
                    "ForeignEconomyProviders.java",
                    "runs the economy through an existing Vault provider, and answers permission queries"),
            new Integration(
                    "Treasury",
                    IntegrationFamily.ECONOMY,
                    "ForeignEconomyProviders.java",
                    "runs the economy through an existing Treasury provider"),
            new Integration(
                    "PlayerPoints",
                    IntegrationFamily.ECONOMY,
                    "PlayerPointsCurrencyBackend.java",
                    "points as a currency menus and requirements can charge"),
            new Integration(
                    "CoinsEngine",
                    IntegrationFamily.ECONOMY,
                    "CoinsEngineCurrencyBackend.java",
                    "CoinsEngine currencies menus and requirements can charge"),
            new Integration(
                    "zEssentials",
                    IntegrationFamily.ECONOMY,
                    "ZEssentialsCurrencyBackend.java",
                    "zEssentials currencies menus and requirements can charge"),
            new Integration(
                    "EconomyShopGUI",
                    IntegrationFamily.ECONOMY,
                    "ShopWorthSource.java",
                    "prices unpriced items for /worth and /sell from the shop's own sell prices"),
            new Integration(
                    "PlaceholderAPI",
                    IntegrationFamily.PLACEHOLDERS,
                    "PlaceholderApiSupport.java",
                    "expands third-party placeholders in our text, and publishes the uxmessentials expansion"),
            new Integration(
                    "MiniPlaceholders",
                    IntegrationFamily.PLACEHOLDERS,
                    "MiniPlaceholdersSupport.java",
                    "MiniMessage-native global tags inside our catalog lines"),
            new Integration(
                    "HeadDatabase",
                    IntegrationFamily.ITEMS,
                    "HeadDatabaseHook.java",
                    "head-database heads as menu icons"),
            new Integration(
                    "ItemsAdder",
                    IntegrationFamily.ITEMS,
                    "ItemsAdderIconProvider.java",
                    "ItemsAdder custom items as menu icons"),
            new Integration(
                    "Oraxen", IntegrationFamily.ITEMS, "OraxenIconProvider.java", "Oraxen custom items as menu icons"),
            new Integration(
                    "Nexo", IntegrationFamily.ITEMS, "NexoIconProvider.java", "Nexo custom items as menu icons"),
            new Integration(
                    "MMOItems", IntegrationFamily.ITEMS, "MMOItemsIconProvider.java", "MMOItems items as menu icons"),
            new Integration(
                    "ExecutableItems",
                    IntegrationFamily.ITEMS,
                    "ExecutableItemsIconProvider.java",
                    "ExecutableItems items as menu icons"),
            new Integration(
                    "Lands", IntegrationFamily.CLAIMS, "LandsClaimProvider.java", "Lands claims gate homes and warps"),
            new Integration(
                    "GriefPrevention",
                    IntegrationFamily.CLAIMS,
                    "GriefPreventionClaimProvider.java",
                    "GriefPrevention claims gate homes and warps"),
            new Integration(
                    "GriefDefender",
                    IntegrationFamily.CLAIMS,
                    "GriefDefenderClaimProvider.java",
                    "GriefDefender claims gate homes and warps"),
            new Integration(
                    "ExcellentClaims",
                    IntegrationFamily.CLAIMS,
                    "ExcellentClaimsClaimProvider.java",
                    "ExcellentClaims claims gate homes and warps"),
            new Integration(
                    "SimpleClaimSystem",
                    IntegrationFamily.CLAIMS,
                    "SimpleClaimSystemClaimProvider.java",
                    "SimpleClaimSystem claims gate homes and warps"),
            new Integration(
                    "RClaim",
                    IntegrationFamily.CLAIMS,
                    "RClaimClaimProvider.java",
                    "RClaim claims gate homes and warps"),
            new Integration(
                    "XClaim",
                    IntegrationFamily.CLAIMS,
                    "XClaimClaimProvider.java",
                    "XClaim claims gate homes and warps"),
            new Integration(
                    "Homestead",
                    IntegrationFamily.CLAIMS,
                    "HomesteadClaimProvider.java",
                    "Homestead claims gate homes and warps"),
            new Integration(
                    "Towny", IntegrationFamily.CLAIMS, "TownyClaimProvider.java", "town plots gate homes and warps"),
            new Integration(
                    "Kingdoms",
                    IntegrationFamily.CLAIMS,
                    "KingdomsClaimProvider.java",
                    "KingdomsX land gates homes and warps"),
            new Integration(
                    "HuskClaims",
                    IntegrationFamily.CLAIMS,
                    "HuskClaimsClaimProvider.java",
                    "HuskClaims claims gate homes and warps"),
            new Integration(
                    "HuskTowns",
                    IntegrationFamily.CLAIMS,
                    "HuskTownsClaimProvider.java",
                    "HuskTowns town claims gate homes and warps"),
            new Integration(
                    "Factions",
                    IntegrationFamily.CLAIMS,
                    "FactionsClaimProvider.java",
                    "FactionsUUID or SaberFactions territory gates homes and warps"),
            new Integration(
                    "BentoBox",
                    IntegrationFamily.CLAIMS,
                    "BentoBoxClaimProvider.java",
                    "BentoBox islands gate homes and warps"),
            new Integration(
                    "Residence",
                    IntegrationFamily.CLAIMS,
                    "ResidenceClaimProvider.java",
                    "residences gate homes and warps"),
            new Integration(
                    "PlotSquared",
                    IntegrationFamily.CLAIMS,
                    "PlotSquaredClaimProvider.java",
                    "plots gate homes and warps"),
            new Integration(
                    "SuperiorSkyblock2",
                    IntegrationFamily.CLAIMS,
                    "SuperiorSkyblockClaimProvider.java",
                    "SuperiorSkyblock islands gate homes and warps"),
            new Integration(
                    "WorldGuard",
                    IntegrationFamily.REGIONS,
                    "WorldGuardReflection.java",
                    "region membership and flags gate teleports, poses and menu requirements"),
            new Integration(
                    "WorldEdit",
                    IntegrationFamily.REGIONS,
                    "WorldEditRegionSelection.java",
                    "defines a region from your current WorldEdit selection"),
            new Integration(
                    "dynmap",
                    IntegrationFamily.MAPS,
                    "MapMarkerPublishers.java",
                    "publishes spawn and warp markers to the dynmap web map"),
            new Integration(
                    "squaremap",
                    IntegrationFamily.MAPS,
                    "MapMarkerPublishers.java",
                    "publishes spawn and warp markers to the squaremap web map"),
            new Integration(
                    "BlueMap",
                    IntegrationFamily.MAPS,
                    "MapMarkerPublishers.java",
                    "publishes spawn and warp markers to the BlueMap web map"),
            new Integration(
                    "Votifier",
                    IntegrationFamily.VOTE,
                    "VotifierListener.java",
                    "feeds votes from vote sites into the vote module"),
            new Integration(
                    "floodgate",
                    IntegrationFamily.BEDROCK,
                    "BedrockDetector.java",
                    "shows Bedrock players native forms instead of chest menus"),
            new Integration(
                    "Geyser-Spigot",
                    IntegrationFamily.BEDROCK,
                    "BedrockDetector.java",
                    "names Bedrock players on networks running Geyser without Floodgate"),
            new Integration(
                    "AuthMe",
                    IntegrationFamily.LOGIN,
                    "LoginPluginHandoff.java",
                    "holds our security prompts until AuthMe has authenticated the player"),
            new Integration(
                    "nLogin",
                    IntegrationFamily.LOGIN,
                    "LoginPluginHandoff.java",
                    "holds our security prompts until nLogin has authenticated the player"),
            new Integration(
                    "SuperVanish",
                    IntegrationFamily.VANISH,
                    "ForeignVanishStore.java",
                    "players SuperVanish has hidden are vanished for our tab list, nametags and /msg too"),
            new Integration(
                    "PremiumVanish",
                    IntegrationFamily.VANISH,
                    "ForeignVanishStore.java",
                    "players PremiumVanish has hidden are vanished for our tab list, nametags and /msg too"),
            new Integration(
                    "CombatLogX",
                    IntegrationFamily.COMBAT,
                    "ForeignCombatGate.java",
                    "a CombatLogX combat tag blocks self-teleports out of a fight"),
            new Integration(
                    "PvPManager",
                    IntegrationFamily.COMBAT,
                    "ForeignCombatGate.java",
                    "a PvPManager combat tag blocks self-teleports out of a fight"),
            new Integration(
                    "ViaVersion",
                    IntegrationFamily.PROTOCOL,
                    "ViaVersionClientProtocol.java",
                    "reports what protocol version a translated client actually speaks"),
            new Integration(
                    "Jobs",
                    IntegrationFamily.CONDITIONS,
                    "IntegrationConditions.java",
                    "job and level conditions in menu requirements"),
            new Integration(
                    "mcMMO",
                    IntegrationFamily.CONDITIONS,
                    "McMmoIntegration.java",
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
