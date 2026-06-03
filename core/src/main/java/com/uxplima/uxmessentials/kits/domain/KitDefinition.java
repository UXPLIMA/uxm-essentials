package com.uxplima.uxmessentials.kits.domain;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

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
 */
public record KitDefinition(
        KitId id, List<KitItem> items, Duration cooldown, boolean oneTime, boolean permission, KitCost cost) {

    public KitDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(cooldown, "cooldown");
        Objects.requireNonNull(cost, "cost");
        if (cooldown.isNegative()) {
            throw new IllegalArgumentException("kit cooldown must not be negative: " + cooldown);
        }
        items = List.copyOf(items);
    }

    /** A free, repeatable, ungated kit with the given items and cooldown. */
    public static KitDefinition repeatable(KitId id, List<KitItem> items, Duration cooldown) {
        return new KitDefinition(id, items, cooldown, false, false, KitCost.free());
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
