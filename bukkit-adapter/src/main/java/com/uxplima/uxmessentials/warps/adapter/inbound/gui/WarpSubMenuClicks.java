package com.uxplima.uxmessentials.warps.adapter.inbound.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.warps.adapter.inbound.listener.WarpChatPromptListener;
import com.uxplima.uxmessentials.warps.application.WarpsMessageKey;
import com.uxplima.uxmessentials.warps.domain.WelcomeMessage;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Handles the click events for the warp editor's three sub-menus — the sound selector, the particle selector,
 * and the welcome-message manager. The warp editor listener owns the inventory dispatch and forwards each
 * sub-menu-holder click here, so the sub-menu logic lives in one focused collaborator rather than padding the
 * listener. Each handler reads the clicked slot against the relevant view's config-driven slots, applies the
 * change through the shared {@link EditableWarpLoader}, and reopens the editor or sub-menu so the operator sees
 * the result; a custom-value prompt resolves its label from a {@link MessageKey} in the viewer's locale.
 */
@NullMarked
final class WarpSubMenuClicks {

    private final WarpEditorView editorView;
    private final WarpChatPromptListener promptListener;
    private final Messages messages;
    private final MiniMessage miniMessage;
    private final EditableWarpLoader loader;
    private final WarpSoundSelectorView soundSelectorView;
    private final WarpParticleSelectorView particleSelectorView;
    private final WarpWelcomeMessagesView welcomeMessagesView;

    WarpSubMenuClicks(
            WarpEditorView editorView,
            WarpChatPromptListener promptListener,
            Messages messages,
            EditableWarpLoader loader,
            WarpSoundSelectorView soundSelectorView,
            WarpParticleSelectorView particleSelectorView,
            WarpWelcomeMessagesView welcomeMessagesView) {
        this.editorView = Objects.requireNonNull(editorView, "editorView");
        this.promptListener = Objects.requireNonNull(promptListener, "promptListener");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.miniMessage = MiniMessage.miniMessage();
        this.loader = Objects.requireNonNull(loader, "loader");
        this.soundSelectorView = Objects.requireNonNull(soundSelectorView, "soundSelectorView");
        this.particleSelectorView = Objects.requireNonNull(particleSelectorView, "particleSelectorView");
        this.welcomeMessagesView = Objects.requireNonNull(welcomeMessagesView, "welcomeMessagesView");
    }

