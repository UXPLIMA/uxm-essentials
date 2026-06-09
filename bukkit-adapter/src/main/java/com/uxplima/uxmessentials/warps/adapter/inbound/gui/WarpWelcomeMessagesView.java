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
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.FixedMenuLayout;
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

    private final Messages messages;
    private final Scheduler scheduler;
    private final WarpRepository warpRepository;
    private final WarpEditorView editorView;
    private final FixedMenuLayout layout;
    private final MiniMessage miniMessage;

    public WarpWelcomeMessagesView(
            Messages messages,
            Scheduler scheduler,
            WarpRepository warpRepository,
            WarpEditorView editorView,
            FixedMenuLayout layout) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.warpRepository = Objects.requireNonNull(warpRepository, "warpRepository");
        this.editorView = Objects.requireNonNull(editorView, "editorView");
        this.layout = Objects.requireNonNull(layout, "layout");
        this.miniMessage = MiniMessage.miniMessage();
    }

    /** The shipped geometry: the message grid limit plus the add/back/clear buttons, externalised to conf. */
    public static FixedMenuLayout defaultLayout() {
        return FixedMenuLayout.builder(3, Material.GRAY_STAINED_GLASS_PANE)
                .slotOnly("message-limit", 18)
                .element("add", 18, Material.WRITABLE_BOOK)
                .element("back", 22, Material.BARRIER)
                .element("clear", 26, Material.LAVA_BUCKET)
                .build();
    }

    /** The three-row chest's slot count: the configured row count times the nine slots per row. */
    private int size() {
        return layout.rows() * 9;
    }

    int messageLimit() {
        return layout.slot("message-limit");
    }

    int addSlot() {
        return layout.slot("add");
    }

    int backSlot() {
        return layout.slot("back");
    }

    int clearSlot() {
        return layout.slot("clear");
    }

    public void open(Player player, PlayerRef viewer, String warpName, @Nullable PlayerRef warpOwner) {
        scheduler.onEntity(viewer, () -> {
            WarpWelcomeMessagesHolder holder = new WarpWelcomeMessagesHolder(viewer, warpName, warpOwner);
            Inventory inventory = Bukkit.createInventory(
                    holder, size(), text(viewer, WarpsMessageKey.WARP_EDITOR_WELCOME_MANAGER_TITLE));
            holder.attach(inventory);
            populate(inventory, holder);
            player.openInventory(inventory);
        });
    }

    public void populate(Inventory inventory, WarpWelcomeMessagesHolder holder) {
        ItemStack filler =
                ItemBuilder.of(layout.fillerMaterial()).name(Component.empty()).build();
        int size = size();
        for (int i = 0; i < size; i++) {
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

        for (int i = 0; i < Math.min(welcomeMessages.size(), messageLimit()); i++) {
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
                addSlot(),
                ItemBuilder.of(layout.material("add"))
                        .name(text(viewer, WarpsMessageKey.WARP_EDITOR_WELCOME_MANAGER_ADD_NAME))
                        .lore(List.of(text(viewer, WarpsMessageKey.WARP_EDITOR_WELCOME_MANAGER_ADD_LORE)))
                        .build());

        inventory.setItem(
                backSlot(),
                ItemBuilder.of(layout.material("back"))
                        .name(text(viewer, WarpsMessageKey.WARP_EDITOR_SELECTOR_BACK))
                        .build());

        inventory.setItem(
                clearSlot(),
                ItemBuilder.of(layout.material("clear"))
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
