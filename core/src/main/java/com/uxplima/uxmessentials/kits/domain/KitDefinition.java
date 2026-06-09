package com.uxplima.uxmessentials.kits.domain;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One operator-curated kit: its {@link KitId}, the {@link KitItem stacks} it grants, the cooldown between
 * claims, whether it may be claimed only once ({@code oneTime}), whether it is gated behind the per-kit
 * permission node, and the optional {@link KitCost}. Each kit is defined in its own
 * {@code modules/kits/kits/<id>.conf} file and loaded into this value object; a kit is never mutated in
 * place — an edit produces a new definition the repository overwrites the kit's file with.
 *
 * <p>The cooldown is the default tier when the player holds no numbered {@code uxmessentials.kit.cooldown.
 * <seconds>} node; the {@code Cooldowns} port resolves the effective wait per claim against it. The
 * {@code oneTime} flag is enforced by a persisted claim stamp (PDC), independent of the cooldown clock: a
 * one-time kit is consumed forever after the first claim, a repeatable kit is merely rate-limited by its
 * cooldown. The {@code permission} flag, when set, requires {@link #permissionNode()} on top of the base
 * {@code uxmessentials.kit.use} command node; {@code customPermission}, when present, is consulted in place
 * of the default per-kit node so several kits can share one permission.
 *
 * <p>A kit may carry per-rank {@link KitVariant variants}: at claim, list, and preview time the best variant
 * whose permission the viewer holds replaces the base items (and optionally the cooldown and cost), so one
 * {@code /kit daily} can hand richer loot to higher ranks. The {@code preview} flag lets the browse menu open
 * a read-only preview of the kit on right-click (default on; disable it for mystery kits); {@code closeOnClaim}
 * closes the browse menu after a successful claim instead of refreshing it.
 *
 * @param id the kit's canonical id
 * @param items the stacks the kit grants, in definition order
 * @param cooldown the default wait between claims when no tier node matches; {@link Duration#ZERO} for none
 * @param oneTime whether the kit may be claimed only once per player (a persisted one-time stamp)
 * @param permission whether the kit additionally requires the per-kit permission node
 * @param cost the price to claim the kit; {@link KitCost#free()} when there is no charge
 * @param displayName custom MiniMessage-formatted name for displaying in menus
 * @param displayMaterial custom item type (material name) for displaying in menus
 * @param displayLore custom lore lines for displaying in menus
 * @param commands commands to execute on successful claim
 * @param sound sound to play on successful claim
 * @param particles particles to spawn on successful claim
 * @param customPermission a permission node to gate the kit with in place of {@code uxmessentials.kit.<id>}
 * @param variants per-rank variants, ordered best-first; empty for today's single-variant behaviour
 * @param preview whether the browse menu may open a read-only preview of this kit (default on)
 * @param closeOnClaim whether a successful browse-menu claim closes the menu instead of refreshing it
 * @param requirements opaque claim conditions (placeholder comparisons) evaluated before the cost; empty for none
 * @param requirementsMaterial display material shown in the browse menu when the viewer fails requirements
 * @param requirementsName display name shown in the browse menu when the viewer fails requirements
 * @param requirementsLore display lore shown in the browse menu when the viewer fails requirements
 */
public record KitDefinition(
        KitId id,
        List<KitItem> items,
        Duration cooldown,
        boolean oneTime,
        boolean permission,
        KitCost cost,
        Optional<String> displayName,
        Optional<String> displayMaterial,
        List<String> displayLore,
        List<String> commands,
        Optional<String> sound,
        Optional<String> particles,
        boolean firstJoin,
        boolean autoEquip,
        Optional<String> categoryId,
        java.math.BigDecimal claimMoney,
        String claimMoneyCurrency,
        java.util.Map<String, Duration> permissionCooldowns,
        int priority,
        Optional<String> noPermissionMaterial,
        Optional<String> noPermissionName,
        List<String> noPermissionLore,
        Optional<String> cooldownMaterial,
        Optional<String> cooldownName,
        List<String> cooldownLore,
        Optional<String> claimedMaterial,
        Optional<String> claimedName,
        List<String> claimedLore,
        Optional<String> unaffordableMaterial,
        Optional<String> unaffordableName,
        List<String> unaffordableLore,
        Optional<String> customPermission,
        List<KitVariant> variants,
        boolean preview,
        boolean closeOnClaim,
        List<KitRequirement> requirements,
        Optional<String> requirementsMaterial,
        Optional<String> requirementsName,
        List<String> requirementsLore) {

    public KitDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(cooldown, "cooldown");
        Objects.requireNonNull(cost, "cost");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(displayMaterial, "displayMaterial");
        Objects.requireNonNull(displayLore, "displayLore");
        Objects.requireNonNull(commands, "commands");
        Objects.requireNonNull(sound, "sound");
        Objects.requireNonNull(particles, "particles");
        Objects.requireNonNull(categoryId, "categoryId");
        Objects.requireNonNull(claimMoney, "claimMoney");
        Objects.requireNonNull(claimMoneyCurrency, "claimMoneyCurrency");
        Objects.requireNonNull(permissionCooldowns, "permissionCooldowns");
        Objects.requireNonNull(noPermissionMaterial, "noPermissionMaterial");
        Objects.requireNonNull(noPermissionName, "noPermissionName");
        Objects.requireNonNull(noPermissionLore, "noPermissionLore");
        Objects.requireNonNull(cooldownMaterial, "cooldownMaterial");
        Objects.requireNonNull(cooldownName, "cooldownName");
        Objects.requireNonNull(cooldownLore, "cooldownLore");
        Objects.requireNonNull(claimedMaterial, "claimedMaterial");
        Objects.requireNonNull(claimedName, "claimedName");
        Objects.requireNonNull(claimedLore, "claimedLore");
        Objects.requireNonNull(unaffordableMaterial, "unaffordableMaterial");
        Objects.requireNonNull(unaffordableName, "unaffordableName");
        Objects.requireNonNull(unaffordableLore, "unaffordableLore");
        Objects.requireNonNull(customPermission, "customPermission");
        Objects.requireNonNull(variants, "variants");
        Objects.requireNonNull(requirements, "requirements");
        Objects.requireNonNull(requirementsMaterial, "requirementsMaterial");
        Objects.requireNonNull(requirementsName, "requirementsName");
        Objects.requireNonNull(requirementsLore, "requirementsLore");
        if (cooldown.isNegative()) {
            throw new IllegalArgumentException("kit cooldown must not be negative: " + cooldown);
        }
        items = List.copyOf(items);
        displayLore = List.copyOf(displayLore);
        commands = List.copyOf(commands);
        permissionCooldowns = java.util.Map.copyOf(permissionCooldowns);
        noPermissionLore = List.copyOf(noPermissionLore);
        cooldownLore = List.copyOf(cooldownLore);
        claimedLore = List.copyOf(claimedLore);
        unaffordableLore = List.copyOf(unaffordableLore);
        variants = List.copyOf(variants);
        requirements = List.copyOf(requirements);
        requirementsLore = List.copyOf(requirementsLore);
    }

    public KitDefinition(
            KitId id,
            List<KitItem> items,
            Duration cooldown,
            boolean oneTime,
            boolean permission,
            KitCost cost,
            Optional<String> displayName,
            Optional<String> displayMaterial,
            List<String> displayLore,
            List<String> commands,
            Optional<String> sound,
            Optional<String> particles,
            boolean firstJoin,
            boolean autoEquip,
            Optional<String> categoryId,
            java.math.BigDecimal claimMoney,
            String claimMoneyCurrency,
            java.util.Map<String, Duration> permissionCooldowns,
            int priority,
            Optional<String> noPermissionMaterial,
            Optional<String> noPermissionName,
            List<String> noPermissionLore,
            Optional<String> cooldownMaterial,
            Optional<String> cooldownName,
            List<String> cooldownLore,
            Optional<String> claimedMaterial,
            Optional<String> claimedName,
            List<String> claimedLore,
            Optional<String> unaffordableMaterial,
            Optional<String> unaffordableName,
            List<String> unaffordableLore) {
        this(
                id,
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
                Optional.empty(),
                List.of(),
                true,
                false,
                List.of(),
                Optional.empty(),
                Optional.empty(),
                List.of());
    }

    public KitDefinition(
            KitId id,
            List<KitItem> items,
            Duration cooldown,
            boolean oneTime,
            boolean permission,
            KitCost cost,
            Optional<String> displayName,
            Optional<String> displayMaterial,
            List<String> displayLore,
            List<String> commands,
            Optional<String> sound,
            Optional<String> particles,
            boolean firstJoin,
            boolean autoEquip,
            Optional<String> categoryId,
            java.math.BigDecimal claimMoney,
            String claimMoneyCurrency,
            java.util.Map<String, Duration> permissionCooldowns,
            int priority) {
        this(
                id,
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
                Optional.empty(),
                Optional.empty(),
                List.of(),
                Optional.empty(),
                Optional.empty(),
                List.of(),
                Optional.empty(),
                Optional.empty(),
                List.of(),
                Optional.empty(),
                Optional.empty(),
                List.of());
    }

    public KitDefinition(
            KitId id,
            List<KitItem> items,
            Duration cooldown,
            boolean oneTime,
            boolean permission,
            KitCost cost,
            Optional<String> displayName,
            Optional<String> displayMaterial,
            List<String> displayLore,
            List<String> commands,
            Optional<String> sound,
            Optional<String> particles,
            boolean firstJoin,
            boolean autoEquip,
            Optional<String> categoryId,
            java.math.BigDecimal claimMoney,
            java.util.Map<String, Duration> permissionCooldowns,
            int priority) {
        this(
                id,
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
                "default",
                permissionCooldowns,
                priority);
    }

    public KitDefinition(
            KitId id,
            List<KitItem> items,
            Duration cooldown,
            boolean oneTime,
            boolean permission,
            KitCost cost,
            Optional<String> displayName,
            Optional<String> displayMaterial,
            List<String> displayLore,
            List<String> commands,
            Optional<String> sound,
            Optional<String> particles,
            boolean firstJoin,
            boolean autoEquip) {
        this(
                id,
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
                Optional.empty(),
                java.math.BigDecimal.ZERO,
                "default",
                java.util.Map.of(),
                0);
    }

    public KitDefinition(
            KitId id,
            List<KitItem> items,
            Duration cooldown,
            boolean oneTime,
            boolean permission,
            KitCost cost,
            Optional<String> displayName,
            Optional<String> displayMaterial,
            List<String> displayLore,
            List<String> commands,
            Optional<String> sound,
            Optional<String> particles) {
        this(
                id,
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
                false,
                false);
    }

    public KitDefinition(
            KitId id, List<KitItem> items, Duration cooldown, boolean oneTime, boolean permission, KitCost cost) {
        this(
                id,
                items,
                cooldown,
                oneTime,
                permission,
                cost,
                Optional.empty(),
                Optional.empty(),
                List.of(),
                List.of(),
                Optional.empty(),
                Optional.empty(),
                false,
                false);
    }

    /** A free, repeatable, ungated kit with the given items and cooldown. */
    public static KitDefinition repeatable(KitId id, List<KitItem> items, Duration cooldown) {
        return new KitDefinition(id, items, cooldown, false, false, KitCost.free());
    }

    /**
     * A copy of this kit with its {@code items} swapped for {@code newItems} and every other setting —
     * cooldown, one-time, permission, cost, every display override, commands, sound, particles, first-join,
     * auto-equip, category, claim money, per-permission cooldowns, priority, custom permission, variants,
     * preview, and close-on-claim — carried through unchanged. The item editor uses this so editing a kit's
     * stacks never silently wipes its other configuration.
     */
    public KitDefinition withItems(List<KitItem> newItems) {
        Objects.requireNonNull(newItems, "newItems");
        return copy(b -> b.items = newItems);
    }

    /** A copy of this kit with its one-time flag set to {@code value}, every other setting preserved. */
    public KitDefinition withOneTime(boolean value) {
        return copy(b -> b.oneTime = value);
    }

    /** A copy of this kit with its permission flag set to {@code value}, every other setting preserved. */
    public KitDefinition withPermission(boolean value) {
        return copy(b -> b.permission = value);
    }

    /** A copy of this kit with its cooldown set to {@code value}, every other setting preserved. */
    public KitDefinition withCooldown(Duration value) {
        Objects.requireNonNull(value, "value");
        return copy(b -> b.cooldown = value);
    }

    /** A copy of this kit with its cost set to {@code value}, every other setting preserved. */
    public KitDefinition withCost(KitCost value) {
        Objects.requireNonNull(value, "value");
        return copy(b -> b.cost = value);
    }

    /** A copy of this kit with its display name set to {@code value}, every other setting preserved. */
    public KitDefinition withDisplayName(Optional<String> value) {
        Objects.requireNonNull(value, "value");
        return copy(b -> b.displayName = value);
    }

    /** A copy of this kit with its display material set to {@code value}, every other setting preserved. */
    public KitDefinition withDisplayMaterial(Optional<String> value) {
        Objects.requireNonNull(value, "value");
        return copy(b -> b.displayMaterial = value);
    }

    /** A copy of this kit with its display lore set to {@code value}, every other setting preserved. */
    public KitDefinition withDisplayLore(List<String> value) {
        Objects.requireNonNull(value, "value");
        return copy(b -> b.displayLore = value);
    }

    /** A copy of this kit with its claim commands set to {@code value}, every other setting preserved. */
    public KitDefinition withCommands(List<String> value) {
        Objects.requireNonNull(value, "value");
        return copy(b -> b.commands = value);
    }

    /** A copy of this kit with its first-join flag set to {@code value}, every other setting preserved. */
    public KitDefinition withFirstJoin(boolean value) {
        return copy(b -> b.firstJoin = value);
    }

    /** A copy of this kit with its auto-equip flag set to {@code value}, every other setting preserved. */
    public KitDefinition withAutoEquip(boolean value) {
        return copy(b -> b.autoEquip = value);
    }

    /** A copy of this kit with its category set to {@code value}, every other setting preserved. */
    public KitDefinition withCategoryId(Optional<String> value) {
        Objects.requireNonNull(value, "value");
        return copy(b -> b.categoryId = value);
    }

    /** A copy of this kit with its preview flag set to {@code value}, every other setting preserved. */
    public KitDefinition withPreview(boolean value) {
        return copy(b -> b.preview = value);
    }

    /** A copy of this kit with its close-on-claim flag set to {@code value}, every other setting preserved. */
    public KitDefinition withCloseOnClaim(boolean value) {
        return copy(b -> b.closeOnClaim = value);
    }

    /** A copy of this kit with its custom permission set to {@code value}, every other setting preserved. */
    public KitDefinition withCustomPermission(Optional<String> value) {
        Objects.requireNonNull(value, "value");
        return copy(b -> b.customPermission = value);
    }

    /** A copy of this kit with its variants set to {@code value}, every other setting preserved. */
    public KitDefinition withVariants(List<KitVariant> value) {
        Objects.requireNonNull(value, "value");
        return copy(b -> b.variants = value);
    }

    /** A copy of this kit with its claim requirements set to {@code value}, every other setting preserved. */
    public KitDefinition withRequirements(List<KitRequirement> value) {
        Objects.requireNonNull(value, "value");
        return copy(b -> b.requirements = value);
    }

    private KitDefinition copy(java.util.function.Consumer<KitDefinitionFields> mutator) {
        KitDefinitionFields fields = new KitDefinitionFields(this);
        mutator.accept(fields);
        return fields.build();
    }

    /** True when claiming this kit consumes it forever (a one-time kit). */
    public boolean isOneTime() {
        return oneTime;
    }

    /** True when this kit requires a permission node beyond the base command node. */
    public boolean requiresPermission() {
        return permission;
    }

    /**
     * The permission node that gates this kit: the kit's {@code customPermission} when set, else the default
     * per-kit node {@code uxmessentials.kit.<id>}. Resolved here so several kits can share one permission.
     */
    public String permissionNode() {
        return customPermission.orElseGet(() -> id.permissionNode());
    }

    /** True when the kit sets a cost the economy gate should charge (a non-free price). */
    public boolean hasCost() {
        return !cost.isFree();
    }

    /** True when the kit carries claim requirements the gate must evaluate before charging. */
    public boolean hasRequirements() {
        return !requirements.isEmpty();
    }

    /** The cooldown in whole seconds, the unit the {@code Cooldowns} port resolves tiers in. */
    public long cooldownSeconds() {
        return cooldown.toSeconds();
    }
}
