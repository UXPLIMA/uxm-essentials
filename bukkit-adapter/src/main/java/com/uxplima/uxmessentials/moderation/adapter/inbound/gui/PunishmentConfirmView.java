package com.uxplima.uxmessentials.moderation.adapter.inbound.gui;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.moderation.application.ModerationMessageKey;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.gui.Guis;
import com.uxplima.uxmlib.gui.SimpleGui;
import com.uxplima.uxmlib.gui.anvil.AnvilInput;
import com.uxplima.uxmlib.gui.anvil.AnvilResult;
import com.uxplima.uxmlib.gui.item.GuiItem;
import com.uxplima.uxmlib.item.ItemBuilder;
import com.uxplima.uxmlib.item.SkullData;
import org.jspecify.annotations.NullMarked;

/**
 * The per-target confirm screen the bare {@code /ban}/{@code /mute} GUI flow opens once a target is chosen in the
 * {@link com.uxplima.uxmessentials.shared.adapter.inbound.gui.PlayerPickerView}. It shows the target's head, two
 * unambiguous confirm actions — apply (broadcast) and apply silently — plus an optional "set reason" button that
 * captures a free-text reason through a vanilla anvil, and a back button to the picker. One view serves both
 * sanctions: the {@link PunishmentAction} supplies the title and the two confirm-button labels, and the caller
 * passes the {@link PunishmentAction.Executor} the confirm click runs, so this class holds no ban/mute-specific
 * branch.
 *
 * <p>Every visible string is a catalog key in the viewer's locale; the target name and the typed reason are
 * substituted outside any tag argument, per the UI canon. The confirm click hops to the actor's entity region
 * thread, runs the audited use-case call there, and closes — the use case itself kicks/notifies and broadcasts.
 * The reason is carried across the anvil round-trip by reopening the screen with the captured value, so the menu
 * stays single-viewer and stateless between opens. {@link #confirm} is package-private so a test can drive the
 * normal/silent click and assert the executor sees the right {@code silent} flag without a live inventory.
 */
@NullMarked
public final class PunishmentConfirmView {

    private static final int ROWS = 3;
    private static final int TARGET_SLOT = 4;
    private static final int APPLY_SLOT = 10;
    private static final int SILENT_SLOT = 12;
    private static final int REASON_SLOT = 14;
    private static final int BACK_SLOT = 22;
    private static final Material FILLER = Material.GRAY_STAINED_GLASS_PANE;
    private static final Material APPLY_ICON = Material.REDSTONE_BLOCK;
    private static final Material SILENT_ICON = Material.BARRIER;
    private static final Material REASON_ICON = Material.WRITABLE_BOOK;
    private static final Material BACK_ICON = Material.ARROW;

    private final GuiText guiText;
    private final Scheduler scheduler;
    private final AnvilInput anvil;

    public PunishmentConfirmView(GuiText guiText, Scheduler scheduler, AnvilInput anvil) {
        this.guiText = Objects.requireNonNull(guiText, "guiText");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.anvil = Objects.requireNonNull(anvil, "anvil");
    }

    /**
     * Open the confirm screen for {@code target}. {@code executor} performs the audited use-case call on confirm;
     * {@code onBack} reopens the picker from the back button. The reason starts empty and is captured through the
     * reason button's anvil.
     */
    public void open(
            Player viewer,
            PlayerRef actor,
            PlayerRef target,
            PunishmentAction action,
            PunishmentAction.Executor executor,
            Runnable onBack) {
        open(viewer, actor, target, action, executor, onBack, Optional.empty());
    }

