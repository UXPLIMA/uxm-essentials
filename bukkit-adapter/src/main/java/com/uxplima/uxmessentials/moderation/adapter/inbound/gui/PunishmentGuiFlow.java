package com.uxplima.uxmessentials.moderation.adapter.inbound.gui;

import java.util.Objects;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.moderation.adapter.ModerationServices;
import com.uxplima.uxmessentials.moderation.application.ModerationMessageKey;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.PlayerPickerView;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The bare {@code /ban} / {@code /mute} GUI flow: a player picker into a per-target confirm screen, ending in the
 * existing audited use-case call. {@code BanCommand} and {@code MuteCommand} expose this as their
 * {@link com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration#guiRoot() bare-root}
 * opener, so {@code /ban} with no arguments opens the picker while {@code /ban <player> [-s] [reason]} stays the
 * raw subcommand. One flow instance serves both sanctions; {@link #open} is parameterised by {@link
 * PunishmentAction}.
 *
 * <p>The reusable {@link PlayerPickerView} stays generic: this flow supplies the picker title for the sanction,
 * the offline-name resolver (the moderation {@code TargetResolver}, so a typed offline name still resolves), the
 * unknown-player reply key, and the pick callback that opens {@link PunishmentConfirmView}. The confirm view's
 * executor is the only ban/mute-specific code — it routes the chosen target to {@code services.ban().ban(...)}
 * or the permanent {@code services.mute().mute(...)}. No sanction is issued until a confirm button is clicked.
 */
@NullMarked
public final class PunishmentGuiFlow {

    /** A permanent mute carries no duration token; the duration form is {@code /tempmute}. */
    private static final String PERMANENT = "";

    private final ModerationServices services;
    private final PlayerPickerView picker;
    private final PunishmentConfirmView confirm;

    public PunishmentGuiFlow(ModerationServices services, PlayerPickerView picker, PunishmentConfirmView confirm) {
        this.services = Objects.requireNonNull(services, "services");
        this.picker = Objects.requireNonNull(picker, "picker");
        this.confirm = Objects.requireNonNull(confirm, "confirm");
    }

    /** Open the picker for {@code action}; a chosen target opens the confirm screen for that sanction. */
    public void open(Player viewer, PlayerRef viewerRef, PunishmentAction action) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(viewerRef, "viewerRef");
        Objects.requireNonNull(action, "action");
        picker.open(viewer, viewerRef, request(viewer, viewerRef, action));
    }

    private PlayerPickerView.Request request(Player viewer, PlayerRef viewerRef, PunishmentAction action) {
        PunishmentAction.Executor executor = executor(action);
        return new PlayerPickerView.Request(
                action.pickerTitle(),
                target -> confirm.open(
                        viewer, viewerRef, target, action, executor, () -> open(viewer, viewerRef, action)),
                name -> services.targets().resolve(name),
                ModerationMessageKey.UNKNOWN_TARGET);
    }

    private PunishmentAction.Executor executor(PunishmentAction action) {
        return switch (action) {
            case BAN -> (actor, target, reason, silent) -> services.ban().ban(actor, target, reason, silent);
            case MUTE ->
                (actor, target, reason, silent) -> services.mute().mute(actor, target, PERMANENT, reason, silent);
        };
    }
}
