package com.uxplima.uxmessentials.staff.adapter.inbound.listener;

import java.util.Map;
import java.util.Objects;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.staff.adapter.StaffGadget;
import com.uxplima.uxmessentials.staff.adapter.inbound.gui.StaffExamineView;
import com.uxplima.uxmessentials.staff.adapter.inbound.gui.StaffNavigatorView;
import com.uxplima.uxmessentials.staff.adapter.outbound.StaffFollowService;
import com.uxplima.uxmessentials.staff.application.StaffMessageKey;
import com.uxplima.uxmessentials.staff.application.StaffNotifier;
import com.uxplima.uxmessentials.staff.application.port.StaffFreeze;
import com.uxplima.uxmessentials.staff.application.port.StaffTeleport;
import com.uxplima.uxmessentials.staff.application.port.StaffVanish;
import org.jspecify.annotations.NullMarked;

/**
 * The gadget action table, split out of {@link StaffModeListener} so the listener stays focused on the Bukkit
 * event plumbing (resolve gadget, cancel, route) and this owns what each gadget does. Two entry points:
 * {@link #onAir} for an interact that hit no player (VANISH/EXAMINE/COMPASS act, FREEZE/FOLLOW report "look at a
 * player"), and {@link #onPlayer} for a right-click landing on a player entity (FREEZE/FOLLOW/COMPASS act,
 * VANISH/EXAMINE fall back to their air behaviour). Each soft-coupled port degrades on its {@code NONE} binding.
 *
 * <p>The FREEZE gadget delegates straight to the moderation freeze use case and shows no line of its own — that
 * use case already confirms to the actor (and tells an exempt target it cannot be frozen), exactly as
 * {@code /freeze} does, so the gadget would otherwise double-notify.
 */
@NullMarked
public final class StaffGadgetActions {

    private final StaffVanish vanish;
    private final StaffFreeze freeze;
    private final StaffTeleport teleport;
    private final StaffFollowService follow;
    private final StaffExamineView examineView;
    private final StaffNavigatorView navigatorView;
    private final StaffNotifier notifier;

    public StaffGadgetActions(
            StaffVanish vanish,
            StaffFreeze freeze,
            StaffTeleport teleport,
            StaffFollowService follow,
            StaffExamineView examineView,
            StaffNavigatorView navigatorView,
            StaffNotifier notifier) {
        this.vanish = Objects.requireNonNull(vanish, "vanish");
        this.freeze = Objects.requireNonNull(freeze, "freeze");
        this.teleport = Objects.requireNonNull(teleport, "teleport");
        this.follow = Objects.requireNonNull(follow, "follow");
        this.examineView = Objects.requireNonNull(examineView, "examineView");
        this.navigatorView = Objects.requireNonNull(navigatorView, "navigatorView");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** A gadget used while not looking at a player. */
    void onAir(StaffGadget gadget, Player player, PlayerRef who) {
        switch (gadget) {
            case VANISH -> vanish.setVanished(who, !player.isInvisible());
            case EXAMINE -> examineView.open(player, who);
            case COMPASS -> navigatorView.open(player, who);
            case FREEZE, FOLLOW -> notifier.send(who, StaffMessageKey.STAFF_GADGET_NO_TARGET);
        }
    }

    /** A gadget right-clicked directly onto {@code targetPlayer}. */
    void onPlayer(StaffGadget gadget, Player actor, PlayerRef who, Player targetPlayer, PlayerRef targetRef) {
        switch (gadget) {
            case FREEZE -> freeze.toggle(who, targetRef);
            case FOLLOW -> follow(actor, who, targetPlayer);
            case COMPASS -> teleport(who, targetPlayer, targetRef);
            case VANISH -> vanish.setVanished(who, !actor.isInvisible());
            case EXAMINE -> examineView.open(actor, who);
        }
    }

    private void follow(Player actor, PlayerRef who, Player targetPlayer) {
        if (actor.getUniqueId().equals(targetPlayer.getUniqueId())) {
            return; // a staff member cannot follow themselves; start nothing and say nothing
        }
        boolean started = follow.toggle(actor, targetPlayer);
        StaffMessageKey key = started ? StaffMessageKey.STAFF_FOLLOW_ON : StaffMessageKey.STAFF_FOLLOW_OFF;
        notifier.send(who, key, Map.of("target", targetPlayer.getName()));
    }

    private void teleport(PlayerRef who, Player targetPlayer, PlayerRef targetRef) {
        boolean ok = teleport.teleportTo(who, targetRef);
        StaffMessageKey key = ok ? StaffMessageKey.STAFF_TELEPORTED : StaffMessageKey.STAFF_TELEPORT_FAILED;
        notifier.send(who, key, Map.of("target", targetPlayer.getName()));
    }
}
