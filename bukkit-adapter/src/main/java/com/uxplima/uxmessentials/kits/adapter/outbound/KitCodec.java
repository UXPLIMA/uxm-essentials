package com.uxplima.uxmessentials.kits.adapter.outbound;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.uxplima.uxmessentials.kits.domain.KitCost;
import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.kits.domain.KitId;
import com.uxplima.uxmessentials.kits.domain.KitItem;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;

/**
 * Reads and writes one kit as a {@link KitDefinition}. Each kit lives in its own {@code <id>.conf} under
 * {@code modules/kits/kits/}, whose root node <em>is</em> the kit:
 *
 * <pre>{@code
 * # modules/kits/kits/starter.conf
 * cooldown = 0          # seconds between claims; 0 = no cooldown
 * one-time = true       # claimable once per player
 * permission = false    # require uxmessentials.kit.<id>
 * cost = 0              # claim price; charged only when economy is wired
 * items = [ "<base64>", { data = "<base64>", amount = 16 } ]
 * requirements = [      # placeholder claim conditions; evaluated through PlaceholderAPI before the cost
 *   "%player_level% >= 10",
 *   "%essentials_playtime_seconds% >= 3600"
 * ]
 * requirements-material = "BARRIER"   # browse-menu icon shown when the viewer fails the requirements
 * requirements-name = "<red>Locked"   # browse-menu name for that state
 * requirements-lore = [ "<gray>Level up to unlock" ]  # browse-menu lore for that state
 * }</pre>
 *
 * <p>Each {@code requirements} entry is split into a {@code KitRequirement(left, operator, right)} on the first
 * of the operators {@code == != >= <= > <}; spacing is tolerated and a malformed entry (no operator or a blank
 * side) is skipped rather than failing the kit. The operands stay opaque raw strings here — the adapter never
 * resolves the placeholders; the {@code RequirementEvaluator} does, on the claim/render thread.
 *
 * The same codec also reads each entry of the previous {@code kits.conf} monolith — a {@code kits { <id> {
 * ... } }} tree — which the repository imports once on first load and splits into per-file kits. An item is
 * either a bare Base64 string (its serialized form carries the amount) or a {@code {data, amount}} map for
 * readability. A read that cannot produce a valid definition returns empty so the repository skips the kit
 * rather than failing the whole load.
 */
@NullMarked
final class KitCodec {

    private KitCodec() {}

