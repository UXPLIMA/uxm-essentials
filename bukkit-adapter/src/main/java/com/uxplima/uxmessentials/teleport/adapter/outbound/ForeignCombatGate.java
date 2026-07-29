package com.uxplima.uxmessentials.teleport.adapter.outbound;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;

import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.teleport.application.port.CombatGate;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The {@link CombatGate} implementation, reading the combat tag an installed combat plugin already keeps.
 * CombatLogX and PvPManager are both supported and both reached <b>entirely by reflection</b>: neither is a
 * {@code compileOnly} dependency, no {@code com.github.sirblobman} or {@code me.chancesd} type is named here,
 * and a server with neither plugin loads none of their classes.
 *
 * <p>A player is tagged when <em>any</em> installed reader says so. Two combat plugins on one server is a
 * misconfiguration rather than a design, but if it happens the safe reading is the strict one: still in a
 * fight according to either timer means still in a fight.
 *
 * <p><b>Failure direction.</b> Every other integration in this codebase fails closed; this one fails open. A
 * lookup that throws answers "not tagged", so a broken or incompatible combat plugin lets teleports through
 * rather than trapping every player on the server with no way out and no way to tell why. The tag is a
 * fairness rule, not a security boundary, and the cost of the two failure directions is not symmetric.
 */
@NullMarked
public final class ForeignCombatGate implements CombatGate {

    private static final String COMBATLOGX = "CombatLogX";
    private static final String PVPMANAGER = "PvPManager";
    private static final String COMBAT_PLAYER_CLASS = "me.chancesd.pvpmanager.player.CombatPlayer";

    private final Server server;
    private final Logger log;

    private @Nullable Method combatLogXManager;
    private @Nullable Method combatLogXInCombat;
    private @Nullable Method pvpManagerGet;
    private @Nullable Method pvpManagerInCombat;
    private boolean unavailableLogged;

    public ForeignCombatGate(Server server, Logger log) {
        this.server = Objects.requireNonNull(server, "server");
        this.log = Objects.requireNonNull(log, "log");
    }

    /** The combat plugins this gate can read, so the wiring can decide whether binding it is worth anything. */
    public static List<String> supportedPlugins() {
        return List.of(COMBATLOGX, PVPMANAGER);
    }

    /** True when any supported combat plugin is installed and enabled. */
    public static boolean anyPresent(Server server) {
        Objects.requireNonNull(server, "server");
        return supportedPlugins().stream().anyMatch(name -> present(server, name));
    }

    @Override
    public boolean isTagged(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        Player player = server.getPlayer(who.uuid());
        if (player == null) {
            // An offline player is in no fight, and neither plugin can answer for one.
            return false;
        }
        return taggedByCombatLogX(player) || taggedByPvpManager(player);
    }

    private boolean taggedByCombatLogX(Player player) {
        Plugin plugin = server.getPluginManager().getPlugin(COMBATLOGX);
        if (plugin == null || !plugin.isEnabled()) {
            return false;
        }
        try {
            if (combatLogXManager == null) {
                combatLogXManager = plugin.getClass().getMethod("getCombatManager");
            }
            Object manager = combatLogXManager.invoke(plugin);
            if (manager == null) {
                return false;
            }
            if (combatLogXInCombat == null) {
                combatLogXInCombat = manager.getClass().getMethod("isInCombat", Player.class);
            }
            return Boolean.TRUE.equals(combatLogXInCombat.invoke(manager, player));
        } catch (Throwable t) {
            return degrade(COMBATLOGX, t);
        }
    }

    private boolean taggedByPvpManager(Player player) {
        if (!present(server, PVPMANAGER)) {
            return false;
        }
        try {
            if (pvpManagerGet == null) {
                pvpManagerGet = Class.forName(COMBAT_PLAYER_CLASS).getMethod("get", Player.class);
            }
            Object combatPlayer = pvpManagerGet.invoke(null, player);
            if (combatPlayer == null) {
                return false;
            }
            if (pvpManagerInCombat == null) {
                pvpManagerInCombat = combatPlayer.getClass().getMethod("isInCombat");
            }
            return Boolean.TRUE.equals(pvpManagerInCombat.invoke(combatPlayer));
        } catch (Throwable t) {
            return degrade(PVPMANAGER, t);
        }
    }

    private static boolean present(Server server, String pluginName) {
        Plugin plugin = server.getPluginManager().getPlugin(pluginName);
        return plugin != null && plugin.isEnabled();
    }

    /**
     * One warning per gate for an unreachable or incompatible combat plugin, then silence: this runs on every
     * teleport attempt, so a repeated failure must not turn the console into a log of every {@code /home}.
     */
    private boolean degrade(String provider, Throwable cause) {
        if (!unavailableLogged) {
            unavailableLogged = true;
            log.warn("event=combat_tag_lookup_failed provider={} reason={}", provider, cause);
        }
        return false;
    }
}
