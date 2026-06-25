package com.uxplima.uxmessentials.warps.adapter.inbound.gui;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.InputRequest;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.warps.application.WarpsMessageKey;
import com.uxplima.uxmessentials.warps.application.port.WarpCategoryRepository;
import com.uxplima.uxmessentials.warps.domain.WarpCategory;
import org.jspecify.annotations.NullMarked;

/**
 * Handles the click events for the still-bespoke warp category settings GUI. The category manager and the two
 * selectors render through the menu engine and route their own clicks, so the only category screen left on the
 * raw-Bukkit listener is the per-category settings window; the warp-editor listener forwards its holder's clicks
 * here. Each handler acts and then reopens the relevant menu so the operator sees the result, and every prompt
 * resolves its label from a {@link MessageKey} in the viewer's locale.
 */
@NullMarked
public final class WarpCategoryEditing {

    private final WarpCategoryManagerView categoryManagerView;
    private final WarpCategorySettingsView categorySettingsView;
    private final WarpCategoryParentSelectorView parentSelectorView;
    private final WarpCategoryRepository categoryRepository;
    private final TextInput textInput;
    private final Messages messages;

    public WarpCategoryEditing(
            WarpCategoryManagerView categoryManagerView,
            WarpCategorySettingsView categorySettingsView,
            WarpCategoryParentSelectorView parentSelectorView,
            WarpCategoryRepository categoryRepository,
            TextInput textInput,
            Messages messages) {
        this.categoryManagerView = Objects.requireNonNull(categoryManagerView, "categoryManagerView");
        this.categorySettingsView = Objects.requireNonNull(categorySettingsView, "categorySettingsView");
        this.parentSelectorView = Objects.requireNonNull(parentSelectorView, "parentSelectorView");
        this.categoryRepository = Objects.requireNonNull(categoryRepository, "categoryRepository");
        this.textInput = Objects.requireNonNull(textInput, "textInput");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    void onCategorySettingsClick(Player player, WarpCategorySettingsHolder holder, int slot) {
        PlayerRef viewer = holder.viewer();
        WarpCategory category = holder.category();
        switch (slot) {
            case 26 -> categoryManagerView.open(player, viewer);
            case 10 -> editCategoryName(player, viewer, category);
            case 12 -> editCategoryMaterial(player, viewer, category);
            case 14 -> editCategoryLore(player, viewer, category);
            case 16 -> editCategorySlot(player, viewer, category);
            case 18 -> parentSelectorView.open(player, viewer, category);
            case 22 -> deleteCategory(player, viewer, category);
            default -> {
                // no-op: a click outside the action slots
            }
        }
    }

    private void editCategoryName(Player player, PlayerRef viewer, WarpCategory category) {
        prompt(
                player,
                viewer,
                "warp.category.display-name",
                WarpsMessageKey.WARP_EDITOR_CATEGORY_SETTINGS_DISPLAY_NAME_PROMPT,
                input -> saveCategory(player, viewer, category.withDisplayName(input)),
                () -> categorySettingsView.open(player, viewer, category));
    }

    private void editCategoryMaterial(Player player, PlayerRef viewer, WarpCategory category) {
        Material hand = player.getInventory().getItemInMainHand().getType();
        if (hand.isAir()) {
            player.sendMessage(text(viewer, WarpsMessageKey.WARP_EDITOR_ICON_ERROR_HAND));
            return;
        }
        saveCategory(player, viewer, category.withDisplayMaterial(Optional.of(hand.name())));
    }

    private void editCategoryLore(Player player, PlayerRef viewer, WarpCategory category) {
        prompt(
                player,
                viewer,
                "warp.category.display-lore",
                WarpsMessageKey.WARP_EDITOR_CATEGORY_SETTINGS_DISPLAY_LORE_PROMPT,
                input -> saveCategory(player, viewer, category.withDisplayLore(splitLines(input))),
                () -> categorySettingsView.open(player, viewer, category));
    }

    private void editCategorySlot(Player player, PlayerRef viewer, WarpCategory category) {
        prompt(
                player,
                viewer,
                "warp.category.slot",
                WarpsMessageKey.WARP_EDITOR_CATEGORY_SETTINGS_SLOT_PROMPT,
                input -> {
                    int targetSlot;
                    try {
                        targetSlot = Integer.parseInt(input.trim());
                    } catch (NumberFormatException invalid) {
                        player.sendMessage(text(viewer, WarpsMessageKey.WARP_EDITOR_INVALID_NUMBER));
                        return;
                    }
                    saveCategory(player, viewer, category.withSlot(targetSlot));
                },
                () -> categorySettingsView.open(player, viewer, category));
    }

    private void deleteCategory(Player player, PlayerRef viewer, WarpCategory category) {
        categoryRepository.delete(category.id());
        categoryManagerView.open(player, viewer);
    }

    private void saveCategory(Player player, PlayerRef viewer, WarpCategory category) {
        categoryRepository.save(category);
        categorySettingsView.open(player, viewer, category);
    }

    private void prompt(
            Player player,
            PlayerRef viewer,
            String inputKey,
            MessageKey key,
            Consumer<String> action,
            Runnable reopen) {
        player.closeInventory();
        textInput.prompt(player, viewer, InputRequest.of(inputKey, key), action, reopen);
    }

    private Component text(PlayerRef viewer, MessageKey key) {
        return StyledText.render(messages.resolve(viewer, key, Map.of()));
    }

    private static List<String> splitLines(String input) {
        return input.equalsIgnoreCase("none") ? List.of() : java.util.Arrays.asList(input.split("\\|"));
    }
}