    /** Parse the node {@code node} for id {@code id} into a definition, or empty when it is malformed. */
    static Optional<KitDefinition> read(String id, ConfigurationNode node) {
        try {
            KitId kitId = KitId.of(id);
            List<KitItem> items = readItems(node.node("items"));
            Duration cooldown =
                    Duration.ofSeconds(Math.max(0L, node.node("cooldown").getLong(0L)));
            boolean oneTime = node.node("one-time").getBoolean(false);
            boolean permission = node.node("permission").getBoolean(false);
            KitCost cost = readCost(node.node("cost"));

            Optional<String> displayName =
                    Optional.ofNullable(node.node("display-name").getString());
            Optional<String> displayMaterial =
                    Optional.ofNullable(node.node("display-material").getString());
            List<String> displayLore = strings(node.node("display-lore"));
            List<String> commands = strings(node.node("commands"));
            Optional<String> sound = Optional.ofNullable(node.node("sound").getString());
            Optional<String> particles =
                    Optional.ofNullable(node.node("particles").getString());

            boolean firstJoin = node.node("first-join").getBoolean(false);
            boolean autoEquip = node.node("auto-equip").getBoolean(false);

            Optional<String> categoryId =
                    Optional.ofNullable(node.node("category-id").getString());
            BigDecimal claimMoney = BigDecimal.ZERO;
            String claimMoneyCurrency = "default";
            String rawClaimMoney = node.node("claim-money").getString("");
            if (rawClaimMoney != null && !rawClaimMoney.isBlank()) {
                try {
                    java.util.List<String> parts = com.google.common.base.Splitter.on(' ')
                            .omitEmptyStrings()
                            .trimResults()
                            .splitToList(rawClaimMoney.strip());
                    if (!parts.isEmpty()) {
                        claimMoney = new BigDecimal(parts.get(0));
                        if (parts.size() > 1) {
                            claimMoneyCurrency = parts.get(1).toLowerCase(java.util.Locale.ROOT);
                        }
                    }
                } catch (Exception ignored) {
                    // Ignore parsing errors for malformed numbers
                }
            }
            String explicitCurrency = node.node("claim-money-currency").getString("");
            if (explicitCurrency != null && !explicitCurrency.isBlank()) {
                claimMoneyCurrency = explicitCurrency.strip().toLowerCase(java.util.Locale.ROOT);
            }
            int priority = node.node("priority").getInt(0);

            Optional<String> noPermissionMaterial =
                    Optional.ofNullable(node.node("no-permission-material").getString());
            Optional<String> noPermissionName =
                    Optional.ofNullable(node.node("no-permission-name").getString());
            List<String> noPermissionLore = strings(node.node("no-permission-lore"));

            Optional<String> cooldownMaterial =
                    Optional.ofNullable(node.node("cooldown-material").getString());
            Optional<String> cooldownName =
                    Optional.ofNullable(node.node("cooldown-name").getString());
            List<String> cooldownLore = strings(node.node("cooldown-lore"));

            Optional<String> claimedMaterial =
                    Optional.ofNullable(node.node("claimed-material").getString());
            Optional<String> claimedName =
                    Optional.ofNullable(node.node("claimed-name").getString());
            List<String> claimedLore = strings(node.node("claimed-lore"));

            Optional<String> unaffordableMaterial =
                    Optional.ofNullable(node.node("unaffordable-material").getString());
            Optional<String> unaffordableName =
                    Optional.ofNullable(node.node("unaffordable-name").getString());
            List<String> unaffordableLore = strings(node.node("unaffordable-lore"));

            java.util.Map<String, Duration> permissionCooldowns = new java.util.HashMap<>();
            ConfigurationNode cooldownsNode = node.node("permission-cooldowns");
            if (!cooldownsNode.virtual() && cooldownsNode.isMap()) {
                for (java.util.Map.Entry<Object, ? extends ConfigurationNode> entry :
                        cooldownsNode.childrenMap().entrySet()) {
                    String group = String.valueOf(entry.getKey());
                    long seconds = entry.getValue().getLong(-1L);
                    if (seconds >= 0) {
                        permissionCooldowns.put(group, Duration.ofSeconds(seconds));
                    }
                }
            }

            String rawCustomPermission = node.node("permission-node").getString("");
            Optional<String> customPermission = rawCustomPermission == null || rawCustomPermission.isBlank()
                    ? Optional.empty()
                    : Optional.of(rawCustomPermission.strip());
            List<com.uxplima.uxmessentials.kits.domain.KitVariant> variants = readVariants(node.node("variants"));
            boolean preview = node.node("preview").getBoolean(true);
            boolean closeOnClaim = node.node("close-on-claim").getBoolean(false);

            List<com.uxplima.uxmessentials.kits.domain.KitRequirement> requirements =
                    readRequirements(node.node("requirements"));
            Optional<String> requirementsMaterial =
                    Optional.ofNullable(node.node("requirements-material").getString());
            Optional<String> requirementsName =
                    Optional.ofNullable(node.node("requirements-name").getString());
            List<String> requirementsLore = strings(node.node("requirements-lore"));

            return Optional.of(new KitDefinition(
                    kitId,
                    items,
                    cooldown,
                    oneTime,
                    permission,
                    cost,
                    displayName,
                    displayMaterial,
                    displayLore,
                    commands,
                    sound,
                    particles,
                    firstJoin,
                    autoEquip,
                    categoryId,
                    claimMoney,
                    claimMoneyCurrency,
                    permissionCooldowns,
                    priority,
                    noPermissionMaterial,
                    noPermissionName,
                    noPermissionLore,
                    cooldownMaterial,
                    cooldownName,
                    cooldownLore,
                    claimedMaterial,
                    claimedName,
                    claimedLore,
                    unaffordableMaterial,
                    unaffordableName,
                    unaffordableLore,
                    customPermission,
                    variants,
                    preview,
                    closeOnClaim,
                    requirements,
                    requirementsMaterial,
                    requirementsName,
                    requirementsLore));
        } catch (RuntimeException malformed) {
            return Optional.empty();
        }
    }

