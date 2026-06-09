package com.uxplima.uxmessentials.economy.adapter.inbound.gui;

import java.util.Objects;

import org.jspecify.annotations.NullMarked;

/**
 * The navigation router that links the three bank menus, replacing the mutable setter cross-references they used
 * to hold. Each view takes a {@code Supplier<BankNavigation>} in its constructor — a final, non-null field — and
 * dereferences it only when a button is clicked, by which point the router is fully built. This keeps the
 * constructor-injection-only rule (no post-construction setters) while still allowing the menus to open each
 * other, since the router and the views it holds cannot all be constructed in one pass.
 */
@NullMarked
public record BankNavigation(
        BankGuiView bankGuiView, BankActionsView bankActionsView, BankMembersView bankMembersView) {

    public BankNavigation {
        Objects.requireNonNull(bankGuiView, "bankGuiView");
        Objects.requireNonNull(bankActionsView, "bankActionsView");
        Objects.requireNonNull(bankMembersView, "bankMembersView");
    }
}
