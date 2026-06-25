package com.uxplima.uxmessentials.warps.adapter.inbound.gui;

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

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.WarpEditorLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.InputRequest;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.warps.application.UseWarp;
import com.uxplima.uxmessentials.warps.application.WarpsMessageKey;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import com.uxplima.uxmessentials.warps.domain.WarpName;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class WarpEditorListener implements Listener {

    private final WarpEditorView editorView;
    private final TextInput textInput;
    private final Messages messages;
    private final EditableWarpLoader loader;
    private final WarpSubMenuClicks subMenuClicks;
    private final UseWarp useWarp;
    private final PlayerWarpGoToHandle playerWarpGoTo;

    private final WarpSoundSelectorView soundSelectorView;
    private final WarpParticleSelectorView particleSelectorView;
    private final WarpWelcomeMessagesView welcomeMessagesView;

    private final WarpCategorySelectorView categorySelectorView;
    private final WarpCategoryEditing categoryEditing;
    private final WarpSoundMenu soundMenu;

    public WarpEditorListener(
            WarpEditorView editorView,
            WarpRepository warpRepository,
            TextInput textInput,
            Messages messages,
            WarpSoundSelectorView soundSelectorView,
            WarpParticleSelectorView particleSelectorView,
            WarpWelcomeMessagesView welcomeMessagesView,
            UseWarp useWarp,
            PlayerWarpGoToHandle playerWarpGoTo,
            WarpCategorySelectorView categorySelectorView,
            WarpCategoryEditing categoryEditing,
            WarpSoundMenu soundMenu) {
        this.editorView = Objects.requireNonNull(editorView, "editorView");
        this.textInput = Objects.requireNonNull(textInput, "textInput");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.soundSelectorView = Objects.requireNonNull(soundSelectorView, "soundSelectorView");
        this.particleSelectorView = Objects.requireNonNull(particleSelectorView, "particleSelectorView");
        this.welcomeMessagesView = Objects.requireNonNull(welcomeMessagesView, "welcomeMessagesView");
        this.useWarp = Objects.requireNonNull(useWarp, "useWarp");
        this.playerWarpGoTo = Objects.requireNonNull(playerWarpGoTo, "playerWarpGoTo");
        this.categorySelectorView = Objects.requireNonNull(categorySelectorView, "categorySelectorView");
        this.categoryEditing = Objects.requireNonNull(categoryEditing, "categoryEditing");
        this.soundMenu = Objects.requireNonNull(soundMenu, "soundMenu");
        this.loader = new EditableWarpLoader(Objects.requireNonNull(warpRepository, "warpRepository"), editorView);
        this.subMenuClicks = new WarpSubMenuClicks(
                editorView, textInput, this.loader, soundSelectorView, particleSelectorView, welcomeMessagesView);
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
            subMenuClicks.handleSoundSelectorClick(player, soundHolder, slot);
            return;
        }

        if (holder instanceof WarpParticleSelectorHolder particleHolder) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            Player player = (Player) event.getWhoClicked();
            subMenuClicks.handleParticleSelectorClick(player, particleHolder, slot);
            return;
        }

        if (holder instanceof WarpWelcomeMessagesHolder welcomeHolder) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            Player player = (Player) event.getWhoClicked();
            ClickType click = event.getClick();
            subMenuClicks.handleWelcomeMessagesClick(player, welcomeHolder, slot, click);
            return;
        }

        if (holder instanceof WarpCategoryManagerHolder categoryManagerHolder) {
            event.setCancelled(true);
            categoryEditing.onCategoryManagerClick(
                    (Player) event.getWhoClicked(), categoryManagerHolder, event.getRawSlot());
            return;
        }

        if (holder instanceof WarpCategorySettingsHolder categorySettingsHolder) {
            event.setCancelled(true);
            categoryEditing.onCategorySettingsClick(
                    (Player) event.getWhoClicked(), categorySettingsHolder, event.getRawSlot());
            return;
        }

        if (holder instanceof WarpCategorySelectorHolder categorySelectorHolder) {
            event.setCancelled(true);
            categoryEditing.onCategorySelectorClick(
                    (Player) event.getWhoClicked(), categorySelectorHolder, event.getRawSlot());
            return;
        }

        if (holder instanceof WarpCategoryParentSelectorHolder parentSelectorHolder) {
            event.setCancelled(true);
            categoryEditing.onCategoryParentSelectorClick(
                    (Player) event.getWhoClicked(), parentSelectorHolder, event.getRawSlot());
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

        EditableWarp warp = loader.load(warpName, owner);
        if (warp == null) {
            player.closeInventory();
            return;
        }
        handleEditorClick(player, viewer, warpName, owner, warp, slot, click);
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
        // The category button is shown only for server warps (owner == null); a click opens the warp→category
        // selector, where the operator assigns this warp to a category or clears it.
        if (owner == null && slot == WarpEditorView.CATEGORY_SLOT) {
            categorySelectorView.open(player, viewer, name);
            return;
        }
        if (slot == layout.iconSlot()) {
            editIcon(player, viewer, name, owner, warp, click);
        } else if (slot == layout.teleportSlot()) {
            goTo(player, viewer, name, owner);
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

    /**
     * The "go to" navigation action: close the editor, then teleport the viewer to the warp through the same
     * path the {@code /warp <name>} command uses ({@code UseWarp} for a server warp, the late-bound player-warp
     * go-to for a player warp). Closing before the teleport keeps a rapid double-click from firing two hops, and
     * the teleport executor itself is region-aware (Folia-safe {@code teleportAsync}). No confirm — it is a
     * navigation action — and the warp's own access/cost gate still applies inside the use case.
     */
    private void goTo(Player player, PlayerRef viewer, String name, @Nullable PlayerRef owner) {
        player.closeInventory();
        if (owner == null) {
            useWarp.use(viewer, WarpName.of(name));
        } else {
            playerWarpGoTo.teleport(viewer, owner, name);
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
            textInput.prompt(
                    player,
                    viewer,
                    InputRequest.of("warp.password", WarpsMessageKey.WARP_EDITOR_PASSWORD_PROMPT),
                    input -> {
                        warp.setPassword(Optional.of(input));
                        editorView.open(player, viewer, name, owner);
                    },
                    () -> editorView.open(player, viewer, name, owner));
        }
    }

    private void editSounds(
            Player player, PlayerRef viewer, String name, @Nullable PlayerRef owner, EditableWarp warp, ClickType c) {
        if (c == ClickType.SHIFT_LEFT || c == ClickType.SHIFT_RIGHT) {
            warp.clearSounds();
            editorView.open(player, viewer, name, owner);
        } else if (owner == null) {
            // Server warps render the selector through the menu engine — the migrated pilot path. Player warps still
            // use the original fixed view until Phase 3 migrates them with a player-warp subject.
            soundMenu.open(viewer, new WarpSoundEdit(WarpName.of(name), c == ClickType.LEFT));
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
            String key = warmup ? "warp.warmup" : "warp.cooldown";
            MessageKey promptKey =
                    warmup ? WarpsMessageKey.WARP_EDITOR_WARMUP_PROMPT : WarpsMessageKey.WARP_EDITOR_COOLDOWN_PROMPT;
            textInput.prompt(
                    player,
                    viewer,
                    InputRequest.of(key, promptKey),
                    input -> submitDuration(player, viewer, name, owner, warp, warmup, input),
                    () -> editorView.open(player, viewer, name, owner));
        }
    }

    private void submitDuration(
            Player player,
            PlayerRef viewer,
            String name,
            @Nullable PlayerRef owner,
            EditableWarp warp,
            boolean warmup,
            String input) {
        double seconds;
        try {
            seconds = Double.parseDouble(input);
        } catch (NumberFormatException e) {
            player.sendMessage(text(viewer, WarpsMessageKey.WARP_EDITOR_INVALID_NUMBER));
            editorView.open(player, viewer, name, owner);
            return;
        }
        applyDuration(warp, warmup, Optional.of(seconds));
        editorView.open(player, viewer, name, owner);
    }

    private void applyDuration(EditableWarp warp, boolean warmup, Optional<Double> seconds) {
        if (warmup) {
            warp.setWarmupOverride(seconds);
        } else {
            warp.setCooldownOverride(seconds);
        }
    }

    private Component text(PlayerRef viewer, MessageKey key) {
        return StyledText.render(messages.resolve(viewer, key, Map.of()));
    }
}