    /** Write {@code definition} into {@code node} in the documented shape. */
    static void write(ConfigurationNode node, KitDefinition definition) throws ConfigurateException {
        node.node("cooldown").set(definition.cooldownSeconds());
        node.node("one-time").set(definition.oneTime());
        node.node("permission").set(definition.permission());
        node.node("cost")
                .set(definition.cost().amount().toPlainString() + " "
                        + definition.cost().currencyId());
        node.node("first-join").set(definition.firstJoin());
        node.node("auto-equip").set(definition.autoEquip());

        if (definition.categoryId().isPresent()) {
            node.node("category-id").set(definition.categoryId().get());
        } else {
            node.node("category-id").set(null);
        }
        node.node("claim-money").set(definition.claimMoney().toPlainString() + " " + definition.claimMoneyCurrency());
        node.node("priority").set(definition.priority());

        node.node("permission-node").set(definition.customPermission().orElse(null));
        node.node("preview").set(definition.preview() ? null : false);
        node.node("close-on-claim").set(definition.closeOnClaim() ? true : null);
        writeVariants(node.node("variants"), definition.variants());

        node.node("no-permission-material")
                .set(definition.noPermissionMaterial().orElse(null));
        node.node("no-permission-name").set(definition.noPermissionName().orElse(null));
        node.node("no-permission-lore")
                .set(definition.noPermissionLore().isEmpty() ? null : definition.noPermissionLore());

        node.node("cooldown-material").set(definition.cooldownMaterial().orElse(null));
        node.node("cooldown-name").set(definition.cooldownName().orElse(null));
        node.node("cooldown-lore").set(definition.cooldownLore().isEmpty() ? null : definition.cooldownLore());

        node.node("claimed-material").set(definition.claimedMaterial().orElse(null));
        node.node("claimed-name").set(definition.claimedName().orElse(null));
        node.node("claimed-lore").set(definition.claimedLore().isEmpty() ? null : definition.claimedLore());

        node.node("unaffordable-material").set(definition.unaffordableMaterial().orElse(null));
        node.node("unaffordable-name").set(definition.unaffordableName().orElse(null));
        node.node("unaffordable-lore")
                .set(definition.unaffordableLore().isEmpty() ? null : definition.unaffordableLore());

        writeRequirements(node.node("requirements"), definition.requirements());
        node.node("requirements-material").set(definition.requirementsMaterial().orElse(null));
        node.node("requirements-name").set(definition.requirementsName().orElse(null));
        node.node("requirements-lore")
                .set(definition.requirementsLore().isEmpty() ? null : definition.requirementsLore());

        node.node("permission-cooldowns").set(null);
        if (!definition.permissionCooldowns().isEmpty()) {
            ConfigurationNode cooldownsNode = node.node("permission-cooldowns");
            for (java.util.Map.Entry<String, Duration> entry :
                    definition.permissionCooldowns().entrySet()) {
                cooldownsNode.node(entry.getKey()).set(entry.getValue().toSeconds());
            }
        }

        if (definition.displayName().isPresent()) {
            node.node("display-name").set(definition.displayName().get());
        } else {
            node.node("display-name").set(null);
        }
        if (definition.displayMaterial().isPresent()) {
            node.node("display-material").set(definition.displayMaterial().get());
        } else {
            node.node("display-material").set(null);
        }
        if (!definition.displayLore().isEmpty()) {
            node.node("display-lore").set(definition.displayLore());
        } else {
            node.node("display-lore").set(null);
        }
        if (!definition.commands().isEmpty()) {
            node.node("commands").set(definition.commands());
        } else {
            node.node("commands").set(null);
        }
        if (definition.sound().isPresent()) {
            node.node("sound").set(definition.sound().get());
        } else {
            node.node("sound").set(null);
        }
        if (definition.particles().isPresent()) {
            node.node("particles").set(definition.particles().get());
        } else {
            node.node("particles").set(null);
        }

        writeItems(node.node("items"), definition.items());
    }

