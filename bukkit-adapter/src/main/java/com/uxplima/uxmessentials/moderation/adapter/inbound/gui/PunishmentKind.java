package com.uxplima.uxmessentials.moderation.adapter.inbound.gui;

import com.uxplima.uxmessentials.moderation.application.ModerationMessageKey;
import org.jspecify.annotations.NullMarked;

/**
 * The sanction family an {@link ActivePunishment} belongs to, with the catalog label each kind renders under in
 * the management GUI. The kind drives both the list icon's type line and the revoke dispatch: the detail view's
 * confirm-gated revoke routes to {@code unban}, {@code unmute}, or {@code unjail} by this value alone, so the
 * GUI adds no parallel revoke logic over the existing use cases.
 */
@NullMarked
public enum PunishmentKind {
    BAN(ModerationMessageKey.MOD_GUI_KIND_BAN),
    MUTE(ModerationMessageKey.MOD_GUI_KIND_MUTE),
    JAIL(ModerationMessageKey.MOD_GUI_KIND_JAIL);

    private final ModerationMessageKey label;

    PunishmentKind(ModerationMessageKey label) {
        this.label = label;
    }

    /** The catalog key naming this kind in the viewer's locale (the icon's type line and the detail label). */
    public ModerationMessageKey label() {
        return label;
    }
}
