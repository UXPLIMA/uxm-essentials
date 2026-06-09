package com.uxplima.uxmessentials.kits.adapter.inbound.gui;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import com.uxplima.uxmessentials.kits.adapter.KitServices;
import com.uxplima.uxmessentials.kits.adapter.inbound.listener.ChatPromptListener;
import com.uxplima.uxmessentials.kits.application.KitsMessageKey;
import com.uxplima.uxmessentials.kits.application.port.KitCategoryRepository;
import com.uxplima.uxmessentials.kits.application.port.KitRepository;
import com.uxplima.uxmessentials.kits.domain.KitCategory;
import com.uxplima.uxmessentials.kits.domain.KitCost;
import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.kits.domain.KitId;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayout;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Handles close and click events for all Kit administration GUIs (KitEditorView, KitManagerView,
 * KitSettingsView and the category GUIs). The click handler dispatches by inventory-holder type to one
 * focused per-GUI handler, each of which is self-contained: it cancels the click, re-checks the optional
 * collaborators it needs, and acts. Every kit/category edit is expressed through a single-field copy method
 * on the domain value object, so an edit never silently defaults the kit's other settings.
 */
@NullMarked
public final class KitEditorListener implements Listener {

    private final KitEditorView editorView;
    private final @Nullable KitServices services;
    private final @Nullable KitRepository repository;
    private final @Nullable KitCategoryRepository categoryRepository;
    private final @Nullable KitSettingsView settingsView;
    private final @Nullable ChatPromptListener promptListener;
    private final @Nullable Messages messages;
    private final @Nullable MiniMessage miniMessage;

    public KitEditorListener(KitEditorView editorView) {
        this.editorView = Objects.requireNonNull(editorView, "editorView");
        this.services = null;
        this.repository = null;
        this.categoryRepository = null;
        this.settingsView = null;
        this.promptListener = null;
        this.messages = null;
        this.miniMessage = null;
    }

    public KitEditorListener(
            KitEditorView editorView,
            KitServices services,
            KitRepository repository,
            KitCategoryRepository categoryRepository,
            KitSettingsView settingsView,
            ChatPromptListener promptListener,
            Messages messages) {
        this.editorView = Objects.requireNonNull(editorView, "editorView");
        this.services = Objects.requireNonNull(services, "services");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.categoryRepository = Objects.requireNonNull(categoryRepository, "categoryRepository");
        this.settingsView = Objects.requireNonNull(settingsView, "settingsView");
        this.promptListener = Objects.requireNonNull(promptListener, "promptListener");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.miniMessage = MiniMessage.miniMessage();
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof KitEditorHolder editorHolder) {
            editorView.onClose(editorHolder);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory inventory = event.getView().getTopInventory();
        InventoryHolder holder = inventory.getHolder();
        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();
        if (holder instanceof KitManagerHolder h) {
            event.setCancelled(true);
            onManagerClick(player, h, slot);
        } else if (holder instanceof KitSettingsHolder h) {
            event.setCancelled(true);
            onSettingsClick(player, h, slot);
        } else if (holder instanceof KitCreateChooserHolder h) {
            event.setCancelled(true);
            onCreateChooserClick(player, h, slot);
        } else if (holder instanceof KitCategoryManagerHolder h) {
            event.setCancelled(true);
            onCategoryManagerClick(player, h, slot);
        } else if (holder instanceof KitCategorySettingsHolder h) {
            event.setCancelled(true);
            onCategorySettingsClick(player, h, slot);
        } else if (holder instanceof KitCategorySelectorHolder h) {
            event.setCancelled(true);
            onCategorySelectorClick(player, h, slot);
        } else if (holder instanceof KitCategoryParentSelectorHolder h) {
            event.setCancelled(true);
            onCategoryParentSelectorClick(player, h, slot);
        }
    }

    private void onManagerClick(Player player, KitManagerHolder managerHolder, int slot) {
        if (services == null || settingsView == null || services.kitManagerView() == null) {
            return;
        }
        PlayerRef viewer = managerHolder.viewer();
        GuiLayout layout = services.kitManagerView().layout();
        if (slot == layout.prevSlot()) {
            if (services.kitCreateChooserView() != null) {
                services.kitCreateChooserView().open(player, viewer);
            }
        } else if (slot == layout.nextSlot()) {
            player.closeInventory();
        } else if (slot == 51) {
            if (services.kitCategoryManagerView() != null) {
                services.kitCategoryManagerView().open(player, viewer);
            }
        } else {
            int index = managerContentSlots(layout).indexOf(slot);
            if (index >= 0 && index < managerHolder.kits().size()) {
                settingsView.open(player, viewer, managerHolder.kits().get(index));
            }
        }
    }

