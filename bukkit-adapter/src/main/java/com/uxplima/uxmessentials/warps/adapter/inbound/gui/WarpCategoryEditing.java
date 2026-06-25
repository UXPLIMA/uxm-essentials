package com.uxplima.uxmessentials.warps.adapter.inbound.gui;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
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
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WarpCategory;
import com.uxplima.uxmessentials.warps.domain.WarpName;
import org.jspecify.annotations.NullMarked;

/**
 * Handles the click events for the warp-category administration GUIs (the category manager, a category's
 * settings, the warp→category selector, and the parent-category selector). The warp-editor listener owns the
 * inventory dispatch and forwards each category-holder click here, so the category logic lives in one focused
 * collaborator rather than padding the listener. Each handler acts and then reopens the relevant menu so the
 * operator sees the result, and every prompt resolves its label from a {@link MessageKey} in the viewer's locale.
 */
@NullMarked
public final class WarpCategoryEditing {

    private final BiConsumer<Player, PlayerRef> reopenManager;
    private final WarpCategoryManagerView categoryManagerView;
    private final WarpCategorySettingsView categorySettingsView;
    private final WarpCategoryParentSelectorView parentSelectorView;
    private final WarpCategoryRepository categoryRepository;
    private final WarpRepository warpRepository;
    private final WarpEditorView editorView;
    private final TextInput textInput;
    private final Messages messages;

    /**
     * @param reopenManager reopens the engine-rendered warp manager — the surface the category manager's back button
     *     returns to. The manager is wired after this collaborator, so it is bound as a seam rather than the view
     */
    public WarpCategoryEditing(
            BiConsumer<Player, PlayerRef> reopenManager,
            WarpCategoryManagerView categoryManagerView,
            WarpCategorySettingsView categorySettingsView,
            WarpCategoryParentSelectorView parentSelectorView,
            WarpCategoryRepository categoryRepository,
            WarpRepository warpRepository,
            WarpEditorView editorView,
            TextInput textInput,
            Messages messages) {
        this.reopenManager = Objects.requireNonNull(reopenManager, "reopenManager");
        this.categoryManagerView = Objects.requireNonNull(categoryManagerView, "categoryManagerView");
        this.categorySettingsView = Objects.requireNonNull(categorySettingsView, "categorySettingsView");
        this.parentSelectorView = Objects.requireNonNull(parentSelectorView, "parentSelectorView");
        this.categoryRepository = Objects.requireNonNull(categoryRepository, "categoryRepository");
        this.warpRepository = Objects.requireNonNull(warpRepository, "warpRepository");
        this.editorView = Objects.requireNonNull(editorView, "editorView");
        this.textInput = Objects.requireNonNull(textInput, "textInput");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    void onCategoryManagerClick(Player player, WarpCategoryManagerHolder holder, int slot) {
        PlayerRef viewer = holder.viewer();
        if (slot == 53) {
            reopenManager.accept(player, viewer);
        } else if (slot == 49) {
            promptCreateCategory(player, viewer);
        } else {
            List<WarpCategory> categories = holder.categories();
            if (slot >= 0 && slot < categories.size()) {
                categorySettingsView.open(player, viewer, categories.get(slot));
            }
        }
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

    void onCategorySelectorClick(Player player, WarpCategorySelectorHolder holder, int slot) {
        PlayerRef viewer = holder.viewer();
        String warpName = holder.warpName();
        if (slot == 53) {
            editorView.open(player, viewer, warpName, null);
            return;
        }
        if (slot == 49) {
            assignCategory(player, viewer, warpName, Optional.empty());
            return;
        }
        List<WarpCategory> categories = categoryRepository.all();
        if (slot >= 0 && slot < categories.size()) {
            assignCategory(
                    player, viewer, warpName, Optional.of(categories.get(slot).id()));
        }
    }

    void onCategoryParentSelectorClick(Player player, WarpCategoryParentSelectorHolder holder, int slot) {
        PlayerRef viewer = holder.viewer();
        WarpCategory category = holder.category();
        if (slot == 53) {
            categorySettingsView.open(player, viewer, category);
            return;
        }
        if (slot == 49) {
            saveCategory(player, viewer, category.withParentCategoryId(Optional.empty()));
            return;
        }
        List<WarpCategory> candidates = categoryRepository.all().stream()
                .filter(cat -> !cat.id().equals(category.id()))
                .toList();
        if (slot >= 0 && slot < candidates.size()) {
            saveCategory(
                    player,
                    viewer,
                    category.withParentCategoryId(
                            Optional.of(candidates.get(slot).id())));
        }
    }

    private void promptCreateCategory(Player player, PlayerRef viewer) {
        prompt(
                player,
                viewer,
                "warp.category.create-name",
                WarpsMessageKey.WARP_EDITOR_CATEGORY_PROMPT_CREATE,
                name -> {
                    String clean = sanitizeId(name);
                    if (clean.isEmpty()) {
                        player.sendMessage(text(viewer, WarpsMessageKey.WARP_MANAGER_ERROR_INVALID_NAME));
                        return;
                    }
                    WarpCategory category =
                            new WarpCategory(clean, name, Optional.empty(), List.of(), 0, Optional.empty());
                    categoryRepository.save(category);
                    categorySettingsView.open(player, viewer, category);
                },
                () -> categoryManagerView.open(player, viewer));
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

    private void assignCategory(Player player, PlayerRef viewer, String warpName, Optional<String> categoryId) {
        Optional<Warp> warp = warpRepository.find(WarpName.of(warpName));
        warp.ifPresent(value -> warpRepository.save(value.withCategoryId(categoryId)));
        editorView.open(player, viewer, warpName, null);
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

    private static String sanitizeId(String name) {
        String clean = name.trim().toLowerCase(java.util.Locale.ROOT);
        return clean.contains(" ") ? "" : clean;
    }

    private static List<String> splitLines(String input) {
        return input.equalsIgnoreCase("none") ? List.of() : java.util.Arrays.asList(input.split("\\|"));
    }
}
