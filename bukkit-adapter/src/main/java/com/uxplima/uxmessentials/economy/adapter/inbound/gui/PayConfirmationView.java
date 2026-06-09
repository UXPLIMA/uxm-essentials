package com.uxplima.uxmessentials.economy.adapter.inbound.gui;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.application.Pay;
import com.uxplima.uxmessentials.economy.domain.Money;
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
    private final MiniMessage miniMessage;

    public PayConfirmationView(Pay pay, Scheduler scheduler, Messages messages) {
        this.pay = Objects.requireNonNull(pay, "pay");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.miniMessage = MiniMessage.miniMessage();
    }

    private Component text(PlayerRef viewer, EconomyMessageKey key, Map<String, String> placeholders) {
        return miniMessage
                .deserialize(messages.resolve(viewer, key, placeholders))
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
                    .rows(1)
                    .build();

            // 1. Confirm items (Slots 0, 1, 2)
            ItemStack confirmItem = ItemBuilder.of(Material.GREEN_STAINED_GLASS_PANE)
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

            GuiItem confirmButton = GuiItem.button(confirmItem, event -> {
                // Execute confirm off-tick
                scheduler.async(() -> {
                    pay.confirm(viewerRef);
                    scheduler.onEntity(viewerRef, () -> gui.close(viewer));
                });
            });
            gui.set(0, confirmButton);
            gui.set(1, confirmButton);
            gui.set(2, confirmButton);

            // 2. Recipient head (Slot 3)
            ItemStack headItem = ItemBuilder.of(Material.PLAYER_HEAD)
                    .skull(SkullData.ofUuid(target.uuid()))
                    .name(text(
                            viewerRef,
                            EconomyMessageKey.PAY_CONFIRM_GUI_RECIPIENT_NAME,
                            Map.of("target", target.name())))
                    .build();
            gui.set(3, GuiItem.display(headItem));

            // 3. Value paper (Slot 4)
            ItemStack valueItem = ItemBuilder.of(Material.PAPER)
                    .name(text(viewerRef, EconomyMessageKey.PAY_CONFIRM_GUI_VALUE_NAME, Map.of("amount", amountText)))
                    .lore(List.of(text(viewerRef, EconomyMessageKey.PAY_CONFIRM_GUI_VALUE_LORE, Map.of())))
                    .build();
            gui.set(4, GuiItem.display(valueItem));

            // 4. Cancel items (Slots 5, 6, 7, 8)
            ItemStack cancelItem = ItemBuilder.of(Material.RED_STAINED_GLASS_PANE)
                    .name(text(viewerRef, EconomyMessageKey.PAY_CONFIRM_GUI_CANCEL_NAME, Map.of()))
                    .lore(List.of(text(viewerRef, EconomyMessageKey.PAY_CONFIRM_GUI_CANCEL_LORE, Map.of())))
                    .build();

            GuiItem cancelButton = GuiItem.button(cancelItem, event -> {
                scheduler.onEntity(viewerRef, () -> gui.close(viewer));
            });
            gui.set(5, cancelButton);
            gui.set(6, cancelButton);
            gui.set(7, cancelButton);
            gui.set(8, cancelButton);

            gui.open(viewer);
        });
    }
}
