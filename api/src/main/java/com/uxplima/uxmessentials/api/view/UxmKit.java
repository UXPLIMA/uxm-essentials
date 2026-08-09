package com.uxplima.uxmessentials.api.view;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * A kit as the operator configured it.
 *
 * <p>This is the definition, not a claim: what it costs, how often it may be taken, and what gates it. Whether a
 * particular player may take it right now is {@link com.uxplima.uxmessentials.api.query.UxmKitsQuery#claimableBy}.
 *
 * <p>The contents are deliberately absent. They are Bukkit item stacks, and putting them here would drag the
 * server API into a module that has none, freezing the item model into a published type it has no business
 * carrying.
 *
 * @param id the kit's id, which is also what a player types
 * @param displayName the name shown in menus and messages
 * @param cooldown how long between claims; {@link Duration#ZERO} when there is none
 * @param oneTime whether it may be claimed once and never again
 * @param requiresPermission whether a permission node gates it
 * @param permissionNode the node that gates it, or empty when none does
 * @param cost what a claim charges, or empty when it is free
 * @param category the category id it was filed under, or empty
 * @param itemCount how many item entries it grants
 * @param firstJoin whether it is handed out automatically on a player's first join
 * @param stockLimit how many claims the server allows in total, or empty when the kit is unlimited
 */
public record UxmKit(
        String id,
        String displayName,
        Duration cooldown,
        boolean oneTime,
        boolean requiresPermission,
        Optional<String> permissionNode,
        Optional<UxmMoney> cost,
        Optional<String> category,
        int itemCount,
        boolean firstJoin,
        Optional<Integer> stockLimit) {

    public UxmKit {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(cooldown, "cooldown");
        Objects.requireNonNull(permissionNode, "permissionNode");
        Objects.requireNonNull(cost, "cost");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(stockLimit, "stockLimit");
        if (itemCount < 0) {
            throw new IllegalArgumentException("a kit cannot grant a negative number of items: " + itemCount);
        }
    }

    /** Whether a claim costs nothing, which reads better than comparing an amount to zero. */
    public boolean isFree() {
        return cost.isEmpty();
    }
}
