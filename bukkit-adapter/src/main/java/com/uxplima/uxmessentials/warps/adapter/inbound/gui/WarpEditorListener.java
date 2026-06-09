package com.uxplima.uxmessentials.warps.adapter.inbound.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.WarpEditorLayout;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.warps.adapter.inbound.listener.WarpChatPromptListener;
import com.uxplima.uxmessentials.warps.application.WarpsMessageKey;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import com.uxplima.uxmessentials.warps.domain.WarpName;
import com.uxplima.uxmessentials.warps.domain.WelcomeMessage;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class WarpEditorListener implements Listener {

    private final WarpEditorView editorView;
    private final WarpRepository warpRepository;
    private final WarpChatPromptListener promptListener;
    private final Messages messages;
    private final MiniMessage miniMessage;

    private final WarpSoundSelectorView soundSelectorView;
    private final WarpParticleSelectorView particleSelectorView;
    private final WarpWelcomeMessagesView welcomeMessagesView;

    public WarpEditorListener(
            WarpEditorView editorView,
            WarpRepository warpRepository,
            WarpChatPromptListener promptListener,
            Messages messages,
            WarpSoundSelectorView soundSelectorView,
            WarpParticleSelectorView particleSelectorView,
            WarpWelcomeMessagesView welcomeMessagesView) {
        this.editorView = Objects.requireNonNull(editorView, "editorView");
        this.warpRepository = Objects.requireNonNull(warpRepository, "warpRepository");
        this.promptListener = Objects.requireNonNull(promptListener, "promptListener");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.miniMessage = MiniMessage.miniMessage();
        this.soundSelectorView = Objects.requireNonNull(soundSelectorView, "soundSelectorView");
        this.particleSelectorView = Objects.requireNonNull(particleSelectorView, "particleSelectorView");
        this.welcomeMessagesView = Objects.requireNonNull(welcomeMessagesView, "welcomeMessagesView");
    }

    private @Nullable PlayerWarpRepository playerWarpRepository() {
        return editorView.playerWarpRepository();
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory inventory = event.getView().getTopInventory();
        InventoryHolder holder = inventory.getHolder();
        if (holder == null) {
            return;
        }

        if (holder instanceof WarpSoundSelectorHolder soundHolder) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            Player player = (Player) event.getWhoClicked();
            handleSoundSelectorClick(player, soundHolder, slot);
            return;
        }

        if (holder instanceof WarpParticleSelectorHolder particleHolder) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            Player player = (Player) event.getWhoClicked();
            handleParticleSelectorClick(player, particleHolder, slot);
            return;
        }

        if (holder instanceof WarpWelcomeMessagesHolder welcomeHolder) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            Player player = (Player) event.getWhoClicked();
            ClickType click = event.getClick();
            handleWelcomeMessagesClick(player, welcomeHolder, slot, click);
            return;
        }

        if (!(holder instanceof WarpEditorHolder editorHolder)) {
            return;
        }

        event.setCancelled(true);
        int slot = event.getRawSlot();
        Player player = (Player) event.getWhoClicked();
        PlayerRef viewer = editorHolder.viewer();
        String warpName = editorHolder.warpName();
        @Nullable PlayerRef owner = editorHolder.warpOwner();
        ClickType click = event.getClick();

        if (slot == editorView.layout().closeSlot()) {
            player.closeInventory();
            return;
        }

        EditableWarp warp = loadEditable(warpName, owner);
        if (warp == null) {
            player.closeInventory();
            return;
        }
        handleEditorClick(player, viewer, warpName, owner, warp, slot, click);
    }

    /** Load the clicked warp as a uniform {@link EditableWarp}, or {@code null} when it no longer exists. */
    private @Nullable EditableWarp loadEditable(String name, @Nullable PlayerRef owner) {
        if (owner != null) {
            PlayerWarpRepository repo = playerWarpRepository();
            if (repo == null) {
                return null;
            }
            return repo.find(owner, PlayerWarpName.of(name))
                    .map(warp -> EditableWarp.ofPlayer(warp, repo))
                    .orElse(null);
        }
        return warpRepository
                .find(WarpName.of(name))
                .map(warp -> EditableWarp.ofServer(warp, warpRepository))
                .orElse(null);
    }

    /**
     * The shared server-warp/player-warp editor click handler. The two warp types differ only in their Java
     * type and the owner threaded back into the re-open; both are bridged behind {@link EditableWarp}, so this
     * single method drives every editor button for either kind.
     */
    private void handleEditorClick(
            Player player,
            PlayerRef viewer,
            String name,
            @Nullable PlayerRef owner,
            EditableWarp warp,
            int slot,
            ClickType click) {
        WarpEditorLayout layout = editorView.layout();
        if (slot == layout.iconSlot()) {
            editIcon(player, viewer, name, owner, warp, click);
        } else if (slot == layout.lockSlot()) {
            warp.setLocked(!warp.isLocked());
            editorView.open(player, viewer, name, owner);
        } else if (slot == layout.passwordSlot()) {
            editPassword(player, viewer, name, owner, warp, click);
        } else if (slot == layout.welcomeSlot()) {
            welcomeMessagesView.open(player, viewer, name, owner);
        } else if (slot == layout.soundsSlot()) {
            editSounds(player, viewer, name, owner, warp, click);
        } else if (slot == layout.particlesSlot()) {
            editParticles(player, viewer, name, owner, warp, click);
        } else if (slot == layout.warmupSlot()) {
            editDuration(player, viewer, name, owner, warp, click, true);
        } else if (slot == layout.cooldownSlot()) {
            editDuration(player, viewer, name, owner, warp, click, false);
        }
    }

    private void editIcon(
            Player player, PlayerRef viewer, String name, @Nullable PlayerRef owner, EditableWarp warp, ClickType c) {
        if (c == ClickType.RIGHT) {
            warp.setIconMaterial(Optional.empty());
            editorView.open(player, viewer, name, owner);
        } else if (c == ClickType.LEFT) {
            Material hand = player.getInventory().getItemInMainHand().getType();
            if (hand.isAir()) {
                player.sendMessage(text(viewer, WarpsMessageKey.WARP_EDITOR_ICON_ERROR_HAND));
                return;
            }
            warp.setIconMaterial(Optional.of(hand.name()));
            editorView.open(player, viewer, name, owner);
        }
    }

    private void editPassword(
            Player player, PlayerRef viewer, String name, @Nullable PlayerRef owner, EditableWarp warp, ClickType c) {
        if (c == ClickType.RIGHT) {
            warp.setPassword(Optional.empty());
            editorView.open(player, viewer, name, owner);
        } else if (c == ClickType.LEFT) {
            player.closeInventory();
            promptListener.prompt(player, text(viewer, WarpsMessageKey.WARP_EDITOR_PASSWORD_PROMPT), input -> {
                warp.setPassword(Optional.of(input));
                editorView.open(player, viewer, name, owner);
            });
        }
    }

    private void editSounds(
            Player player, PlayerRef viewer, String name, @Nullable PlayerRef owner, EditableWarp warp, ClickType c) {
        if (c == ClickType.SHIFT_LEFT || c == ClickType.SHIFT_RIGHT) {
            warp.clearSounds();
            editorView.open(player, viewer, name, owner);
        } else {
            soundSelectorView.open(player, viewer, name, owner, c == ClickType.LEFT);
        }
    }

    private void editParticles(
            Player player, PlayerRef viewer, String name, @Nullable PlayerRef owner, EditableWarp warp, ClickType c) {
        if (c == ClickType.SHIFT_LEFT || c == ClickType.SHIFT_RIGHT) {
            warp.clearParticles();
            editorView.open(player, viewer, name, owner);
        } else {
            particleSelectorView.open(player, viewer, name, owner, c == ClickType.LEFT);
        }
    }

    private void editDuration(
            Player player,
            PlayerRef viewer,
            String name,
            @Nullable PlayerRef owner,
            EditableWarp warp,
            ClickType c,
            boolean warmup) {
        if (c == ClickType.RIGHT) {
            applyDuration(warp, warmup, Optional.empty());
            editorView.open(player, viewer, name, owner);
        } else if (c == ClickType.LEFT) {
            player.closeInventory();
            MessageKey promptKey =
                    warmup ? WarpsMessageKey.WARP_EDITOR_WARMUP_PROMPT : WarpsMessageKey.WARP_EDITOR_COOLDOWN_PROMPT;
            promptListener.prompt(player, text(viewer, promptKey), input -> {
                double seconds;
                try {
                    seconds = Double.parseDouble(input);
                } catch (NumberFormatException e) {
                    player.sendMessage(text(viewer, WarpsMessageKey.WARP_EDITOR_INVALID_NUMBER));
                    return;
                }
                applyDuration(warp, warmup, Optional.of(seconds));
                editorView.open(player, viewer, name, owner);
            });
        }
    }

    private void applyDuration(EditableWarp warp, boolean warmup, Optional<Double> seconds) {
        if (warmup) {
            warp.setWarmupOverride(seconds);
        } else {
            warp.setCooldownOverride(seconds);
        }
    }

    private void handleSoundSelectorClick(Player player, WarpSoundSelectorHolder holder, int slot) {
        String name = holder.warpName();
        PlayerRef viewer = holder.viewer();
        @Nullable PlayerRef owner = holder.warpOwner();
        boolean isDeparture = holder.isDeparture();

        if (slot == WarpSoundSelectorView.BACK_SLOT) {
            editorView.open(player, viewer, name, owner);
            return;
        }

        if (slot == WarpSoundSelectorView.REMOVE_SLOT) {
            saveSound(holder, Optional.empty());
            editorView.open(player, viewer, name, owner);
            return;
        }

        if (slot == WarpSoundSelectorView.CUSTOM_SLOT) {
            player.closeInventory();
            MessageKey promptKey = isDeparture
                    ? WarpsMessageKey.WARP_EDITOR_SOUND_DEPARTURE_PROMPT
                    : WarpsMessageKey.WARP_EDITOR_SOUND_ARRIVAL_PROMPT;
            promptListener.prompt(player, text(viewer, promptKey), input -> {
                saveSound(holder, Optional.of(input.toLowerCase(Locale.ROOT)));
                editorView.open(player, viewer, name, owner);
            });
            return;
        }

        if (slot >= 0 && slot < WarpSoundSelectorView.OPTION_LIMIT) {
            List<WarpSoundSelectorView.SoundOption> options = soundSelectorView.getOptions();
            if (slot < options.size()) {
                WarpSoundSelectorView.SoundOption opt = options.get(slot);
                saveSound(holder, Optional.of(opt.soundName()));
                editorView.open(player, viewer, name, owner);
            }
        }
    }

    private void saveSound(WarpSoundSelectorHolder holder, Optional<String> sound) {
        EditableWarp warp = loadEditable(holder.warpName(), holder.warpOwner());
        if (warp == null) {
            return;
        }
        if (holder.isDeparture()) {
            warp.setDepartureSound(sound);
        } else {
            warp.setArrivalSound(sound);
        }
    }

    private void handleParticleSelectorClick(Player player, WarpParticleSelectorHolder holder, int slot) {
        String name = holder.warpName();
        PlayerRef viewer = holder.viewer();
        @Nullable PlayerRef owner = holder.warpOwner();
        boolean isDeparture = holder.isDeparture();

        if (slot == WarpParticleSelectorView.BACK_SLOT) {
            editorView.open(player, viewer, name, owner);
            return;
        }

        if (slot == WarpParticleSelectorView.REMOVE_SLOT) {
            saveParticle(holder, Optional.empty());
            editorView.open(player, viewer, name, owner);
            return;
        }

        if (slot == WarpParticleSelectorView.CUSTOM_SLOT) {
            player.closeInventory();
            MessageKey promptKey = isDeparture
                    ? WarpsMessageKey.WARP_EDITOR_PARTICLE_DEPARTURE_PROMPT
                    : WarpsMessageKey.WARP_EDITOR_PARTICLE_ARRIVAL_PROMPT;
            promptListener.prompt(player, text(viewer, promptKey), input -> {
                saveParticle(holder, Optional.of(input.toUpperCase(Locale.ROOT)));
                editorView.open(player, viewer, name, owner);
            });
            return;
        }

        if (slot >= 0 && slot < WarpParticleSelectorView.OPTION_LIMIT) {
            List<WarpParticleSelectorView.ParticleOption> options = particleSelectorView.getOptions();
            if (slot < options.size()) {
                WarpParticleSelectorView.ParticleOption opt = options.get(slot);
                saveParticle(holder, Optional.of(opt.particleName()));
                editorView.open(player, viewer, name, owner);
            }
        }
    }

    private void saveParticle(WarpParticleSelectorHolder holder, Optional<String> particle) {
        EditableWarp warp = loadEditable(holder.warpName(), holder.warpOwner());
        if (warp == null) {
            return;
        }
        if (holder.isDeparture()) {
            warp.setDepartureParticle(particle);
        } else {
            warp.setArrivalParticle(particle);
        }
    }

    private void handleWelcomeMessagesClick(
            Player player, WarpWelcomeMessagesHolder holder, int slot, ClickType click) {
        String name = holder.warpName();
        PlayerRef viewer = holder.viewer();
        @Nullable PlayerRef owner = holder.warpOwner();

        if (slot == WarpWelcomeMessagesView.BACK_SLOT) {
            editorView.open(player, viewer, name, owner);
            return;
        }

        EditableWarp warp = loadEditable(name, owner);
        List<WelcomeMessage> currentMsgs = warp == null ? new ArrayList<>() : new ArrayList<>(warp.welcomeMessages());

        if (slot == WarpWelcomeMessagesView.CLEAR_SLOT) {
            saveWelcomeMessages(holder, List.of());
            welcomeMessagesView.open(player, viewer, name, owner);
            return;
        }

        if (slot == WarpWelcomeMessagesView.ADD_SLOT) {
            player.closeInventory();
            promptListener.prompt(player, text(viewer, WarpsMessageKey.WARP_EDITOR_WELCOME_PROMPT), input -> {
                List<WelcomeMessage> updatedList = new ArrayList<>(currentMsgs);
                updatedList.add(new WelcomeMessage(input, "CHAT"));
                saveWelcomeMessages(holder, updatedList);
                welcomeMessagesView.open(player, viewer, name, owner);
            });
            return;
        }

        if (slot >= 0 && slot < currentMsgs.size()) {
            WelcomeMessage targetMsg = currentMsgs.get(slot);
            if (click == ClickType.RIGHT) { // Delete
                List<WelcomeMessage> updatedList = new ArrayList<>(currentMsgs);
                updatedList.remove(slot);
                saveWelcomeMessages(holder, updatedList);
                welcomeMessagesView.open(player, viewer, name, owner);
            } else if (click == ClickType.LEFT) { // Edit text
                player.closeInventory();
                promptListener.prompt(player, text(viewer, WarpsMessageKey.WARP_EDITOR_WELCOME_PROMPT), input -> {
                    List<WelcomeMessage> updatedList = new ArrayList<>(currentMsgs);
                    updatedList.set(slot, new WelcomeMessage(input, targetMsg.type()));
                    saveWelcomeMessages(holder, updatedList);
                    welcomeMessagesView.open(player, viewer, name, owner);
                });
            } else if (click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT) { // Cycle type
                String nextType =
                        switch (targetMsg.type().toUpperCase(Locale.ROOT)) {
                            case "CHAT" -> "ACTION_BAR";
                            case "ACTION_BAR" -> "TITLE";
                            case "TITLE" -> "SUBTITLE";
                            case "SUBTITLE" -> "BOSS_BAR";
                            default -> "CHAT";
                        };
                List<WelcomeMessage> updatedList = new ArrayList<>(currentMsgs);
                updatedList.set(slot, new WelcomeMessage(targetMsg.message(), nextType));
                saveWelcomeMessages(holder, updatedList);
                welcomeMessagesView.open(player, viewer, name, owner);
            }
        }
    }

    private void saveWelcomeMessages(WarpWelcomeMessagesHolder holder, List<WelcomeMessage> msgs) {
        EditableWarp warp = loadEditable(holder.warpName(), holder.warpOwner());
        if (warp != null) {
            warp.setWelcomeMessages(msgs);
        }
    }

    private Component text(PlayerRef viewer, MessageKey key) {
        return miniMessage.deserialize(messages.resolve(viewer, key, Map.of()));
    }
}