    private static List<String> strings(ConfigurationNode node) {
        if (node.virtual() || !node.isList()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (ConfigurationNode child : node.childrenList()) {
            String value = child.getString();
            if (value != null && !value.isBlank()) {
                values.add(value.strip());
            }
        }
        return List.copyOf(values);
    }

    private static List<KitItem> readItems(ConfigurationNode node) {
        List<KitItem> items = new ArrayList<>();
        for (ConfigurationNode child : node.childrenList()) {
            readItem(child).ifPresent(items::add);
        }
        return items;
    }

    /** The inventory slot the off-hand maps to in a {@code PlayerInventory}; {@code offhand = true} is its alias. */
    private static final int OFF_HAND_SLOT = 40;

    private static Optional<KitItem> readItem(ConfigurationNode child) {
        if (child.isMap()) {
            String data = child.node("data").getString("");
            int amount = Math.max(1, child.node("amount").getInt(1));
            Optional<Integer> slot = readSlot(child);
            return data.isBlank() ? Optional.empty() : Optional.of(KitItem.of(data, amount, slot));
        }
        String raw = child.getString("");
        return raw.isBlank() ? Optional.empty() : Optional.of(KitItem.of(raw, 1, Optional.empty()));
    }

    private static Optional<Integer> readSlot(ConfigurationNode child) {
        if (child.node("offhand").getBoolean(false)) {
            return Optional.of(OFF_HAND_SLOT);
        }
        return child.node("slot").virtual()
                ? Optional.empty()
                : Optional.of(child.node("slot").getInt());
    }

    private static List<com.uxplima.uxmessentials.kits.domain.KitRequirement> readRequirements(ConfigurationNode node) {
        if (node.virtual() || !node.isList()) {
            return List.of();
        }
        List<com.uxplima.uxmessentials.kits.domain.KitRequirement> requirements = new ArrayList<>();
        for (ConfigurationNode child : node.childrenList()) {
            String entry = child.getString();
            if (entry == null || entry.isBlank()) {
                continue;
            }
            // A malformed entry (no recognised operator or a blank side) is skipped, not fatal.
            com.uxplima.uxmessentials.kits.domain.KitRequirement.parse(entry).ifPresent(requirements::add);
        }
        return List.copyOf(requirements);
    }

    private static List<com.uxplima.uxmessentials.kits.domain.KitVariant> readVariants(ConfigurationNode node) {
        if (node.virtual() || !node.isMap()) {
            return List.of();
        }
        List<com.uxplima.uxmessentials.kits.domain.KitVariant> variants = new ArrayList<>();
        for (java.util.Map.Entry<Object, ? extends ConfigurationNode> entry :
                node.childrenMap().entrySet()) {
            readVariant(entry.getValue()).ifPresent(variants::add);
        }
        return List.copyOf(variants);
    }

    private static Optional<com.uxplima.uxmessentials.kits.domain.KitVariant> readVariant(ConfigurationNode node) {
        String permission = node.node("permission").getString("");
        if (permission == null || permission.isBlank()) {
            return Optional.empty();
        }
        List<KitItem> items = readItems(node.node("items"));
        if (items.isEmpty()) {
            return Optional.empty();
        }
        Optional<Duration> cooldown = node.node("cooldown").virtual()
                ? Optional.empty()
                : Optional.of(
                        Duration.ofSeconds(Math.max(0L, node.node("cooldown").getLong(0L))));
        Optional<KitCost> cost =
                node.node("cost").virtual() ? Optional.empty() : Optional.of(readCost(node.node("cost")));
        return Optional.of(
                new com.uxplima.uxmessentials.kits.domain.KitVariant(permission.strip(), items, cooldown, cost));
    }

    private static void writeVariants(
            ConfigurationNode node, List<com.uxplima.uxmessentials.kits.domain.KitVariant> variants)
            throws ConfigurateException {
        node.set(null);
        int index = 0;
        for (com.uxplima.uxmessentials.kits.domain.KitVariant variant : variants) {
            ConfigurationNode child = node.node("tier" + index++);
            child.node("permission").set(variant.permission());
            if (variant.cooldown().isPresent()) {
                child.node("cooldown").set(variant.cooldown().get().toSeconds());
            }
            if (variant.cost().isPresent()) {
                child.node("cost")
                        .set(variant.cost().get().amount().toPlainString() + " "
                                + variant.cost().get().currencyId());
            }
            writeItems(child.node("items"), variant.items());
        }
    }

    private static void writeRequirements(
            ConfigurationNode node, List<com.uxplima.uxmessentials.kits.domain.KitRequirement> requirements)
            throws ConfigurateException {
        if (requirements.isEmpty()) {
            node.set(null);
            return;
        }
        node.setList(
                String.class,
                requirements.stream()
                        .map(com.uxplima.uxmessentials.kits.domain.KitRequirement::asText)
                        .toList());
    }

    private static void writeItems(ConfigurationNode node, List<KitItem> items) throws ConfigurateException {
        node.set(null);
        for (KitItem item : items) {
            ConfigurationNode child = node.appendListNode();
            child.node("data").set(item.data());
            child.node("amount").set(item.amount());
            if (item.slot().isPresent()) {
                if (item.slot().get() == OFF_HAND_SLOT) {
                    child.node("offhand").set(true);
                } else {
                    child.node("slot").set(item.slot().get());
                }
            }
        }
    }

    private static KitCost readCost(ConfigurationNode node) {
        String raw = node.getString("");
        if (raw.isBlank()) {
            return KitCost.free();
        }
        try {
            java.util.List<String> parts = com.google.common.base.Splitter.on(' ')
                    .omitEmptyStrings()
                    .trimResults()
                    .splitToList(raw.strip());
            if (parts.isEmpty()) {
                return KitCost.free();
            }
            BigDecimal amount = new BigDecimal(parts.get(0));
            if (amount.signum() <= 0) {
                return KitCost.free();
            }
            String currency = parts.size() > 1 ? parts.get(1).toLowerCase(java.util.Locale.ROOT) : "default";
            return KitCost.of(amount, currency);
        } catch (Exception notANumber) {
            return KitCost.free();
        }
    }
}
