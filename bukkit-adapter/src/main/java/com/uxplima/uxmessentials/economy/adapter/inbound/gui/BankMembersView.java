package com.uxplima.uxmessentials.economy.adapter.inbound.gui;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import com.uxplima.uxmessentials.economy.adapter.inbound.listener.BankChatPromptListener;
import com.uxplima.uxmessentials.economy.application.BankService;
import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.domain.BankError;
import com.uxplima.uxmessentials.economy.domain.SharedBank;
import com.uxplima.uxmessentials.economy.domain.SharedBank.BankAction;
import com.uxplima.uxmessentials.economy.domain.SharedBank.BankMember;
import com.uxplima.uxmessentials.economy.domain.SharedBank.BankRole;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmlib.gui.Guis;
import com.uxplima.uxmlib.gui.PaginatedGui;
import com.uxplima.uxmlib.gui.item.GuiItem;
import com.uxplima.uxmlib.gui.item.ItemPopulator;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class BankMembersView {

    private final BankService bankService;
    private final BankChatPromptListener chatPromptListener;
    private final Scheduler scheduler;
    private final PlayerLookup players;
    private final Messages messages;
    private final Supplier<BankNavigation> navigation;
    private final com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayout layout;
    private final MiniMessage miniMessage;

    public BankMembersView(
            BankService bankService,
            BankChatPromptListener chatPromptListener,
            Scheduler scheduler,
            PlayerLookup players,
            Messages messages,
            Supplier<BankNavigation> navigation,
            com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayout layout) {
        this.bankService = Objects.requireNonNull(bankService, "bankService");
        this.chatPromptListener = Objects.requireNonNull(chatPromptListener, "chatPromptListener");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.players = Objects.requireNonNull(players, "players");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.navigation = Objects.requireNonNull(navigation, "navigation");
        this.layout = Objects.requireNonNull(layout, "layout");
        this.miniMessage = MiniMessage.miniMessage();
    }

    private Component text(PlayerRef viewer, EconomyMessageKey key, Map<String, String> placeholders) {
        return miniMessage
                .deserialize(messages.resolve(viewer, key, placeholders))
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);
    }

    public void open(Player player, SharedBank bank) {
        PlayerRef viewerRef = new PlayerRef(player.getUniqueId(), player.getName());

        scheduler.async(() -> {
            // Re-fetch bank to get fresh members list
            Optional<SharedBank> oBank = bankService.getBank(bank.id());
            if (oBank.isEmpty()) {
                scheduler.onEntity(
                        viewerRef, () -> navigation.get().bankActionsView().open(player, bank));
                return;
            }
            SharedBank freshBank = oBank.get();

            scheduler.onEntity(viewerRef, () -> {
                Guis.PaginatedBuilder builder = Guis.paginated()
                        .title(text(
                                viewerRef, EconomyMessageKey.BANK_MEMBERS_GUI_TITLE, Map.of("bank", freshBank.name())))
                        .rows(layout.rows());
                layout.explicitContentSlots().ifPresent(builder::contentSlots);
                PaginatedGui gui = builder.build();

                boolean canRemove = freshBank.hasPermission(viewerRef, BankAction.REMOVE_MEMBER);

                gui.populate(freshBank.members(), ItemPopulator.of(m -> memberIcon(viewerRef, m), (member, event) -> {
                    if (event.isRightClick() && canRemove) {
                        scheduler.async(() -> {
                            Result<Unit, BankError> res =
                                    bankService.removeMember(viewerRef, freshBank.id(), member.player());
                            scheduler.onEntity(viewerRef, () -> {
                                if (res.isOk()) {
                                    player.sendMessage(text(
                                            viewerRef,
                                            EconomyMessageKey.BANK_MEMBERS_GUI_REMOVED,
                                            Map.of("player", member.player().name())));
                                } else {
                                    player.sendMessage(text(
                                            viewerRef, EconomyMessageKey.BANK_MEMBERS_GUI_REMOVE_FAILED, Map.of()));
                                }
                                open(player, freshBank);
                            });
                        });
                    }
                }));

                // Back / Next buttons on bottom row
                ItemStack prevItem = ItemBuilder.of(layout.navIcon())
                        .name(text(viewerRef, EconomyMessageKey.BANK_MEMBERS_GUI_PREV, Map.of()))
                        .build();
                ItemStack nextItem = ItemBuilder.of(layout.navIcon())
                        .name(text(viewerRef, EconomyMessageKey.BANK_MEMBERS_GUI_NEXT, Map.of()))
                        .build();

                gui.set(layout.prevSlot(), GuiItem.previousPage(gui, prevItem));

                // The action buttons sit in the reserved bottom row, derived from the configured row count so a
                // menu shorter than six rows never indexes a slot past the inventory. back/add take the third and
                // fifth bottom-row cells; the rest of the bottom row is filled, skipping the nav button slots.
                int bottom = (layout.rows() - 1) * 9;
                int backSlot = bottom + 2;
                int addSlot = bottom + 4;

                // Add Member button
                if (freshBank.hasPermission(viewerRef, BankAction.ADD_MEMBER)) {
                    gui.set(
                            addSlot,
                            GuiItem.button(
                                    ItemBuilder.of(Material.EMERALD_BLOCK)
                                            .name(text(viewerRef, EconomyMessageKey.BANK_MEMBERS_GUI_ADD, Map.of()))
                                            .lore(List.of(text(
                                                    viewerRef, EconomyMessageKey.BANK_MEMBERS_GUI_ADD_LORE, Map.of())))
                                            .build(),
                                    event -> {
                                        scheduler.onEntity(viewerRef, () -> {
                                            gui.close(player);
                                            promptAddMember(player, freshBank);
                                        });
                                    }));
                }

                // Go Back button
                gui.set(
                        backSlot,
                        GuiItem.button(
                                ItemBuilder.of(Material.BARRIER)
                                        .name(text(viewerRef, EconomyMessageKey.BANK_MEMBERS_GUI_BACK, Map.of()))
                                        .build(),
                                event -> scheduler.onEntity(
                                        viewerRef,
                                        () -> navigation.get().bankActionsView().open(player, freshBank))));

                gui.set(layout.nextSlot(), GuiItem.nextPage(gui, nextItem));

                // Empty fillers on the rest of the bottom row, skipping the nav/back/add cells already placed.
                ItemStack filler = ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE)
                        .name(Component.empty())
                        .build();
                java.util.Set<Integer> taken =
                        java.util.Set.of(layout.prevSlot(), layout.nextSlot(), backSlot, addSlot);
                for (int slot = bottom; slot < bottom + 9; slot++) {
                    if (!taken.contains(slot)) {
                        gui.set(slot, GuiItem.display(filler));
                    }
                }

                gui.open(player);
            });
        });
    }

    private ItemStack memberIcon(PlayerRef viewer, BankMember member) {
        return ItemBuilder.of(Material.PLAYER_HEAD)
                .name(text(
                        viewer,
                        EconomyMessageKey.BANK_MEMBERS_GUI_MEMBER_NAME,
                        Map.of("player", member.player().name())))
                .lore(List.of(
                        text(
                                viewer,
                                EconomyMessageKey.BANK_MEMBERS_GUI_MEMBER_ROLE,
                                Map.of("role", member.role().name())),
                        Component.empty(),
                        text(viewer, EconomyMessageKey.BANK_MEMBERS_GUI_MEMBER_REMOVE_HINT, Map.of())))
                .build();
    }

    private void promptAddMember(Player player, SharedBank bank) {
        PlayerRef viewerRef = new PlayerRef(player.getUniqueId(), player.getName());
        chatPromptListener.prompt(
                player, text(viewerRef, EconomyMessageKey.BANK_MEMBERS_GUI_ADD_PROMPT, Map.of()), targetName -> {
                    String cleanName = targetName.trim();
                    if (cleanName.isEmpty()) {
                        player.sendMessage(text(viewerRef, EconomyMessageKey.BANK_MEMBERS_GUI_NAME_EMPTY, Map.of()));
                        open(player, bank);
                        return;
                    }

                    scheduler.async(() -> {
                        Optional<PlayerRef> resolved = players.findByName(cleanName);
                        if (resolved.isEmpty()) {
                            scheduler.onEntity(viewerRef, () -> {
                                player.sendMessage(
                                        text(viewerRef, EconomyMessageKey.BANK_MEMBERS_GUI_ADD_FAILED, Map.of()));
                                open(player, bank);
                            });
                            return;
                        }
                        Result<Unit, BankError> res =
                                bankService.addMember(viewerRef, bank.id(), resolved.get(), BankRole.MEMBER);
                        scheduler.onEntity(viewerRef, () -> {
                            if (res.isOk()) {
                                player.sendMessage(text(
                                        viewerRef,
                                        EconomyMessageKey.BANK_MEMBERS_GUI_ADDED,
                                        Map.of("player", cleanName)));
                            } else {
                                player.sendMessage(
                                        text(viewerRef, EconomyMessageKey.BANK_MEMBERS_GUI_ADD_FAILED, Map.of()));
                            }
                            open(player, bank);
                        });
                    });
                });
    }
}
