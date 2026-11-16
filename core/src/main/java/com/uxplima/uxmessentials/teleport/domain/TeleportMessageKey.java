package com.uxplima.uxmessentials.teleport.domain;

import com.uxplima.uxmessentials.shared.domain.message.MessageKey;

/**
 * The teleport context's user-visible message keys. Each constant maps 1:1 to a kebab-case catalog key
 * in {@code messages_<lang>.conf} ({@code TPA_SENT} ↔ {@code teleport.tpa.sent}); the constant is the
 * compile-time handle, the catalog holds the text. There are no inline player-facing literals anywhere
 * in the context: every message resolves through one of these.
 *
 * <p>Per the i18n contract, a disabled module still ships its keys so the catalog stays whole and the
 * locale-parity guard sees the full {@code en} key set.
 */
public enum TeleportMessageKey implements MessageKey {

    // tpa lifecycle
    TPA_SENT("teleport.tpa.sent"),
    TPA_RECEIVED("teleport.tpa.received"),
    TPA_HERE_RECEIVED("teleport.tpa.here-received"),
    TPA_ACCEPTED("teleport.tpa.accepted"),
    TPA_DENIED("teleport.tpa.denied"),
    TPA_CANCELLED("teleport.tpa.cancelled"),
    TPA_EXPIRED("teleport.tpa.expired"),
    TPA_SELF("teleport.tpa.self"),
    TPA_TARGET_OFFLINE("teleport.tpa.target-offline"),
    TPA_TOGGLED_OFF("teleport.tpa.toggled-off"),
    // the /tpahere requester, when the player who accepted may not be moved (jailed or in combat)
    TPA_MOVER_REFUSED("teleport.tpa.mover-refused"),
    TPA_BLOCKED("teleport.tpa.blocked"),
    TPA_NONE_PENDING("teleport.tpa.none-pending"),
    TPA_LIST_HEADER("teleport.tpa.list-header"),
    TPA_LIST_ENTRY("teleport.tpa.list-entry"),
    TPA_TOGGLE_ON("teleport.tpa.toggle-on"),
    TPA_TOGGLE_OFF("teleport.tpa.toggle-off"),
    // a target on another backend denied the request; the requester is told, since they cannot see it happen
    TPA_DENIED_REMOTE("teleport.tpa.denied-remote"),

    // a teleport to a player on another backend: the hop notices, shared by accepted requests and staff teleports
    CROSS_SERVER_SENDING("teleport.cross-server.sending"),
    CROSS_SERVER_ARRIVED("teleport.cross-server.arrived"),
    CROSS_SERVER_TIMED_OUT("teleport.cross-server.timed-out"),
    CROSS_SERVER_MISROUTED("teleport.cross-server.misrouted"),
    CROSS_SERVER_FAILED("teleport.cross-server.failed"),
    CROSS_SERVER_UNAVAILABLE("teleport.cross-server.unavailable"),
    CROSS_SERVER_REFUNDED("teleport.cross-server.refunded"),

    // /tpauto: auto-accept incoming requests
    TPAUTO_ON("teleport.tpauto-on"),
    TPAUTO_OFF("teleport.tpauto-off"),

    // back, deathback
    BACK_RETURNED("teleport.back.returned"),
    BACK_NONE("teleport.back.none"),
    BACK_DEATH_DENIED("teleport.back.death-denied"),
    BACK_DEATH_DELAY("teleport.back.death-delay"),
    DEATHBACK_NONE("teleport.deathback.none"),

    // rtp
    RTP_SEARCHING("teleport.rtp.searching"),
    RTP_DISALLOWED("teleport.rtp.disallowed"),
    RTP_NO_LOCATION("teleport.rtp.no-location"),
    RTP_CANT_AFFORD("teleport.rtp.cant-afford"),
    RTP_CHARGE_FAILED("teleport.rtp.charge-failed"),
    RTP_REFUNDED("teleport.rtp.refunded"),
    RTP_REFUND_FAILED("teleport.rtp.refund-failed"),
    RTP_BIOME_UNKNOWN("teleport.rtp.biome-unknown"),
    RTP_BIOME_NOT_FOUND("teleport.rtp.biome-not-found"),
    // /rtp <player> and /rtp <world>. The argument names neither an online player nor a loaded world
    RTP_UNKNOWN_TARGET("teleport.rtp.unknown-target"),

