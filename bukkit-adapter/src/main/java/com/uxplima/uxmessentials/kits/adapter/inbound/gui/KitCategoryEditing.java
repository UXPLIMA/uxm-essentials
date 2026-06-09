package com.uxplima.uxmessentials.kits.adapter.inbound.gui;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.kits.adapter.KitServices;
import com.uxplima.uxmessentials.kits.adapter.inbound.listener.ChatPromptListener;
import com.uxplima.uxmessentials.kits.application.KitsMessageKey;
import com.uxplima.uxmessentials.kits.application.port.KitCategoryRepository;
import com.uxplima.uxmessentials.kits.domain.KitCategory;
import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Handles the click events for the kit-category administration GUIs (the category manager, a category's
 * settings, the kit→category selector, and the parent-category selector). The kit-editor listener owns the
 * inventory dispatch and forwards each category-holder click here, so the category logic lives in one focused
 * collaborator rather than padding the listener. Each handler re-checks the optional collaborators it needs and
 * acts; a save reopens the relevant menu so the operator sees the result, and every prompt resolves its label
 * from a {@link MessageKey} in the viewer's locale through the shared {@link KitEditorText}.
 */
@NullMarked
final class KitCategoryEditing {

    private final KitServices services;
    private final KitCategoryRepository categoryRepository;
    private final ChatPromptListener promptListener;
    private final KitEditorText text;

    KitCategoryEditing(
            KitServices services,
            KitCategoryRepository categoryRepository,
            ChatPromptListener promptListener,
            KitEditorText text) {
        this.services = Objects.requireNonNull(services, "services");
        this.categoryRepository = Objects.requireNonNull(categoryRepository, "categoryRepository");
        this.promptListener = Objects.requireNonNull(promptListener, "promptListener");
        this.text = Objects.requireNonNull(text, "text");
    }

    void promptCreateCategory(Player player, PlayerRef viewer) {
        prompt(player, viewer, KitsMessageKey.KIT_EDITOR_CATEGORY_PROMPT_CREATE, name -> {
            String clean = sanitizeId(name);
            if (clean.isEmpty()) {
                player.sendMessage(text.text(viewer, KitsMessageKey.KIT_EDITOR_ERROR_INVALID_NAME));
                return;
            }
            KitCategory category = new KitCategory(clean, name, Optional.empty(), List.of(), 0, Optional.empty());
            categoryRepository.save(category);
            openCategorySettings(player, viewer, category);
        });
    }

    void onCategoryManagerClick(Player player, KitCategoryManagerHolder managerHolder, int slot) {
        PlayerRef viewer = managerHolder.viewer();
        if (slot == 53) {
            openManager(player, viewer);
        } else if (slot == 49) {
            promptCreateCategory(player, viewer);
        } else {
            List<KitCategory> categories = managerHolder.categories();
            if (slot >= 0 && slot < categories.size()) {
                openCategorySettings(player, viewer, categories.get(slot));
            }
        }
    }

    void onCategorySettingsClick(Player player, KitCategorySettingsHolder settingsHolder, int slot) {
        PlayerRef viewer = settingsHolder.viewer();
        KitCategory category = settingsHolder.category();
        if (slot == 26) {
            KitCategoryManagerView managerView = services.kitCategoryManagerView();
            if (managerView != null) {
                managerView.open(player, viewer);
            }
            return;
        }
        switch (slot) {
            case 10 -> editCategoryName(player, viewer, category);
            case 12 -> editCategoryMaterial(player, viewer, category);
            case 14 -> editCategoryLore(player, viewer, category);
            case 16 -> editCategorySlot(player, viewer, category);
            case 18 -> openParentSelector(player, viewer, category);
            case 22 -> deleteCategory(player, viewer, category);
            default -> {
                // no-op: a click outside the action slots
            }
        }
    }

    private void editCategoryName(Player player, PlayerRef viewer, KitCategory category) {
        prompt(
                player,
                viewer,
                KitsMessageKey.KIT_EDITOR_CATEGORY_SETTINGS_DISPLAY_NAME_PROMPT,
                input -> saveCategory(player, viewer, category.withDisplayName(input)));
    }

