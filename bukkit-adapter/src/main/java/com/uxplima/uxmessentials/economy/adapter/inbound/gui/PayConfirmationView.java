package com.uxplima.uxmessentials.economy.adapter.inbound.gui;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.application.Pay;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.FixedMenuLayout;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.gui.Guis;
import com.uxplima.uxmlib.gui.SimpleGui;
import com.uxplima.uxmlib.gui.item.GuiItem;
import com.uxplima.uxmlib.item.ItemBuilder;
import com.uxplima.uxmlib.item.SkullData;
import org.jspecify.annotations.NullMarked;

/**
 * Interactive 1-row confirmation GUI for transaction values exceeding the confirmation threshold.
 */
@NullMarked
public final class PayConfirmationView {

    private final Pay pay;
    private final Scheduler scheduler;
    private final Messages messages;
    private final FixedMenuLayout layout;

    public PayConfirmationView(Pay pay, Scheduler scheduler, Messages messages, FixedMenuLayout layout) {
        this.pay = Objects.requireNonNull(pay, "pay");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.layout = Objects.requireNonNull(layout, "layout");
    }

    /** The shipped geometry: the confirm/recipient/value/cancel strip, externalised to pay-confirm.conf. */
    public static FixedMenuLayout defaultLayout() {
        return FixedMenuLayout.builder(1, Material.GRAY_STAINED_GLASS_PANE)
                .element("confirm", 0, Material.GREEN_STAINED_GLASS_PANE)
                .element("recipient", 3, Material.PLAYER_HEAD)
                .element("value", 4, Material.PAPER)
                .element("cancel", 5, Material.RED_STAINED_GLASS_PANE)
                .build();
    }

    private Component text(PlayerRef viewer, EconomyMessageKey key, Map<String, String> placeholders) {
        return StyledText.render(messages.resolve(viewer, key, placeholders))
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);
    }

    /**
     * Opens the payment confirmation chest GUI.
     *
     * @param viewer the player who is paying
     * @param target the recipient player
     * @param amount the money amount being transferred
     */
    public void open(Player viewer, PlayerRef target, Money amount) {
        PlayerRef viewerRef = new PlayerRef(viewer.getUniqueId(), viewer.getName());

        scheduler.onEntity(viewerRef, () -> {
            String amountText = amount.amount().toPlainString() + " "
                    + amount.currency().id().value();
            SimpleGui gui = Guis.gui()
                    .title(text(viewerRef, EconomyMessageKey.PAY_CONFIRM_GUI_TITLE, Map.of()))
                    .rows(layout.rows())
                    .build();

            placeConfirm(gui, viewer, viewerRef, target, amountText);
            placeRecipient(gui, viewerRef, target);
            placeValue(gui, viewerRef, amountText);
            placeCancel(gui, viewer, viewerRef);

            gui.open(viewer);
        });
    }

    private void placeConfirm(SimpleGui gui, Player viewer, PlayerRef viewerRef, PlayerRef target, String amountText) {
        ItemStack confirmItem = ItemBuilder.of(layout.material("confirm"))
                .name(text(viewerRef, EconomyMessageKey.PAY_CONFIRM_GUI_CONFIRM_NAME, Map.of()))
                .lore(List.of(
                        text(viewerRef, EconomyMessageKey.PAY_CONFIRM_GUI_CONFIRM_LORE_HINT, Map.of()),
                        text(
                                viewerRef,
                                EconomyMessageKey.PAY_CONFIRM_GUI_CONFIRM_LORE_TARGET,
                                Map.of("target", target.name())),
                        text(
                                viewerRef,
                                EconomyMessageKey.PAY_CONFIRM_GUI_CONFIRM_LORE_AMOUNT,
                                Map.of("amount", amountText))))
                .build();
        GuiItem confirmButton = GuiItem.button(
                confirmItem,
                event -> scheduler.async(() -> {
                    pay.confirm(viewerRef);
                    scheduler.onEntity(viewerRef, () -> gui.close(viewer));
                }));
        // The confirm block spans three slots from its anchor.
        int anchor = layout.slot("confirm");
        for (int i = 0; i < 3; i++) {
            gui.set(anchor + i, confirmButton);
        }
    }

    private void placeRecipient(SimpleGui gui, PlayerRef viewerRef, PlayerRef target) {
        ItemStack headItem = ItemBuilder.of(layout.material("recipient"))
                .skull(SkullData.ofUuid(target.uuid()))
                .name(text(
                        viewerRef, EconomyMessageKey.PAY_CONFIRM_GUI_RECIPIENT_NAME, Map.of("target", target.name())))
                .build();
        gui.set(layout.slot("recipient"), GuiItem.display(headItem));
    }

    private void placeValue(SimpleGui gui, PlayerRef viewerRef, String amountText) {
        ItemStack valueItem = ItemBuilder.of(layout.material("value"))
                .name(text(viewerRef, EconomyMessageKey.PAY_CONFIRM_GUI_VALUE_NAME, Map.of("amount", amountText)))
                .lore(List.of(text(viewerRef, EconomyMessageKey.PAY_CONFIRM_GUI_VALUE_LORE, Map.of())))
                .build();
        gui.set(layout.slot("value"), GuiItem.display(valueItem));
    }

    private void placeCancel(SimpleGui gui, Player viewer, PlayerRef viewerRef) {
        ItemStack cancelItem = ItemBuilder.of(layout.material("cancel"))
                .name(text(viewerRef, EconomyMessageKey.PAY_CONFIRM_GUI_CANCEL_NAME, Map.of()))
                .lore(List.of(text(viewerRef, EconomyMessageKey.PAY_CONFIRM_GUI_CANCEL_LORE, Map.of())))
                .build();
        GuiItem cancelButton =
                GuiItem.button(cancelItem, event -> scheduler.onEntity(viewerRef, () -> gui.close(viewer)));
        // The cancel block spans four slots from its anchor.
        int anchor = layout.slot("cancel");
        for (int i = 0; i < 4; i++) {
            gui.set(anchor + i, cancelButton);
        }
    }
}