    private static List<Integer> managerContentSlots(GuiLayout layout) {
        return layout.explicitContentSlots().orElseGet(() -> {
            List<Integer> defaults = new ArrayList<>();
            int contentLimit = (layout.rows() - 1) * 9;
            for (int i = 0; i < contentLimit; i++) {
                defaults.add(i);
            }
            return defaults;
        });
    }

    private void onSettingsClick(Player player, KitSettingsHolder settingsHolder, int slot) {
        if (services == null || settingsView == null || promptListener == null) {
            return;
        }
        PlayerRef viewer = settingsHolder.viewer();
        KitDefinition kit = settingsHolder.kit();
        GuiLayout layout = settingsView.layout();
        if (slot == layout.prevSlot()) {
            openManager(player, viewer);
            return;
        }
        dispatchSettingsAction(player, viewer, kit, settingsAction(layout, slot));
    }

    private int settingsAction(GuiLayout layout, int slot) {
        List<Integer> slots = layout.contentSlots();
        if (slots.size() >= 12) {
            return slots.indexOf(slot);
        }
        return switch (slot) {
            case 0 -> 0;
            case 2 -> 1;
            case 4 -> 2;
            case 6 -> 3;
            case 8 -> 4;
            case 10 -> 5;
            case 12 -> 6;
            case 14 -> 7;
            case 16 -> 8;
            case 22 -> 9;
            case 18 -> 10;
            case 20 -> 11;
            default -> -1;
        };
    }

    private void dispatchSettingsAction(Player player, PlayerRef viewer, KitDefinition kit, int action) {
        switch (action) {
            case 0 -> editItems(player, viewer, kit);
            case 1 -> saveAndReopen(player, viewer, kit.withPermission(!kit.permission()));
            case 2 -> saveAndReopen(player, viewer, kit.withOneTime(!kit.oneTime()));
            case 3 -> editCooldown(player, viewer, kit);
            case 4 -> editCost(player, viewer, kit);
            case 5 -> editDisplayName(player, viewer, kit);
            case 6 -> editDisplayMaterial(player, viewer, kit);
            case 7 -> editDisplayLore(player, viewer, kit);
            case 8 -> editCommands(player, viewer, kit);
            case 9 -> deleteKit(player, viewer, kit);
            case 10 -> saveAndReopen(player, viewer, kit.withFirstJoin(!kit.firstJoin()));
            case 11 -> saveAndReopen(player, viewer, kit.withAutoEquip(!kit.autoEquip()));
            case 12 -> openCategorySelector(player, viewer, kit);
            default -> {
                // no-op: a click outside the action slots
            }
        }
    }

    private void editItems(Player player, PlayerRef viewer, KitDefinition kit) {
        player.closeInventory();
        editorView.open(player, viewer, kit);
    }

    private void editCooldown(Player player, PlayerRef viewer, KitDefinition kit) {
        prompt(player, viewer, KitsMessageKey.KIT_EDITOR_PROMPT_COOLDOWN, input -> {
            long sec;
            try {
                sec = Long.parseLong(input);
            } catch (NumberFormatException e) {
                player.sendMessage(text(viewer, KitsMessageKey.KIT_EDITOR_ERROR_INVALID_NUMBER));
                return;
            }
            if (sec < 0) {
                player.sendMessage(text(viewer, KitsMessageKey.KIT_EDITOR_ERROR_NEGATIVE_COOLDOWN));
                return;
            }
            saveAndReopen(player, viewer, kit.withCooldown(Duration.ofSeconds(sec)));
        });
    }

