package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Server;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.Nullable;

/**
 * The mcMMO skill and power-level reader behind the {@code mcmmo-level} and {@code mcmmo-power} menu conditions,
 * reached purely by reflection. The SDK is named only by string class-name, so no field or method signature here
 * carries a {@code com.gmail.nossr50} type: constructing this on a server without mcMMO loads none of its
 * classes, and the present-guard short-circuits before any reflection runs.
 *
 * <p>mcMMO has renamed its skill enum twice ({@code SkillType}, then {@code PrimarySkill}, then
 * {@code PrimarySkillType}), so the enum is resolved by trying the three names newest first and the winner is
 * cached. That is the same order every other plugin integrating with mcMMO uses, and it is why the skill argument
 * is matched against the enum by name rather than against a list we would have to keep.
 *
 * <p>Any reflective or unchecked failure is logged exactly once and degrades to an empty answer, which the
 * calling condition then reads as "deny": a gate that cannot be evaluated must not pass.
 */
final class McMmoIntegration {

    private static final String PLUGIN = "mcMMO";
    private static final String USER_MANAGER = "com.gmail.nossr50.util.player.UserManager";
    private static final String MCMMO_PLAYER = "com.gmail.nossr50.datatypes.player.McMMOPlayer";

    /** The skill enum's names across mcMMO's history, newest first. */
    private static final List<String> SKILL_ENUMS = List.of(
            "com.gmail.nossr50.datatypes.skills.PrimarySkillType",
            "com.gmail.nossr50.datatypes.skills.PrimarySkill",
            "com.gmail.nossr50.datatypes.skills.SkillType");

    private final Server server;
    private final Logger log;
    private final AtomicBoolean warned = new AtomicBoolean();

    private @Nullable Class<?> skillEnum;

    McMmoIntegration(Server server, Logger log) {
        this.server = Objects.requireNonNull(server, "server");
        this.log = Objects.requireNonNull(log, "log");
    }

    /** The viewer's level in {@code skill}, or empty when mcMMO is absent, the skill is unknown, or a lookup fails. */
    OptionalInt skillLevel(Player player, String skill) {
        if (skill.isBlank() || !present()) {
            return OptionalInt.empty();
        }
        try {
            Class<?> skills = skillEnum();
            Object type = skills.getMethod("valueOf", String.class)
                    .invoke(null, skill.strip().toUpperCase(Locale.ROOT));
            Object profile = profile(player);
            if (type == null || profile == null) {
                return OptionalInt.empty();
            }
            Object level = Class.forName(MCMMO_PLAYER)
                    .getMethod("getSkillLevel", skills)
                    .invoke(profile, type);
            return level instanceof Number number ? OptionalInt.of(number.intValue()) : OptionalInt.empty();
        } catch (ReflectiveOperationException | RuntimeException failure) {
            // An unknown skill name arrives here as an IllegalArgumentException out of valueOf, which is an
            // authoring mistake rather than a broken integration, so it degrades exactly like the rest.
            degrade(failure);
            return OptionalInt.empty();
        }
    }

    /** The viewer's mcMMO power level (the sum of every skill), or empty when mcMMO is absent or a lookup fails. */
    OptionalInt powerLevel(Player player) {
        if (!present()) {
            return OptionalInt.empty();
        }
        try {
            Object profile = profile(player);
            if (profile == null) {
                return OptionalInt.empty();
            }
            Object level =
                    Class.forName(MCMMO_PLAYER).getMethod("getPowerLevel").invoke(profile);
            return level instanceof Number number ? OptionalInt.of(number.intValue()) : OptionalInt.empty();
        } catch (ReflectiveOperationException | RuntimeException failure) {
            degrade(failure);
            return OptionalInt.empty();
        }
    }

    private boolean present() {
        return server.getPluginManager().isPluginEnabled(PLUGIN);
    }

    /**
     * The player's mcMMO profile, or {@code null} when mcMMO has not loaded one yet. That happens for a player
     * whose data is still loading and for one mcMMO deliberately excludes, so it is an ordinary absent answer
     * rather than a failure.
     */
    private @Nullable Object profile(Player player) throws ReflectiveOperationException {
        return Class.forName(USER_MANAGER).getMethod("getPlayer", Player.class).invoke(null, player);
    }

    private Class<?> skillEnum() throws ClassNotFoundException {
        Class<?> cached = skillEnum;
        if (cached != null) {
            return cached;
        }
        for (String candidate : SKILL_ENUMS) {
            try {
                Class<?> resolved = Class.forName(candidate);
                skillEnum = resolved;
                return resolved;
            } catch (ClassNotFoundException olderName) {
                // Expected for every name but the one this mcMMO build uses.
            }
        }
        throw new ClassNotFoundException("no mcMMO skill enum among " + SKILL_ENUMS);
    }

    private void degrade(Exception failure) {
        if (warned.compareAndSet(false, true)) {
            log.warn("event=menu_integration_reflection_failed integration={} reason={}", PLUGIN, failure.toString());
        }
    }
}