    private void editCategoryMaterial(Player player, PlayerRef viewer, KitCategory category) {
        Material hand = player.getInventory().getItemInMainHand().getType();
        if (hand.isAir()) {
            player.sendMessage(text.text(viewer, KitsMessageKey.KIT_EDITOR_ERROR_EMPTY_HAND));
            return;
        }
        saveCategory(player, viewer, category.withDisplayMaterial(Optional.of(hand.name())));
    }

    private void editCategoryLore(Player player, PlayerRef viewer, KitCategory category) {
        prompt(
                player,
                viewer,
                KitsMessageKey.KIT_EDITOR_CATEGORY_SETTINGS_DISPLAY_LORE_PROMPT,
                input -> saveCategory(player, viewer, category.withDisplayLore(splitLines(input))));
    }

    private void editCategorySlot(Player player, PlayerRef viewer, KitCategory category) {
        prompt(player, viewer, KitsMessageKey.KIT_EDITOR_CATEGORY_SETTINGS_SLOT_PROMPT, input -> {
            int targetSlot;
            try {
                targetSlot = Integer.parseInt(input);
            } catch (NumberFormatException e) {
                player.sendMessage(text.text(viewer, KitsMessageKey.KIT_EDITOR_ERROR_INVALID_NUMBER));
                return;
            }
            saveCategory(player, viewer, category.withSlot(targetSlot));
        });
    }

    private void openParentSelector(Player player, PlayerRef viewer, KitCategory category) {
        KitCategoryParentSelectorView view = services.kitCategoryParentSelectorView();
        if (view != null) {
            view.open(player, viewer, category);
        }
    }

    private void deleteCategory(Player player, PlayerRef viewer, KitCategory category) {
        categoryRepository.delete(category.id());
        KitCategoryManagerView view = services.kitCategoryManagerView();
        if (view != null) {
            view.open(player, viewer);
        }
    }

    void onCategorySelectorClick(
            Player player, KitCategorySelectorHolder selectorHolder, int slot, KitSettingsView settingsView) {
        PlayerRef viewer = selectorHolder.viewer();
        KitDefinition kit = selectorHolder.kit();
        if (slot == 53) {
            settingsView.open(player, viewer, kit);
            return;
        }
        if (slot == 49) {
            saveAndReopen(player, viewer, kit.withCategoryId(Optional.empty()), settingsView);
            return;
        }
        List<KitCategory> categories = categoryRepository.all();
        if (slot >= 0 && slot < categories.size()) {
            saveAndReopen(
                    player,
                    viewer,
                    kit.withCategoryId(Optional.of(categories.get(slot).id())),
                    settingsView);
        }
    }

    void onCategoryParentSelectorClick(Player player, KitCategoryParentSelectorHolder selectorHolder, int slot) {
        PlayerRef viewer = selectorHolder.viewer();
        KitCategory category = selectorHolder.category();
        if (slot == 53) {
            openCategorySettings(player, viewer, category);
            return;
        }
        if (slot == 49) {
            saveCategory(player, viewer, category.withParentCategoryId(Optional.empty()));
            return;
        }
        List<KitCategory> candidates = categoryRepository.all().stream()
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

    private void saveAndReopen(Player player, PlayerRef viewer, KitDefinition kit, KitSettingsView settingsView) {
        services.kitEditor().save(viewer, kit);
        settingsView.open(player, viewer, kit);
    }

    private void saveCategory(Player player, PlayerRef viewer, KitCategory category) {
        categoryRepository.save(category);
        openCategorySettings(player, viewer, category);
    }

    private void openManager(Player player, PlayerRef viewer) {
        KitManagerView view = services.kitManagerView();
        if (view != null) {
            view.open(player, viewer);
        }
    }

    private void openCategorySettings(Player player, PlayerRef viewer, KitCategory category) {
        KitCategorySettingsView view = services.kitCategorySettingsView();
        if (view != null) {
            view.open(player, viewer, category);
        }
    }

    private void prompt(Player player, PlayerRef viewer, MessageKey key, Consumer<String> action) {
        player.closeInventory();
        promptListener.prompt(player, text.text(viewer, key), action);
    }

    private static String sanitizeId(String name) {
        String clean = name.trim().toLowerCase(java.util.Locale.ROOT);
        return clean.contains(" ") ? "" : clean;
    }

    private static List<String> splitLines(String input) {
        return input.equalsIgnoreCase("none") ? List.of() : java.util.Arrays.asList(input.split("\\|"));
    }
}
