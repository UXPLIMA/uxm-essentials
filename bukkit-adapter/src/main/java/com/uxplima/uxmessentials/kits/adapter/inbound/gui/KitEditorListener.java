package com.uxplima.uxmessentials.kits.adapter.inbound.gui;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
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

import com.uxplima.uxmessentials.kits.adapter.KitServices;
import com.uxplima.uxmessentials.kits.application.KitsMessageKey;
import com.uxplima.uxmessentials.kits.application.port.KitCategoryRepository;
import com.uxplima.uxmessentials.kits.domain.KitCost;
import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.InputRequest;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Handles close and click events for the bespoke Kit administration GUIs (KitEditorView, KitSettingsView and the
 * category GUIs). The admin kit-manager list itself now renders through the menu engine, so its clicks are handled
 * there; this listener owns only the still-bespoke editor windows. The click handler dispatches by inventory-holder
 * type to one focused per-GUI handler, each of which is self-contained: it cancels the click, re-checks the optional
 * collaborators it needs, and acts. Every kit/category edit is expressed through a single-field copy method
 * on the domain value object, so an edit never silently defaults the kit's other settings.
 */
@NullMarked
public final class KitEditorListener implements Listener {

    private final KitEditorView editorView;
    private final @Nullable KitServices services;
    private final @Nullable KitSettingsView settingsView;
    private final @Nullable TextInput textInput;
    private final @Nullable KitEditorText text;
    private final @Nullable KitCategoryEditing categoryEditing;

    public KitEditorListener(KitEditorView editorView) {
        this.editorView = Objects.requireNonNull(editorView, "editorView");
        this.services = null;
        this.settingsView = null;
        this.textInput = null;
        this.text = null;
        this.categoryEditing = null;
    }

    public KitEditorListener(
            KitEditorView editorView,
            KitServices services,
            KitCategoryRepository categoryRepository,
            KitSettingsView settingsView,
            TextInput textInput,
            Messages messages) {
        this.editorView = Objects.requireNonNull(editorView, "editorView");
        this.services = Objects.requireNonNull(services, "services");
        Objects.requireNonNull(categoryRepository, "categoryRepository");
        this.settingsView = Objects.requireNonNull(settingsView, "settingsView");
        this.textInput = Objects.requireNonNull(textInput, "textInput");
        this.text = new KitEditorText(Objects.requireNonNull(messages, "messages"));
        this.categoryEditing = new KitCategoryEditing(services, categoryRepository, textInput, this.text);
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
        if (holder instanceof KitSettingsHolder h) {
            event.setCancelled(true);
            onSettingsClick(player, h, slot);
        } else if (holder instanceof KitCategoryManagerHolder h) {
            event.setCancelled(true);
            if (categoryEditing != null) {
                categoryEditing.onCategoryManagerClick(player, h, slot);
            }
        } else if (holder instanceof KitCategorySettingsHolder h) {
            event.setCancelled(true);
            if (categoryEditing != null) {
                categoryEditing.onCategorySettingsClick(player, h, slot);
            }
        } else if (holder instanceof KitCategorySelectorHolder h) {
            event.setCancelled(true);
            if (categoryEditing != null && settingsView != null) {
                categoryEditing.onCategorySelectorClick(player, h, slot, settingsView);
            }
        } else if (holder instanceof KitCategoryParentSelectorHolder h) {
            event.setCancelled(true);
            if (categoryEditing != null) {
                categoryEditing.onCategoryParentSelectorClick(player, h, slot);
            }
        }
    }

    private void onSettingsClick(Player player, KitSettingsHolder settingsHolder, int slot) {
        if (services == null || settingsView == null || textInput == null) {
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
        prompt(
                player,
                viewer,
                "kit.cooldown",
                KitsMessageKey.KIT_EDITOR_PROMPT_COOLDOWN,
                input -> {
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
                },
                () -> reopenSettings(player, viewer, kit));
    }

    private void editCost(Player player, PlayerRef viewer, KitDefinition kit) {
        prompt(
                player,
                viewer,
                "kit.cost",
                KitsMessageKey.KIT_EDITOR_PROMPT_COST,
                input -> {
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
                },
                () -> reopenSettings(player, viewer, kit));
    }

    private void editDisplayName(Player player, PlayerRef viewer, KitDefinition kit) {
        prompt(
                player,
                viewer,
                "kit.display-name",
                KitsMessageKey.KIT_EDITOR_PROMPT_DISPLAY_NAME,
                input -> {
                    Optional<String> name = input.equalsIgnoreCase("none") ? Optional.empty() : Optional.of(input);
                    saveAndReopen(player, viewer, kit.withDisplayName(name));
                },
                () -> reopenSettings(player, viewer, kit));
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
                "kit.display-lore",
                KitsMessageKey.KIT_EDITOR_PROMPT_DISPLAY_LORE,
                input -> saveAndReopen(player, viewer, kit.withDisplayLore(splitLines(input))),
                () -> reopenSettings(player, viewer, kit));
    }

    private void editCommands(Player player, PlayerRef viewer, KitDefinition kit) {
        prompt(
                player,
                viewer,
                "kit.commands",
                KitsMessageKey.KIT_EDITOR_PROMPT_COMMANDS,
                input -> saveAndReopen(player, viewer, kit.withCommands(splitLines(input))),
                () -> reopenSettings(player, viewer, kit));
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

    private void saveAndReopen(Player player, PlayerRef viewer, KitDefinition kit) {
        Objects.requireNonNull(services).kitEditor().save(viewer, kit);
        Objects.requireNonNull(settingsView).open(player, viewer, kit);
    }

    private void reopenSettings(Player player, PlayerRef viewer, KitDefinition kit) {
        Objects.requireNonNull(settingsView).open(player, viewer, kit);
    }

    private void openManager(Player player, PlayerRef viewer) {
        KitManagerMenu manager = Objects.requireNonNull(services).kitManager();
        if (manager != null) {
            manager.open(player, viewer);
        }
    }

    private void prompt(
            Player player,
            PlayerRef viewer,
            String inputKey,
            MessageKey key,
            java.util.function.Consumer<String> action,
            Runnable reopen) {
        player.closeInventory();
        Objects.requireNonNull(textInput).prompt(player, viewer, InputRequest.of(inputKey, key), action, reopen);
    }

    private static List<String> splitLines(String input) {
        return input.equalsIgnoreCase("none") ? List.of() : Arrays.asList(input.split("\\|"));
    }

    private Component text(PlayerRef viewer, MessageKey key) {
        return text == null ? Component.empty() : text.text(viewer, key);
    }
}