    void handleSoundSelectorClick(Player player, WarpSoundSelectorHolder holder, int slot) {
        String name = holder.warpName();
        PlayerRef viewer = holder.viewer();
        @Nullable PlayerRef owner = holder.warpOwner();
        boolean isDeparture = holder.isDeparture();

        if (slot == soundSelectorView.backSlot()) {
            editorView.open(player, viewer, name, owner);
            return;
        }

        if (slot == soundSelectorView.removeSlot()) {
            saveSound(holder, Optional.empty());
            editorView.open(player, viewer, name, owner);
            return;
        }

        if (slot == soundSelectorView.customSlot()) {
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

        if (slot >= 0 && slot < soundSelectorView.optionLimit()) {
            List<WarpSoundSelectorView.SoundOption> options = soundSelectorView.getOptions();
            if (slot < options.size()) {
                WarpSoundSelectorView.SoundOption opt = options.get(slot);
                saveSound(holder, Optional.of(opt.soundName()));
                editorView.open(player, viewer, name, owner);
            }
        }
    }

    private void saveSound(WarpSoundSelectorHolder holder, Optional<String> sound) {
        EditableWarp warp = loader.load(holder.warpName(), holder.warpOwner());
        if (warp == null) {
            return;
        }
        if (holder.isDeparture()) {
            warp.setDepartureSound(sound);
        } else {
            warp.setArrivalSound(sound);
        }
    }

    void handleParticleSelectorClick(Player player, WarpParticleSelectorHolder holder, int slot) {
        String name = holder.warpName();
        PlayerRef viewer = holder.viewer();
        @Nullable PlayerRef owner = holder.warpOwner();
        boolean isDeparture = holder.isDeparture();

        if (slot == particleSelectorView.backSlot()) {
            editorView.open(player, viewer, name, owner);
            return;
        }

        if (slot == particleSelectorView.removeSlot()) {
            saveParticle(holder, Optional.empty());
            editorView.open(player, viewer, name, owner);
            return;
        }

        if (slot == particleSelectorView.customSlot()) {
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

        if (slot >= 0 && slot < particleSelectorView.optionLimit()) {
            List<WarpParticleSelectorView.ParticleOption> options = particleSelectorView.getOptions();
            if (slot < options.size()) {
                WarpParticleSelectorView.ParticleOption opt = options.get(slot);
                saveParticle(holder, Optional.of(opt.particleName()));
                editorView.open(player, viewer, name, owner);
            }
        }
    }

    private void saveParticle(WarpParticleSelectorHolder holder, Optional<String> particle) {
        EditableWarp warp = loader.load(holder.warpName(), holder.warpOwner());
        if (warp == null) {
            return;
        }
        if (holder.isDeparture()) {
            warp.setDepartureParticle(particle);
        } else {
            warp.setArrivalParticle(particle);
        }
    }

    void handleWelcomeMessagesClick(Player player, WarpWelcomeMessagesHolder holder, int slot, ClickType click) {
        String name = holder.warpName();
        PlayerRef viewer = holder.viewer();
        @Nullable PlayerRef owner = holder.warpOwner();

        if (slot == welcomeMessagesView.backSlot()) {
            editorView.open(player, viewer, name, owner);
            return;
        }

        EditableWarp warp = loader.load(name, owner);
        List<WelcomeMessage> currentMsgs = warp == null ? new ArrayList<>() : new ArrayList<>(warp.welcomeMessages());

        if (slot == welcomeMessagesView.clearSlot()) {
            saveWelcomeMessages(holder, List.of());
            welcomeMessagesView.open(player, viewer, name, owner);
            return;
        }

        if (slot == welcomeMessagesView.addSlot()) {
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
            editWelcomeEntry(player, holder, currentMsgs, slot, click);
        }
    }

    private void editWelcomeEntry(
            Player player, WarpWelcomeMessagesHolder holder, List<WelcomeMessage> currentMsgs, int slot, ClickType c) {
        String name = holder.warpName();
        PlayerRef viewer = holder.viewer();
        @Nullable PlayerRef owner = holder.warpOwner();
        WelcomeMessage targetMsg = currentMsgs.get(slot);
        if (c == ClickType.RIGHT) { // Delete
            List<WelcomeMessage> updatedList = new ArrayList<>(currentMsgs);
            updatedList.remove(slot);
            saveWelcomeMessages(holder, updatedList);
            welcomeMessagesView.open(player, viewer, name, owner);
        } else if (c == ClickType.LEFT) { // Edit text
            player.closeInventory();
            promptListener.prompt(player, text(viewer, WarpsMessageKey.WARP_EDITOR_WELCOME_PROMPT), input -> {
                List<WelcomeMessage> updatedList = new ArrayList<>(currentMsgs);
                updatedList.set(slot, new WelcomeMessage(input, targetMsg.type()));
                saveWelcomeMessages(holder, updatedList);
                welcomeMessagesView.open(player, viewer, name, owner);
            });
        } else if (c == ClickType.SHIFT_LEFT || c == ClickType.SHIFT_RIGHT) { // Cycle type
            List<WelcomeMessage> updatedList = new ArrayList<>(currentMsgs);
            updatedList.set(slot, new WelcomeMessage(targetMsg.message(), nextWelcomeType(targetMsg.type())));
            saveWelcomeMessages(holder, updatedList);
            welcomeMessagesView.open(player, viewer, name, owner);
        }
    }

    private static String nextWelcomeType(String type) {
        return switch (type.toUpperCase(Locale.ROOT)) {
            case "CHAT" -> "ACTION_BAR";
            case "ACTION_BAR" -> "TITLE";
            case "TITLE" -> "SUBTITLE";
            case "SUBTITLE" -> "BOSS_BAR";
            default -> "CHAT";
        };
    }

    private void saveWelcomeMessages(WarpWelcomeMessagesHolder holder, List<WelcomeMessage> msgs) {
        EditableWarp warp = loader.load(holder.warpName(), holder.warpOwner());
        if (warp != null) {
            warp.setWelcomeMessages(msgs);
        }
    }

    private Component text(PlayerRef viewer, MessageKey key) {
        return miniMessage.deserialize(messages.resolve(viewer, key, Map.of()));
    }
}
