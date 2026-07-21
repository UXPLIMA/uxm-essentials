package com.uxplima.uxmessentials.kits.domain;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * The single construction path for an immutable {@link KitDefinition}. Every component starts at a sensible
 * default, so a caller sets only the fields it cares about and calls {@link #build()}, which routes through the
 * canonical {@code KitDefinition} constructor so every null-check and invariant still fires. The codec that reads
 * a kit from disk sets every field; a test or command that wants a bare kit sets a handful and lets the rest
 * default. The defaults match what the old telescoping constructors defaulted: an empty {@link ItemDisplay} for
 * each icon state, {@link KitCost#free()}, {@link Duration#ZERO} cooldown, {@code preview} on, {@code onFull}
 * dropping the overflow, and every other flag off.
 *
 * <p>{@link KitDefinition#toBuilder()} seeds a builder from an existing kit so a {@code with…} copy changes one
 * field and rebuilds without re-listing the thirty-odd unchanged components at the call site.
 */
public final class KitDefinitionBuilder {

    private @Nullable KitId id;
    private List<KitItem> items = List.of();
    private Duration cooldown = Duration.ZERO;
    private boolean oneTime;
    private boolean permission;
    private KitCost cost = KitCost.free();
    private ItemDisplay display = ItemDisplay.empty();
    private List<String> commands = List.of();
    private Optional<String> sound = Optional.empty();
    private Optional<String> particles = Optional.empty();
    private boolean firstJoin;
    private boolean autoEquip;
    private Optional<String> categoryId = Optional.empty();
    private BigDecimal claimMoney = BigDecimal.ZERO;
    private String claimMoneyCurrency = "default";
    private Map<String, Duration> permissionCooldowns = Map.of();
    private int priority;
    private ItemDisplay noPermission = ItemDisplay.empty();
    private ItemDisplay cooldownDisplay = ItemDisplay.empty();
    private ItemDisplay claimed = ItemDisplay.empty();
    private ItemDisplay unaffordable = ItemDisplay.empty();
    private Optional<String> customPermission = Optional.empty();
    private List<KitVariant> variants = List.of();
    private boolean preview = true;
    private boolean closeOnClaim;
    private List<KitRequirement> requirements = List.of();
    private ItemDisplay requirementsDisplay = ItemDisplay.empty();
    private List<KitAction> claimActions = List.of();
    private List<KitAction> denyActions = List.of();
    private KitSchedule schedule = KitSchedule.always();
    private int stockLimit;
    private boolean parsePlaceholders;
    private KitFullPolicy onFull = KitFullPolicy.DROP;
    private boolean unlockOnce;

    KitDefinitionBuilder() {}

    KitDefinitionBuilder(KitDefinition k) {
        Objects.requireNonNull(k, "k");
        this.id = k.id();
        this.items = k.items();
        this.cooldown = k.cooldown();
        this.oneTime = k.oneTime();
        this.permission = k.permission();
        this.cost = k.cost();
        this.display = k.display();
        this.commands = k.commands();
        this.sound = k.sound();
        this.particles = k.particles();
        this.firstJoin = k.firstJoin();
        this.autoEquip = k.autoEquip();
        this.categoryId = k.categoryId();
        this.claimMoney = k.claimMoney();
        this.claimMoneyCurrency = k.claimMoneyCurrency();
        this.permissionCooldowns = k.permissionCooldowns();
        this.priority = k.priority();
        this.noPermission = k.noPermission();
        this.cooldownDisplay = k.cooldownDisplay();
        this.claimed = k.claimed();
        this.unaffordable = k.unaffordable();
        this.customPermission = k.customPermission();
        this.variants = k.variants();
        this.preview = k.preview();
        this.closeOnClaim = k.closeOnClaim();
        this.requirements = k.requirements();
        this.requirementsDisplay = k.requirementsDisplay();
        this.claimActions = k.claimActions();
        this.denyActions = k.denyActions();
        this.schedule = k.schedule();
        this.stockLimit = k.stockLimit();
        this.parsePlaceholders = k.parsePlaceholders();
        this.onFull = k.onFull();
        this.unlockOnce = k.unlockOnce();
    }

    public KitDefinitionBuilder id(KitId value) {
        this.id = value;
        return this;
    }

    public KitDefinitionBuilder items(List<KitItem> value) {
        this.items = value;
        return this;
    }

    public KitDefinitionBuilder cooldown(Duration value) {
        this.cooldown = value;
        return this;
    }

    public KitDefinitionBuilder oneTime(boolean value) {
        this.oneTime = value;
        return this;
    }

    public KitDefinitionBuilder permission(boolean value) {
        this.permission = value;
        return this;
    }

    public KitDefinitionBuilder cost(KitCost value) {
        this.cost = value;
        return this;
    }

    public KitDefinitionBuilder display(ItemDisplay value) {
        this.display = value;
        return this;
    }

    public KitDefinitionBuilder commands(List<String> value) {
        this.commands = value;
        return this;
    }

    public KitDefinitionBuilder sound(Optional<String> value) {
        this.sound = value;
        return this;
    }

    public KitDefinitionBuilder particles(Optional<String> value) {
        this.particles = value;
        return this;
    }

    public KitDefinitionBuilder firstJoin(boolean value) {
        this.firstJoin = value;
        return this;
    }

    public KitDefinitionBuilder autoEquip(boolean value) {
        this.autoEquip = value;
        return this;
    }

    public KitDefinitionBuilder categoryId(Optional<String> value) {
        this.categoryId = value;
        return this;
    }

    public KitDefinitionBuilder claimMoney(BigDecimal value) {
        this.claimMoney = value;
        return this;
    }

    public KitDefinitionBuilder claimMoneyCurrency(String value) {
        this.claimMoneyCurrency = value;
        return this;
    }

    public KitDefinitionBuilder permissionCooldowns(Map<String, Duration> value) {
        this.permissionCooldowns = value;
        return this;
    }

    public KitDefinitionBuilder priority(int value) {
        this.priority = value;
        return this;
    }

    public KitDefinitionBuilder noPermission(ItemDisplay value) {
        this.noPermission = value;
        return this;
    }

    public KitDefinitionBuilder cooldownDisplay(ItemDisplay value) {
        this.cooldownDisplay = value;
        return this;
    }

    public KitDefinitionBuilder claimed(ItemDisplay value) {
        this.claimed = value;
        return this;
    }

    public KitDefinitionBuilder unaffordable(ItemDisplay value) {
        this.unaffordable = value;
        return this;
    }

    public KitDefinitionBuilder customPermission(Optional<String> value) {
        this.customPermission = value;
        return this;
    }

    public KitDefinitionBuilder variants(List<KitVariant> value) {
        this.variants = value;
        return this;
    }

    public KitDefinitionBuilder preview(boolean value) {
        this.preview = value;
        return this;
    }

    public KitDefinitionBuilder closeOnClaim(boolean value) {
        this.closeOnClaim = value;
        return this;
    }

    public KitDefinitionBuilder requirements(List<KitRequirement> value) {
        this.requirements = value;
        return this;
    }

    public KitDefinitionBuilder requirementsDisplay(ItemDisplay value) {
        this.requirementsDisplay = value;
        return this;
    }

    public KitDefinitionBuilder claimActions(List<KitAction> value) {
        this.claimActions = value;
        return this;
    }

    public KitDefinitionBuilder denyActions(List<KitAction> value) {
        this.denyActions = value;
        return this;
    }

    public KitDefinitionBuilder schedule(KitSchedule value) {
        this.schedule = value;
        return this;
    }

    public KitDefinitionBuilder stockLimit(int value) {
        this.stockLimit = value;
        return this;
    }

    public KitDefinitionBuilder parsePlaceholders(boolean value) {
        this.parsePlaceholders = value;
        return this;
    }

    public KitDefinitionBuilder onFull(KitFullPolicy value) {
        this.onFull = value;
        return this;
    }

    public KitDefinitionBuilder unlockOnce(boolean value) {
        this.unlockOnce = value;
        return this;
    }

    public KitDefinition build() {
        KitId checkedId = Objects.requireNonNull(id, "id");
        return new KitDefinition(
                checkedId,
                items,
                cooldown,
                oneTime,
                permission,
                cost,
                display,
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
                noPermission,
                cooldownDisplay,
                claimed,
                unaffordable,
                customPermission,
                variants,
                preview,
                closeOnClaim,
                requirements,
                requirementsDisplay,
                claimActions,
                denyActions,
                schedule,
                stockLimit,
                parsePlaceholders,
                onFull,
                unlockOnce);
    }
}
