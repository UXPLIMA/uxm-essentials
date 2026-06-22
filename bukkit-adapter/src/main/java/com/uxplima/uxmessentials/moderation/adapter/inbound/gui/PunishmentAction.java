package com.uxplima.uxmessentials.moderation.adapter.inbound.gui;

import java.util.Optional;

import com.uxplima.uxmessentials.moderation.application.ModerationMessageKey;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The sanction the bare-command GUI flow issues — a {@code /ban} or a {@code /mute} — bundled with the catalog
 * labels its picker title and confirm screen render under, and the use-case call its confirm buttons fire. One
 * enum value drives the whole flow so {@link PlayerPickerView} and {@link PunishmentConfirmView} stay generic
 * over ban vs mute: the picker title, the two confirm-button labels (normal + silent), and the
 * {@link Executor} that performs the audited use-case call are all read from here.
 *
 * <p>Distinct from {@link PunishmentKind} (which drives the management GUI's revoke dispatch and includes
 * {@code JAIL}): this enum is only the two sanctions a bare {@code /ban}/{@code /mute} opens, each carrying its
 * creation-side labels rather than a revoke label.
 */
@NullMarked
public enum PunishmentAction {
    BAN(
            ModerationMessageKey.MOD_GUI_PICK_BAN_TITLE,
            ModerationMessageKey.MOD_GUI_CONFIRM_BAN_TITLE,
            ModerationMessageKey.MOD_GUI_CONFIRM_BAN,
            ModerationMessageKey.MOD_GUI_CONFIRM_BAN_LORE,
            ModerationMessageKey.MOD_GUI_CONFIRM_BAN_SILENT,
            ModerationMessageKey.MOD_GUI_CONFIRM_BAN_SILENT_LORE),
    MUTE(
            ModerationMessageKey.MOD_GUI_PICK_MUTE_TITLE,
            ModerationMessageKey.MOD_GUI_CONFIRM_MUTE_TITLE,
            ModerationMessageKey.MOD_GUI_CONFIRM_MUTE,
            ModerationMessageKey.MOD_GUI_CONFIRM_MUTE_LORE,
            ModerationMessageKey.MOD_GUI_CONFIRM_MUTE_SILENT,
            ModerationMessageKey.MOD_GUI_CONFIRM_MUTE_SILENT_LORE);

    private final ModerationMessageKey pickerTitle;
    private final ModerationMessageKey confirmTitle;
    private final ModerationMessageKey applyLabel;
    private final ModerationMessageKey applyLore;
    private final ModerationMessageKey silentLabel;
    private final ModerationMessageKey silentLore;

    PunishmentAction(
            ModerationMessageKey pickerTitle,
            ModerationMessageKey confirmTitle,
            ModerationMessageKey applyLabel,
            ModerationMessageKey applyLore,
            ModerationMessageKey silentLabel,
            ModerationMessageKey silentLore) {
        this.pickerTitle = pickerTitle;
        this.confirmTitle = confirmTitle;
        this.applyLabel = applyLabel;
        this.applyLore = applyLore;
        this.silentLabel = silentLabel;
        this.silentLore = silentLore;
    }

    /** The player-picker title key for this sanction. */
    public ModerationMessageKey pickerTitle() {
        return pickerTitle;
    }

    /** The confirm-screen title key (rendered with the target name appended outside the tag). */
    public ModerationMessageKey confirmTitle() {
        return confirmTitle;
    }

    /** The normal (broadcast) confirm-button label key. */
    public ModerationMessageKey applyLabel() {
        return applyLabel;
    }

    /** The normal confirm-button lore key. */
    public ModerationMessageKey applyLore() {
        return applyLore;
    }

    /** The silent confirm-button label key. */
    public ModerationMessageKey silentLabel() {
        return silentLabel;
    }

    /** The silent confirm-button lore key. */
    public ModerationMessageKey silentLore() {
        return silentLore;
    }

    /**
     * Performs the audited use-case call for this sanction. The confirm view supplies the actor, the chosen
     * target, the optional reason captured on the confirm screen, and whether the broadcast is suppressed; the
     * caller's implementation routes to {@code services.ban().ban(...)} or {@code services.mute().mute(...)}. It
     * is passed to {@link PunishmentConfirmView#open} rather than held on the enum, since it needs the wired
     * {@code ModerationServices} the enum cannot see.
     */
    @FunctionalInterface
    public interface Executor {

        /** Issue the sanction on {@code target} by {@code actor}, optionally silent, with an optional reason. */
        void execute(PlayerRef actor, PlayerRef target, Optional<String> reason, boolean silent);
    }
}
