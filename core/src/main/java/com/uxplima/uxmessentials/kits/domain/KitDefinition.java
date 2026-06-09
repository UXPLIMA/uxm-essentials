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
 * cooldown. The {@code permission} flag, when set, requires {@link KitId#permissionNode()} on top of the
 * base {@code uxmessentials.kit.use} command node.
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
        List<String> unaffordableLore) {

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
     * auto-equip, category, claim money, per-permission cooldowns and priority — carried through unchanged.
     * The item editor uses this so editing a kit's stacks never silently wipes its other configuration.
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

    private KitDefinition copy(java.util.function.Consumer<Fields> mutator) {
        Fields fields = new Fields(this);
        mutator.accept(fields);
        return fields.build();
    }

    /** A mutable carrier used only to express single-field copies; never escapes {@link #copy}. */
    private static final class Fields {
        private KitId id;
        private List<KitItem> items;
        private Duration cooldown;
        private boolean oneTime;
        private boolean permission;
        private KitCost cost;
        private Optional<String> displayName;
        private Optional<String> displayMaterial;
        private List<String> displayLore;
        private List<String> commands;
        private Optional<String> sound;
        private Optional<String> particles;
        private boolean firstJoin;
        private boolean autoEquip;
        private Optional<String> categoryId;
        private java.math.BigDecimal claimMoney;
        private String claimMoneyCurrency;
        private java.util.Map<String, Duration> permissionCooldowns;
        private int priority;
        private Optional<String> noPermissionMaterial;
        private Optional<String> noPermissionName;
        private List<String> noPermissionLore;
        private Optional<String> cooldownMaterial;
        private Optional<String> cooldownName;
        private List<String> cooldownLore;
        private Optional<String> claimedMaterial;
        private Optional<String> claimedName;
        private List<String> claimedLore;
        private Optional<String> unaffordableMaterial;
        private Optional<String> unaffordableName;
        private List<String> unaffordableLore;

        private Fields(KitDefinition k) {
            this.id = k.id;
            this.items = k.items;
            this.cooldown = k.cooldown;
            this.oneTime = k.oneTime;
            this.permission = k.permission;
            this.cost = k.cost;
            this.displayName = k.displayName;
            this.displayMaterial = k.displayMaterial;
            this.displayLore = k.displayLore;
            this.commands = k.commands;
            this.sound = k.sound;
            this.particles = k.particles;
            this.firstJoin = k.firstJoin;
            this.autoEquip = k.autoEquip;
            this.categoryId = k.categoryId;
            this.claimMoney = k.claimMoney;
            this.claimMoneyCurrency = k.claimMoneyCurrency;
            this.permissionCooldowns = k.permissionCooldowns;
            this.priority = k.priority;
            this.noPermissionMaterial = k.noPermissionMaterial;
            this.noPermissionName = k.noPermissionName;
            this.noPermissionLore = k.noPermissionLore;
            this.cooldownMaterial = k.cooldownMaterial;
            this.cooldownName = k.cooldownName;
            this.cooldownLore = k.cooldownLore;
            this.claimedMaterial = k.claimedMaterial;
            this.claimedName = k.claimedName;
            this.claimedLore = k.claimedLore;
            this.unaffordableMaterial = k.unaffordableMaterial;
            this.unaffordableName = k.unaffordableName;
            this.unaffordableLore = k.unaffordableLore;
        }

        private KitDefinition build() {
            return new KitDefinition(
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
                    unaffordableLore);
        }
    }

    /** True when claiming this kit consumes it forever (a one-time kit). */
    public boolean isOneTime() {
        return oneTime;
    }

    /** True when this kit requires the per-kit permission node beyond the base command node. */
    public boolean requiresPermission() {
        return permission;
    }

    /** True when the kit sets a cost the economy gate should charge (a non-free price). */
    public boolean hasCost() {
        return !cost.isFree();
    }

    /** The cooldown in whole seconds, the unit the {@code Cooldowns} port resolves tiers in. */
    public long cooldownSeconds() {
        return cooldown.toSeconds();
    }
}
