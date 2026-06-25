package com.uxplima.uxmessentials.warps.adapter.inbound.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.InputRequest;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.warps.application.WarpsMessageKey;
import com.uxplima.uxmessentials.warps.domain.WelcomeMessage;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Handles the click events for the warp editor's welcome-message manager sub-menu. The warp editor listener owns
 * the inventory dispatch and forwards each welcome-holder click here, so the sub-menu logic lives in one focused
 * collaborator rather than padding the listener. The handler reads the clicked slot against the welcome view's
 * config-driven slots, applies the change through the shared {@link EditableWarpLoader}, and reopens the editor or
 * sub-menu so the operator sees the result; a custom-value prompt resolves its label from a {@link MessageKey} in
 * the viewer's locale. (The sound and particle selectors now render through the menu engine and route their own
 * clicks, so they are no longer dispatched here.)
 */
@NullMarked
final class WarpSubMenuClicks {

    private final WarpEditorView editorView;
    private final TextInput textInput;
    private final EditableWarpLoader loader;
    private final WarpWelcomeMessagesView welcomeMessagesView;

    WarpSubMenuClicks(
            WarpEditorView editorView,
            TextInput textInput,
            EditableWarpLoader loader,
            WarpWelcomeMessagesView welcomeMessagesView) {
        this.editorView = Objects.requireNonNull(editorView, "editorView");
        this.textInput = Objects.requireNonNull(textInput, "textInput");
        this.loader = Objects.requireNonNull(loader, "loader");
        this.welcomeMessagesView = Objects.requireNonNull(welcomeMessagesView, "welcomeMessagesView");
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
            textInput.prompt(
                    player,
                    viewer,
                    InputRequest.of("warp.welcome", WarpsMessageKey.WARP_EDITOR_WELCOME_PROMPT),
                    input -> {
                        List<WelcomeMessage> updatedList = new ArrayList<>(currentMsgs);
                        updatedList.add(new WelcomeMessage(input, "CHAT"));
                        saveWelcomeMessages(holder, updatedList);
                        welcomeMessagesView.open(player, viewer, name, owner);
                    },
                    () -> welcomeMessagesView.open(player, viewer, name, owner));
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
            textInput.prompt(
                    player,
                    viewer,
                    InputRequest.of("warp.welcome", WarpsMessageKey.WARP_EDITOR_WELCOME_PROMPT),
                    input -> {
                        List<WelcomeMessage> updatedList = new ArrayList<>(currentMsgs);
                        updatedList.set(slot, new WelcomeMessage(input, targetMsg.type()));
                        saveWelcomeMessages(holder, updatedList);
                        welcomeMessagesView.open(player, viewer, name, owner);
                    },
                    () -> welcomeMessagesView.open(player, viewer, name, owner));
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
}