    private void open(
            Player viewer,
            PlayerRef actor,
            PlayerRef target,
            PunishmentAction action,
            PunishmentAction.Executor executor,
            Runnable onBack,
            Optional<String> reason) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(onBack, "onBack");
        Objects.requireNonNull(reason, "reason");
        scheduler.onEntity(
                actor,
                () -> build(viewer, actor, target, action, executor, onBack, reason)
                        .open(viewer));
    }

    private SimpleGui build(
            Player viewer,
            PlayerRef actor,
            PlayerRef target,
            PunishmentAction action,
            PunishmentAction.Executor executor,
            Runnable onBack,
            Optional<String> reason) {
        Component title = guiText.text(actor, action.confirmTitle(), Map.of("player", target.name()));
        SimpleGui gui = Guis.gui().title(title).rows(ROWS).build();
        fill(gui);
        gui.set(TARGET_SLOT, GuiItem.display(targetHead(actor, target)));
        gui.set(
                APPLY_SLOT,
                GuiItem.button(applyIcon(actor, action), e -> confirm(viewer, actor, target, executor, reason, false)));
        // /banip has no silent form, so its confirm screen omits the silent button; every other verb shows both.
        if (action.silentSupported()) {
            gui.set(
                    SILENT_SLOT,
                    GuiItem.button(
                            silentIcon(actor, action), e -> confirm(viewer, actor, target, executor, reason, true)));
        }
        gui.set(
                REASON_SLOT,
                GuiItem.button(
                        reasonIcon(actor, reason), e -> promptReason(viewer, actor, target, action, executor, onBack)));
        gui.set(BACK_SLOT, GuiItem.button(backIcon(actor), e -> onBack.run()));
        return gui;
    }

    /**
     * Run the executor for the chosen target on the actor's entity thread and close the screen. Package-private
     * so a test drives the normal vs silent button and asserts the {@code silent} flag the executor receives.
     */
    void confirm(
            Player viewer,
            PlayerRef actor,
            PlayerRef target,
            PunishmentAction.Executor executor,
            Optional<String> reason,
            boolean silent) {
        scheduler.onEntity(actor, () -> {
            viewer.closeInventory();
            executor.execute(actor, target, reason, silent);
        });
    }

    /** Open the anvil to capture a reason; a submission reopens the confirm screen carrying the typed reason. */
    private void promptReason(
            Player viewer,
            PlayerRef actor,
            PlayerRef target,
            PunishmentAction action,
            PunishmentAction.Executor executor,
            Runnable onBack) {
        scheduler.onEntity(
                actor,
                () -> anvil.open(viewer, reasonPrompt(actor), result -> {
                    Optional<String> reason = result instanceof AnvilResult.Submitted submitted
                                    && !submitted.text().isBlank()
                            ? Optional.of(submitted.text().strip())
                            : Optional.empty();
                    open(viewer, actor, target, action, executor, onBack, reason);
                }));
    }

    private ItemStack targetHead(PlayerRef viewer, PlayerRef target) {
        return ItemBuilder.of(Material.PLAYER_HEAD)
                .name(guiText.text(
                        viewer, ModerationMessageKey.MOD_GUI_CONFIRM_TARGET_NAME, Map.of("player", target.name())))
                .lore(List.of(guiText.text(viewer, ModerationMessageKey.MOD_GUI_CONFIRM_TARGET_LORE)))
                .skull(SkullData.ofUuid(target.uuid()))
                .build();
    }

    private ItemStack applyIcon(PlayerRef viewer, PunishmentAction action) {
        return labelled(viewer, APPLY_ICON, action.applyLabel(), action.applyLore());
    }

    private ItemStack silentIcon(PlayerRef viewer, PunishmentAction action) {
        // Only reached when silentSupported() is true, so the two labels are present.
        return labelled(
                viewer,
                SILENT_ICON,
                action.silentLabel().orElseThrow(),
                action.silentLore().orElseThrow());
    }

    private ItemStack reasonIcon(PlayerRef viewer, Optional<String> reason) {
        Component lore = reason.map(set -> guiText.text(
                        viewer, ModerationMessageKey.MOD_GUI_CONFIRM_REASON_SET_LORE, Map.of("reason", set)))
                .orElseGet(() -> guiText.text(viewer, ModerationMessageKey.MOD_GUI_CONFIRM_REASON_NONE_LORE));
        return ItemBuilder.of(REASON_ICON)
                .name(guiText.text(viewer, ModerationMessageKey.MOD_GUI_CONFIRM_REASON))
                .lore(List.of(lore))
                .build();
    }

    private ItemStack reasonPrompt(PlayerRef viewer) {
        return ItemBuilder.of(REASON_ICON)
                .name(guiText.text(viewer, ModerationMessageKey.MOD_GUI_CONFIRM_REASON_PROMPT))
                .build();
    }

    private ItemStack backIcon(PlayerRef viewer) {
        return ItemBuilder.of(BACK_ICON)
                .name(guiText.text(viewer, ModerationMessageKey.MOD_GUI_CONFIRM_BACK))
                .build();
    }

    private ItemStack labelled(PlayerRef viewer, Material material, MessageKey name, MessageKey lore) {
        return ItemBuilder.of(material)
                .name(guiText.text(viewer, name))
                .lore(List.of(guiText.text(viewer, lore)))
                .build();
    }

    private void fill(SimpleGui gui) {
        ItemStack filler = ItemBuilder.of(FILLER).name(Component.empty()).build();
        for (int slot = 0; slot < ROWS * 9; slot++) {
            gui.set(slot, GuiItem.display(filler));
        }
    }
}
