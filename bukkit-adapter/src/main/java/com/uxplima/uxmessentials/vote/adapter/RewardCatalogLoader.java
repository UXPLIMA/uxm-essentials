package com.uxplima.uxmessentials.vote.adapter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.vote.domain.reward.ItemReward;
import com.uxplima.uxmessentials.vote.domain.reward.MilestoneReward;
import com.uxplima.uxmessentials.vote.domain.reward.RewardCatalog;
import com.uxplima.uxmessentials.vote.domain.reward.RewardSpec;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * Parses the {@code rewards { ... }} block of {@code modules/vote/config.conf} into a {@link RewardCatalog} the
 * {@link com.uxplima.uxmessentials.vote.application.RewardEngine} resolves against. The block groups specs by
 * what triggers them:
 *
 * <pre>{@code
 * rewards {
 *   per-vote = [ { chance = 100, commands = [ "eco give {player} 100" ], items = [ { material = "DIAMOND", amount = 1 } ] } ]
 *   per-site { "PlanetMinecraft" = [ { commands = [ "give {player} diamond 2" ] } ] }
 *   first-vote = [ { messages = [ "<green>Thanks for your first vote!" ] } ]
 *   milestones = [ { at = 100, broadcast = [ "<gold>{player} hit 100 votes!" ] }, { every = 50, commands = [ "crate give {player} vote 1" ] } ]
 * }
 * }</pre>
 *
 * <p>Each {@code <reward>} node carries {@code chance} (int, default 100), an optional {@code permission}, the
 * three command/message/broadcast string lists, an {@code items} list of {@code { material, amount, name, lore }}
 * maps, and an optional {@code worlds} filter (empty = any world). A milestone node is a {@code <reward>} with
 * one of {@code at} or {@code every} added. Every section is tolerant: an absent block yields an empty
 * list/map, and a malformed entry (a milestone with neither {@code at} nor {@code every}, an item with no
 * material) is skipped rather than failing the load, so an unconfigured server resolves to {@link
 * RewardCatalog#empty()}.
 */
@NullMarked
public final class RewardCatalogLoader {

    private static final int DEFAULT_CHANCE = 100;

    private RewardCatalogLoader() {}

    /** Read {@code moduleConfig} and parse its {@code rewards} block; an absent or unreadable file yields an empty catalog. */
    public static RewardCatalog loadFrom(Path moduleConfig, Logger log) {
        Objects.requireNonNull(moduleConfig, "moduleConfig");
        Objects.requireNonNull(log, "log");
        if (!Files.exists(moduleConfig)) {
            return RewardCatalog.empty();
        }
        try {
            ConfigurationNode root = HoconConfigurationLoader.builder()
                    .path(moduleConfig)
                    .build()
                    .load();
            return parse(root.node("rewards"));
        } catch (ConfigurateException failure) {
            log.error("failed to load vote rewards from " + moduleConfig + "; using empty catalog", failure);
            return RewardCatalog.empty();
        }
    }

    /** Parse the {@code rewards} node into a catalog; an absent node yields {@link RewardCatalog#empty()}. */
    public static RewardCatalog parse(ConfigurationNode rewards) {
        Objects.requireNonNull(rewards, "rewards");
        if (rewards.virtual()) {
            return RewardCatalog.empty();
        }
        return new RewardCatalog(
                readSpecs(rewards.node("per-vote")),
                readPerSite(rewards.node("per-site")),
                readSpecs(rewards.node("first-vote")),
                readMilestones(rewards.node("milestones")));
    }

    private static Map<String, List<RewardSpec>> readPerSite(ConfigurationNode node) {
        if (node.virtual() || !node.isMap()) {
            return Map.of();
        }
        Map<String, List<RewardSpec>> perSite = new LinkedHashMap<>();
        for (Map.Entry<Object, ? extends ConfigurationNode> entry :
                node.childrenMap().entrySet()) {
            String service = String.valueOf(entry.getKey());
            List<RewardSpec> specs = readSpecs(entry.getValue());
            if (!specs.isEmpty()) {
                perSite.put(service, specs);
            }
        }
        return perSite;
    }

    private static List<RewardSpec> readSpecs(ConfigurationNode node) {
        if (node.virtual() || !node.isList()) {
            return List.of();
        }
        List<RewardSpec> specs = new ArrayList<>();
        for (ConfigurationNode child : node.childrenList()) {
            readSpec(child).ifPresent(specs::add);
        }
        return List.copyOf(specs);
    }

    private static Optional<RewardSpec> readSpec(ConfigurationNode node) {
        if (node.virtual() || !node.isMap()) {
            return Optional.empty();
        }
        int chance = clampChance(node.node("chance").getInt(DEFAULT_CHANCE));
        Optional<String> permission = optionalString(node.node("permission"));
        List<String> commands = strings(node.node("commands"));
        List<String> messages = strings(node.node("messages"));
        List<String> broadcasts = strings(node.node("broadcast"));
        List<ItemReward> items = readItems(node.node("items"));
        java.util.Set<String> worlds = java.util.Set.copyOf(strings(node.node("worlds")));
        return Optional.of(new RewardSpec(chance, permission, commands, messages, broadcasts, items, worlds));
    }

    private static List<MilestoneReward> readMilestones(ConfigurationNode node) {
        if (node.virtual() || !node.isList()) {
            return List.of();
        }
        List<MilestoneReward> milestones = new ArrayList<>();
        for (ConfigurationNode child : node.childrenList()) {
            readMilestone(child).ifPresent(milestones::add);
        }
        return List.copyOf(milestones);
    }

    private static Optional<MilestoneReward> readMilestone(ConfigurationNode node) {
        Optional<RewardSpec> spec = readSpec(node);
        if (spec.isEmpty()) {
            return Optional.empty();
        }
        OptionalLong at = positiveLong(node.node("at"));
        OptionalLong every = positiveLong(node.node("every"));
        // Exactly one of at/every must be present and positive; a malformed milestone is skipped, not fatal.
        if (at.isPresent() == every.isPresent()) {
            return Optional.empty();
        }
        return Optional.of(new MilestoneReward(at, every, spec.get()));
    }

    private static List<ItemReward> readItems(ConfigurationNode node) {
        if (node.virtual() || !node.isList()) {
            return List.of();
        }
        List<ItemReward> items = new ArrayList<>();
        for (ConfigurationNode child : node.childrenList()) {
            readItem(child).ifPresent(items::add);
        }
        return List.copyOf(items);
    }

    private static Optional<ItemReward> readItem(ConfigurationNode node) {
        if (node.virtual() || !node.isMap()) {
            return Optional.empty();
        }
        String material = node.node("material").getString("");
        if (material.isBlank()) {
            return Optional.empty();
        }
        int amount = Math.max(1, node.node("amount").getInt(1));
        Optional<String> name = optionalString(node.node("name"));
        List<String> lore = strings(node.node("lore"));
        return Optional.of(new ItemReward(material.strip(), amount, name, lore));
    }

    private static OptionalLong positiveLong(ConfigurationNode node) {
        if (node.virtual()) {
            return OptionalLong.empty();
        }
        long value = node.getLong(0L);
        return value > 0 ? OptionalLong.of(value) : OptionalLong.empty();
    }

    private static int clampChance(int chance) {
        return Math.max(0, Math.min(100, chance));
    }

    private static Optional<String> optionalString(ConfigurationNode node) {
        @Nullable String raw = node.getString();
        return raw == null || raw.isBlank() ? Optional.empty() : Optional.of(raw.strip());
    }

    private static List<String> strings(ConfigurationNode node) {
        if (node.virtual() || !node.isList()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (ConfigurationNode child : node.childrenList()) {
            @Nullable String value = child.getString();
            if (value != null && !value.isBlank()) {
                values.add(value.strip());
            }
        }
        return List.copyOf(values);
    }
}
