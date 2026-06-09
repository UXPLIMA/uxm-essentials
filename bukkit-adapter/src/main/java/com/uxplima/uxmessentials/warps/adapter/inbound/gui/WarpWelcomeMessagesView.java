package com.uxplima.uxmessentials.warps.adapter.inbound.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.warps.application.WarpsMessageKey;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WarpName;
import com.uxplima.uxmessentials.warps.domain.WelcomeMessage;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class WarpWelcomeMessagesView {

    /** The 27-slot (three-row) chest: the message grid, plus add, back and clear-all buttons. */
    static final int SIZE = 27;

    static final int MESSAGE_LIMIT = 18;
    static final int ADD_SLOT = 18;
    static final int BACK_SLOT = 22;
    static final int CLEAR_SLOT = 26;

    private final Messages messages;
    private final Scheduler scheduler;
    private final WarpRepository warpRepository;
    private final WarpEditorView editorView;
    private final MiniMessage miniMessage;

    public WarpWelcomeMessagesView(
            Messages messages, Scheduler scheduler, WarpRepository warpRepository, WarpEditorView editorView) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.warpRepository = Objects.requireNonNull(warpRepository, "warpRepository");
        this.editorView = Objects.requireNonNull(editorView, "editorView");
        this.miniMessage = MiniMessage.miniMessage();
    }

    public void open(Player player, PlayerRef viewer, String warpName, @Nullable PlayerRef warpOwner) {
        scheduler.onEntity(viewer, () -> {
            WarpWelcomeMessagesHolder holder = new WarpWelcomeMessagesHolder(viewer, warpName, warpOwner);
            Inventory inventory = Bukkit.createInventory(
                    holder, SIZE, text(viewer, WarpsMessageKey.WARP_EDITOR_WELCOME_MANAGER_TITLE));
            holder.attach(inventory);
            populate(inventory, holder);
            player.openInventory(inventory);
        });
    }

    public void populate(Inventory inventory, WarpWelcomeMessagesHolder holder) {
        ItemStack filler = ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE)
                .name(Component.empty())
                .build();
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, filler);
        }

        PlayerRef viewer = holder.viewer();
        List<WelcomeMessage> welcomeMessages = new ArrayList<>();
        if (holder.isPlayerWarp()) {
            PlayerWarpRepository repo = editorView.playerWarpRepository();
            if (repo != null) {
                Optional<PlayerWarp> opt =
                        repo.find(Objects.requireNonNull(holder.warpOwner()), PlayerWarpName.of(holder.warpName()));
                opt.ifPresent(w -> welcomeMessages.addAll(w.welcomeMessages()));
            }
        } else {
            Optional<Warp> opt = warpRepository.find(WarpName.of(holder.warpName()));
            opt.ifPresent(w -> welcomeMessages.addAll(w.welcomeMessages()));
        }

        for (int i = 0; i < Math.min(welcomeMessages.size(), MESSAGE_LIMIT); i++) {
            WelcomeMessage msg = welcomeMessages.get(i);
            Material mat = materialFor(msg.type());

            inventory.setItem(
                    i,
                    ItemBuilder.of(mat)
                            .name(text(
                                    viewer,
                                    WarpsMessageKey.WARP_EDITOR_WELCOME_MANAGER_ENTRY_NAME,
                                    Map.of("index", Integer.toString(i + 1))))
                            .lore(List.of(
                                    text(
                                            viewer,
                                            WarpsMessageKey.WARP_EDITOR_WELCOME_MANAGER_ENTRY_TEXT,
                                            Map.of("text", msg.message())),
                                    text(
                                            viewer,
                                            WarpsMessageKey.WARP_EDITOR_WELCOME_MANAGER_ENTRY_TYPE,
                                            Map.of("type", msg.type())),
                                    Component.empty(),
                                    text(viewer, WarpsMessageKey.WARP_EDITOR_WELCOME_MANAGER_ENTRY_EDIT),
                                    text(viewer, WarpsMessageKey.WARP_EDITOR_WELCOME_MANAGER_ENTRY_DELETE),
                                    text(viewer, WarpsMessageKey.WARP_EDITOR_WELCOME_MANAGER_ENTRY_CYCLE)))
                            .build());
        }

        inventory.setItem(
                ADD_SLOT,
                ItemBuilder.of(Material.WRITABLE_BOOK)
                        .name(text(viewer, WarpsMessageKey.WARP_EDITOR_WELCOME_MANAGER_ADD_NAME))
                        .lore(List.of(text(viewer, WarpsMessageKey.WARP_EDITOR_WELCOME_MANAGER_ADD_LORE)))
                        .build());

        inventory.setItem(
                BACK_SLOT,
                ItemBuilder.of(Material.BARRIER)
                        .name(text(viewer, WarpsMessageKey.WARP_EDITOR_SELECTOR_BACK))
                        .build());

        inventory.setItem(
                CLEAR_SLOT,
                ItemBuilder.of(Material.LAVA_BUCKET)
                        .name(text(viewer, WarpsMessageKey.WARP_EDITOR_WELCOME_MANAGER_CLEAR_NAME))
                        .lore(List.of(text(viewer, WarpsMessageKey.WARP_EDITOR_WELCOME_MANAGER_CLEAR_LORE)))
                        .build());
    }

    private Material materialFor(String type) {
        return switch (type.toUpperCase(java.util.Locale.ROOT)) {
            case "CHAT" -> Material.PAPER;
            case "ACTION_BAR" -> Material.REPEATER;
            case "TITLE" -> Material.GOLDEN_HELMET;
            case "SUBTITLE" -> Material.IRON_HELMET;
            case "BOSS_BAR" -> Material.DRAGON_EGG;
            default -> Material.WRITABLE_BOOK;
        };
    }

    private Component text(PlayerRef viewer, MessageKey key) {
        return miniMessage.deserialize(messages.resolve(viewer, key, Map.of()));
    }

    private Component text(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
        return miniMessage.deserialize(messages.resolve(viewer, key, placeholders));
    }
}