    private void editCost(Player player, PlayerRef viewer, KitDefinition kit) {
        prompt(player, viewer, KitsMessageKey.KIT_EDITOR_PROMPT_COST, input -> {
            if (input.equalsIgnoreCase("free") || input.equalsIgnoreCase("0")) {
                saveAndReopen(player, viewer, kit.withCost(KitCost.free()));
                return;
            }
            BigDecimal amount;
            try {
                amount = new BigDecimal(input);
            } catch (NumberFormatException e) {
                player.sendMessage(text(viewer, KitsMessageKey.KIT_EDITOR_ERROR_INVALID_NUMBER));
                return;
            }
            if (amount.compareTo(BigDecimal.ZERO) < 0) {
                player.sendMessage(text(viewer, KitsMessageKey.KIT_EDITOR_ERROR_NEGATIVE_COST));
                return;
            }
            saveAndReopen(player, viewer, kit.withCost(KitCost.of(amount)));
        });
    }

    private void editDisplayName(Player player, PlayerRef viewer, KitDefinition kit) {
        prompt(player, viewer, KitsMessageKey.KIT_EDITOR_PROMPT_DISPLAY_NAME, input -> {
            Optional<String> name = input.equalsIgnoreCase("none") ? Optional.empty() : Optional.of(input);
            saveAndReopen(player, viewer, kit.withDisplayName(name));
        });
    }

    private void editDisplayMaterial(Player player, PlayerRef viewer, KitDefinition kit) {
        Material hand = player.getInventory().getItemInMainHand().getType();
        if (hand.isAir()) {
            player.sendMessage(text(viewer, KitsMessageKey.KIT_EDITOR_ERROR_EMPTY_HAND));
            return;
        }
        saveAndReopen(player, viewer, kit.withDisplayMaterial(Optional.of(hand.name())));
    }

    private void editDisplayLore(Player player, PlayerRef viewer, KitDefinition kit) {
        prompt(
                player,
                viewer,
                KitsMessageKey.KIT_EDITOR_PROMPT_DISPLAY_LORE,
                input -> saveAndReopen(player, viewer, kit.withDisplayLore(splitLines(input))));
    }

    private void editCommands(Player player, PlayerRef viewer, KitDefinition kit) {
        prompt(
                player,
                viewer,
                KitsMessageKey.KIT_EDITOR_PROMPT_COMMANDS,
                input -> saveAndReopen(player, viewer, kit.withCommands(splitLines(input))));
    }

    private void deleteKit(Player player, PlayerRef viewer, KitDefinition kit) {
        player.closeInventory();
        Objects.requireNonNull(services).delKit().delete(viewer, kit.id());
        openManager(player, viewer);
    }

    private void openCategorySelector(Player player, PlayerRef viewer, KitDefinition kit) {
        KitCategorySelectorView view = Objects.requireNonNull(services).kitCategorySelectorView();
        if (view != null) {
            view.open(player, viewer, kit);
        }
    }

    private void onCreateChooserClick(Player player, KitCreateChooserHolder chooserHolder, int slot) {
        if (services == null || promptListener == null || repository == null || categoryRepository == null) {
            return;
        }
        PlayerRef viewer = chooserHolder.viewer();
        if (slot == 22) {
            openManager(player, viewer);
        } else if (slot == 11) {
            promptCreateKit(player, viewer);
        } else if (slot == 15) {
            promptCreateCategory(player, viewer);
        }
    }

    private void promptCreateKit(Player player, PlayerRef viewer) {
        prompt(player, viewer, KitsMessageKey.KIT_EDITOR_PROMPT_CREATE, name -> {
            String clean = sanitizeId(name);
            if (clean.isEmpty()) {
                player.sendMessage(text(viewer, KitsMessageKey.KIT_EDITOR_ERROR_INVALID_NAME));
                return;
            }
            KitId id = KitId.of(clean);
            Objects.requireNonNull(services)
                    .createKit()
                    .create(viewer, new KitDefinition(id, List.of(), Duration.ZERO, false, false, KitCost.free()));
            KitSettingsView view = Objects.requireNonNull(settingsView);
            Objects.requireNonNull(repository).find(id).ifPresent(kit -> view.open(player, viewer, kit));
        });
    }

    private void promptCreateCategory(Player player, PlayerRef viewer) {
        prompt(player, viewer, KitsMessageKey.KIT_EDITOR_CATEGORY_PROMPT_CREATE, name -> {
            String clean = sanitizeId(name);
            if (clean.isEmpty()) {
                player.sendMessage(text(viewer, KitsMessageKey.KIT_EDITOR_ERROR_INVALID_NAME));
                return;
            }
            KitCategory category = new KitCategory(clean, name, Optional.empty(), List.of(), 0, Optional.empty());
            Objects.requireNonNull(categoryRepository).save(category);
            openCategorySettings(player, viewer, category);
        });
    }