    // /rtp gui, the menu-engine world picker
    RTP_GUI_TITLE("teleport.rtp.gui.title"),
    RTP_GUI_WORLD_NAME("teleport.rtp.gui.world-name"),
    RTP_GUI_WORLD_LORE("teleport.rtp.gui.world-lore"),
    RTP_GUI_BIOME_HINT("teleport.rtp.gui.biome-hint"),
    RTP_GUI_BIOME_HINT_LORE("teleport.rtp.gui.biome-hint-lore"),
    RTP_GUI_PREV("teleport.rtp.gui.prev"),
    RTP_GUI_NEXT("teleport.rtp.gui.next"),

    // /settpr, set the rtp zone at runtime
    SETTPR_SET("teleport.settpr.set"),
    SETTPR_INVALID("teleport.settpr.invalid"),

    // spawn
    SPAWN_TELEPORTED("teleport.spawn.teleported"),
    SPAWN_SET("teleport.spawn.set"),
    SPAWN_UNRESOLVED("teleport.spawn.unresolved"),
    SPAWN_MAIN_SET("teleport.spawn.main-set"),
    SPAWN_MAIN_SET_SCOPED("teleport.spawn.main-set-scoped"),
    SPAWN_SCOPE_INVALID("teleport.spawn.scope-invalid"),
    SPAWN_REMOVED("teleport.spawn.removed"),
    SPAWN_REMOVE_NONE("teleport.spawn.remove-none"),
    SPAWN_MIRRORED("teleport.spawn.mirrored"),
    SPAWN_MIRROR_UNKNOWN_WORLD("teleport.spawn.mirror-unknown-world"),
    SPAWN_MIRROR_SELF("teleport.spawn.mirror-self"),

    // arrival HUD. The short title flashed on the screen when a teleport lands
    ARRIVED_TITLE("teleport.arrived.title"),

    // admin / positional
    TP_DONE("teleport.tp.done"),
    // staff /tp of a player on another backend, as the staff member reads it
    TP_CROSS_SERVER_SENT("teleport.tp.cross-server.sent"),
    TP_CROSS_SERVER_REFUSED("teleport.tp.cross-server.refused"),
    TP_CROSS_SERVER_NO_ANSWER("teleport.tp.cross-server.no-answer"),
    TP_NO_TARGET_BLOCK("teleport.tp.no-target-block"),
    // a staff /tp refused by the target's /tptoggle; /tpo overrides it
    TP_TOGGLED_OFF("teleport.tp.toggled-off"),
    // /tpoffline reads the logout position this server saved; /tpofflinehere stores a move for the next join
    TPOFFLINE_ONLINE("teleport.tpoffline.online"),
    TPOFFLINE_UNKNOWN("teleport.tpoffline.unknown"),
    TPOFFLINE_WORLD_MISSING("teleport.tpoffline.world-missing"),
    TPOFFLINE_MOVED("teleport.tpoffline.moved"),
    TPOFFLINE_FAILED("teleport.tpoffline.failed"),
    TPOFFLINE_AIRBORNE("teleport.tpoffline.airborne"),
    TPRANDOM_NONE("teleport.tprandom.none"),

    // a jailed player may not self-teleport. The moderation context's JailGate denies it here
    JAILED("teleport.jailed"),

    // an installed combat plugin has the mover tagged; CombatGate denies the self-teleport here
    COMBAT_TAGGED("teleport.combat-tagged"),

    // shared cooldown / warmup feedback owned by the teleport tiers
    COOLDOWN_ACTIVE("teleport.cooldown.active"),
    WARMUP_STARTED("teleport.warmup.started"),
    WARMUP_CANCELLED("teleport.warmup.cancelled"),
    WARMUP_REPLACED("teleport.warmup.replaced"),

    // /tpsettings. The per-player teleport settings panel
    GUI_SETTINGS_TITLE("teleport.gui.settings.title"),
    GUI_SETTINGS_VALUE_LORE("teleport.gui.settings.value-lore"),
    GUI_SETTINGS_BACK("teleport.gui.settings.back"),
    GUI_SETTINGS_ACCEPT("teleport.gui.settings.accept"),
    GUI_SETTINGS_AUTO_ACCEPT("teleport.gui.settings.auto-accept"),
    GUI_SETTINGS_VALUE_ON("teleport.gui.settings.value-on"),
    GUI_SETTINGS_VALUE_OFF("teleport.gui.settings.value-off");

    private final String key;

    TeleportMessageKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}
