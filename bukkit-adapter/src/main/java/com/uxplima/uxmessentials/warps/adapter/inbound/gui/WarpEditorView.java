package com.uxplima.uxmessentials.warps.adapter.inbound.gui;

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
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.WarpEditorLayout;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.warps.application.WarpsMessageKey;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import com.uxplima.uxmessentials.warps.domain.WarpName;
import com.uxplima.uxmessentials.warps.domain.WelcomeMessage;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class WarpEditorView {

    private final Messages messages;
    private final Scheduler scheduler;
    private final WarpRepository warpRepository;
    private final MiniMessage miniMessage;
    private final WarpEditorLayout layout;
    private final PlayerWarpRepositoryHandle playerWarpRepositoryHandle;

    public WarpEditorView(
            Messages messages,
            Scheduler scheduler,
            WarpRepository warpRepository,
            WarpEditorLayout layout,
            PlayerWarpRepositoryHandle playerWarpRepositoryHandle) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.warpRepository = Objects.requireNonNull(warpRepository, "warpRepository");
        this.layout = Objects.requireNonNull(layout, "layout");
        this.playerWarpRepositoryHandle =
                Objects.requireNonNull(playerWarpRepositoryHandle, "playerWarpRepositoryHandle");
        this.miniMessage = MiniMessage.miniMessage();
    }

    public WarpEditorLayout layout() {
        return layout;
    }

    public @Nullable PlayerWarpRepository playerWarpRepository() {
        return playerWarpRepositoryHandle.get();
    }

    public void open(Player player, PlayerRef viewer, String warpName, @Nullable PlayerRef warpOwner) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(warpName, "warpName");
        scheduler.onEntity(viewer, () -> {
            WarpEditorHolder holder = new WarpEditorHolder(viewer, warpName, warpOwner);
            Inventory inventory = Bukkit.createInventory(holder, layout.rows() * 9, title(warpName, viewer));
            holder.attach(inventory);
            populate(inventory, holder);
            player.openInventory(inventory);
        });
    }

    public void populate(Inventory inventory, WarpEditorHolder holder) {
        ItemStack filler = ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE)
                .name(Component.empty())
                .build();
        for (int i = 0; i < layout.rows() * 9; i++) {
            inventory.setItem(i, filler);
        }

        PlayerRef viewer = holder.viewer();
        if (holder.isPlayerWarp()) {
            PlayerWarpRepository repo = playerWarpRepositoryHandle.get();
            if (repo == null) {
                return;
            }
            repo.find(Objects.requireNonNull(holder.warpOwner()), PlayerWarpName.of(holder.warpName()))
                    .ifPresent(warp -> populateFrom(inventory, WarpDisplay.of(warp), viewer));
        } else {
            warpRepository
                    .find(WarpName.of(holder.warpName()))
                    .ifPresent(warp -> populateFrom(inventory, WarpDisplay.of(warp), viewer));
        }
    }

    private void populateFrom(Inventory inventory, WarpDisplay warp, PlayerRef viewer) {
        iconOption(inventory, warp, viewer);
        lockOption(inventory, warp, viewer);
        passwordOption(inventory, warp, viewer);
        option(
                inventory,
                viewer,
                layout.welcomeSlot(),
                layout.welcomeMaterial(),
                WarpsMessageKey.WARP_EDITOR_WELCOME_NAME,
                welcomeLines(warp, viewer),
                WarpsMessageKey.WARP_EDITOR_WELCOME_LORE_PROMPT);
        soundsOption(inventory, warp, viewer);
        particlesOption(inventory, warp, viewer);
        durationOption(
                inventory,
                viewer,
                layout.warmupSlot(),
                layout.warmupMaterial(),
                WarpsMessageKey.WARP_EDITOR_WARMUP_NAME,
                WarpsMessageKey.WARP_EDITOR_WARMUP_LORE_CURRENT,
                WarpsMessageKey.WARP_EDITOR_WARMUP_LORE_PROMPT,
                warp.warmupOverrideSeconds());
        durationOption(
                inventory,
                viewer,
                layout.cooldownSlot(),
                layout.cooldownMaterial(),
                WarpsMessageKey.WARP_EDITOR_COOLDOWN_NAME,
                WarpsMessageKey.WARP_EDITOR_COOLDOWN_LORE_CURRENT,
                WarpsMessageKey.WARP_EDITOR_COOLDOWN_LORE_PROMPT,
                warp.cooldownOverrideSeconds());
        inventory.setItem(
                layout.closeSlot(),
                ItemBuilder.of(Material.BARRIER)
                        .name(text(viewer, WarpsMessageKey.WARP_EDITOR_CLOSE))
                        .build());
    }

    private void lockOption(Inventory inventory, WarpDisplay warp, PlayerRef viewer) {
        option(
                inventory,
                viewer,
                layout.lockSlot(),
                layout.lockMaterial(),
                WarpsMessageKey.WARP_EDITOR_LOCK_NAME,
                List.of(text(
                        viewer,
                        WarpsMessageKey.WARP_EDITOR_LOCK_LORE_CURRENT,
                        Map.of("state", lockState(viewer, warp.isLocked())))),
                WarpsMessageKey.WARP_EDITOR_LOCK_LORE_PROMPT);
    }

    private void passwordOption(Inventory inventory, WarpDisplay warp, PlayerRef viewer) {
        option(
                inventory,
                viewer,
                layout.passwordSlot(),
                layout.passwordMaterial(),
                WarpsMessageKey.WARP_EDITOR_PASSWORD_NAME,
                List.of(text(
                        viewer,
                        WarpsMessageKey.WARP_EDITOR_PASSWORD_LORE_CURRENT,
                        Map.of("password", warp.password().orElse(none(viewer))))),
                WarpsMessageKey.WARP_EDITOR_PASSWORD_LORE_PROMPT);
    }

    private void soundsOption(Inventory inventory, WarpDisplay warp, PlayerRef viewer) {
        option(
                inventory,
                viewer,
                layout.soundsSlot(),
                layout.soundsMaterial(),
                WarpsMessageKey.WARP_EDITOR_SOUNDS_NAME,
                List.of(
                        text(
                                viewer,
                                WarpsMessageKey.WARP_EDITOR_SOUNDS_LORE_DEPARTURE,
                                Map.of("sound", warp.departureSound().orElse(none(viewer)))),
                        text(
                                viewer,
                                WarpsMessageKey.WARP_EDITOR_SOUNDS_LORE_ARRIVAL,
                                Map.of("sound", warp.arrivalSound().orElse(none(viewer))))),
                WarpsMessageKey.WARP_EDITOR_SOUNDS_LORE_PROMPT);
    }

    private void particlesOption(Inventory inventory, WarpDisplay warp, PlayerRef viewer) {
        option(
                inventory,
                viewer,
                layout.particlesSlot(),
                layout.particlesMaterial(),
                WarpsMessageKey.WARP_EDITOR_PARTICLES_NAME,
                List.of(
                        text(
                                viewer,
                                WarpsMessageKey.WARP_EDITOR_PARTICLES_LORE_DEPARTURE,
                                Map.of("particle", warp.departureParticle().orElse(none(viewer)))),
                        text(
                                viewer,
                                WarpsMessageKey.WARP_EDITOR_PARTICLES_LORE_ARRIVAL,
                                Map.of("particle", warp.arrivalParticle().orElse(none(viewer))))),
                WarpsMessageKey.WARP_EDITOR_PARTICLES_LORE_PROMPT);
    }

    /** The icon material option: the current material plus the set/clear prompt, parsed-material icon. */
    private void iconOption(Inventory inventory, WarpDisplay warp, PlayerRef viewer) {
        Material iconMat = warp.iconMaterial()
                .map(Material::matchMaterial)
                .filter(m -> m != Material.AIR)
                .orElse(Material.ITEM_FRAME);
        List<Component> current = List.of(text(
                viewer,
                WarpsMessageKey.WARP_EDITOR_ICON_LORE_CURRENT,
                Map.of("material", warp.iconMaterial().orElse(none(viewer)))));
        option(
                inventory,
                viewer,
                layout.iconSlot(),
                iconMat,
                WarpsMessageKey.WARP_EDITOR_ICON_NAME,
                current,
                WarpsMessageKey.WARP_EDITOR_ICON_LORE_PROMPT);
    }

    private List<Component> welcomeLines(WarpDisplay warp, PlayerRef viewer) {
        if (warp.welcomeMessages().isEmpty()) {
            return List.of(
                    text(viewer, WarpsMessageKey.WARP_EDITOR_WELCOME_LORE_CURRENT, Map.of("message", none(viewer))));
        }
        List<Component> lines = new java.util.ArrayList<>();
        for (WelcomeMessage msg : warp.welcomeMessages()) {
            lines.add(text(
                    viewer,
                    WarpsMessageKey.WARP_EDITOR_WELCOME_LORE_ENTRY,
                    Map.of("type", msg.type(), "message", msg.message())));
        }
        return lines;
    }

    private void durationOption(
            Inventory inventory,
            PlayerRef viewer,
            int slot,
            Material material,
            WarpsMessageKey nameKey,
            WarpsMessageKey currentKey,
            WarpsMessageKey promptKey,
            Optional<Double> seconds) {
        String value = seconds.map(d -> d + "s").orElse(none(viewer));
        option(
                inventory,
                viewer,
                slot,
                material,
                nameKey,
                List.of(text(viewer, currentKey, Map.of("seconds", value))),
                promptKey);
    }

    /** Render one editor option: an item at {@code slot} with its name, the current-value lines, and a prompt. */
    private void option(
            Inventory inventory,
            PlayerRef viewer,
            int slot,
            Material material,
            WarpsMessageKey nameKey,
            List<Component> currentLines,
            WarpsMessageKey promptKey) {
        List<Component> lore = new java.util.ArrayList<>(currentLines);
        lore.add(Component.empty());
        lore.addAll(textPrompt(viewer, promptKey));
        inventory.setItem(
                slot,
                ItemBuilder.of(material).name(text(viewer, nameKey)).lore(lore).build());
    }

    private String none(PlayerRef viewer) {
        return messages.resolve(viewer, WarpsMessageKey.WARP_EDITOR_VALUE_NONE, Map.of());
    }

    private String lockState(PlayerRef viewer, boolean locked) {
        WarpsMessageKey key =
                locked ? WarpsMessageKey.WARP_EDITOR_VALUE_LOCKED : WarpsMessageKey.WARP_EDITOR_VALUE_UNLOCKED;
        return messages.resolve(viewer, key, Map.of());
    }

    private Component title(String warpName, PlayerRef viewer) {
        return miniMessage.deserialize(
                messages.resolve(viewer, WarpsMessageKey.WARP_EDITOR_TITLE, Map.of("warp", warpName)));
    }

    private Component text(PlayerRef viewer, MessageKey key) {
        return miniMessage.deserialize(messages.resolve(viewer, key, Map.of()));
    }

    private Component text(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
        return miniMessage.deserialize(messages.resolve(viewer, key, placeholders));
    }

    private List<Component> textPrompt(PlayerRef viewer, MessageKey key) {
        String resolved = messages.resolve(viewer, key, Map.of());
        return splitAndParse(resolved);
    }

    private List<Component> splitAndParse(String resolved) {
        Iterable<String> parts = com.google.common.base.Splitter.on(java.util.regex.Pattern.compile("(?i)<br/?>|\\n"))
                .split(resolved);
        java.util.ArrayList<Component> list = new java.util.ArrayList<>();
        for (String part : parts) {
            list.add(miniMessage.deserialize(part));
        }
        return list;
    }
}