    private void onCategoryManagerClick(Player player, KitCategoryManagerHolder managerHolder, int slot) {
        if (services == null || categoryRepository == null || promptListener == null) {
            return;
        }
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

    private void onCategorySettingsClick(Player player, KitCategorySettingsHolder settingsHolder, int slot) {
        if (services == null || categoryRepository == null || promptListener == null) {
            return;
        }
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
            player.sendMessage(text(viewer, KitsMessageKey.KIT_EDITOR_ERROR_EMPTY_HAND));
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
                player.sendMessage(text(viewer, KitsMessageKey.KIT_EDITOR_ERROR_INVALID_NUMBER));
                return;
            }
            saveCategory(player, viewer, category.withSlot(targetSlot));
        });
    }

    private void openParentSelector(Player player, PlayerRef viewer, KitCategory category) {
        KitCategoryParentSelectorView view = Objects.requireNonNull(services).kitCategoryParentSelectorView();
        if (view != null) {
            view.open(player, viewer, category);
        }
    }

    private void deleteCategory(Player player, PlayerRef viewer, KitCategory category) {
        Objects.requireNonNull(categoryRepository).delete(category.id());
        KitCategoryManagerView view = Objects.requireNonNull(services).kitCategoryManagerView();
        if (view != null) {
            view.open(player, viewer);
        }
    }

    private void onCategorySelectorClick(Player player, KitCategorySelectorHolder selectorHolder, int slot) {
        if (services == null || categoryRepository == null || settingsView == null) {
            return;
        }
        PlayerRef viewer = selectorHolder.viewer();
        KitDefinition kit = selectorHolder.kit();
        if (slot == 53) {
            settingsView.open(player, viewer, kit);
            return;
        }
        if (slot == 49) {
            saveAndReopen(player, viewer, kit.withCategoryId(Optional.empty()));
            return;
        }
        List<KitCategory> categories = categoryRepository.all();
        if (slot >= 0 && slot < categories.size()) {
            saveAndReopen(
                    player,
                    viewer,
                    kit.withCategoryId(Optional.of(categories.get(slot).id())));
        }
    }

    private void onCategoryParentSelectorClick(
            Player player, KitCategoryParentSelectorHolder selectorHolder, int slot) {
        if (services == null || categoryRepository == null) {
            return;
        }
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

    private void saveAndReopen(Player player, PlayerRef viewer, KitDefinition kit) {
        Objects.requireNonNull(services).kitEditor().save(viewer, kit);
        Objects.requireNonNull(settingsView).open(player, viewer, kit);
    }

    private void saveCategory(Player player, PlayerRef viewer, KitCategory category) {
        Objects.requireNonNull(categoryRepository).save(category);
        openCategorySettings(player, viewer, category);
    }

    private void openManager(Player player, PlayerRef viewer) {
        KitManagerView view = Objects.requireNonNull(services).kitManagerView();
        if (view != null) {
            view.open(player, viewer);
        }
    }

    private void openCategorySettings(Player player, PlayerRef viewer, KitCategory category) {
        KitCategorySettingsView view = Objects.requireNonNull(services).kitCategorySettingsView();
        if (view != null) {
            view.open(player, viewer, category);
        }
    }

    private void prompt(Player player, PlayerRef viewer, MessageKey key, java.util.function.Consumer<String> action) {
        player.closeInventory();
        Objects.requireNonNull(promptListener).prompt(player, text(viewer, key), action);
    }

    private static String sanitizeId(String name) {
        String clean = name.trim().toLowerCase(java.util.Locale.ROOT);
        return clean.contains(" ") ? "" : clean;
    }

    private static List<String> splitLines(String input) {
        return input.equalsIgnoreCase("none") ? List.of() : Arrays.asList(input.split("\\|"));
    }

    private Component text(PlayerRef viewer, MessageKey key) {
        return text(viewer, key, Map.of());
    }

    private Component text(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
        if (messages == null || miniMessage == null) {
            return Component.empty();
        }
        String prefixStr = messages.resolve(viewer, () -> "prefix", Map.of());
        TagResolver prefix = Placeholder.component("prefix", miniMessage.deserialize(prefixStr));
        String resolved = messages.resolve(viewer, key, placeholders);
        return miniMessage.deserialize(resolved, prefix);
    }
}
