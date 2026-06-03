package com.uxplima.uxmessentials.teleport.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.bootstrap.di.DefaultModuleRegistry;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import org.junit.jupiter.api.Test;

/**
 * Pins {@code /setmainspawn}, {@code /removespawn} and {@code /mirrorspawn} into the teleport context's
 * command surface. Operator spawns were session-only with a single per-world default; these write the global
 * main spawn, clear a world's own spawn, and redirect a world's spawn to another over the DB-backed spawn
 * directory. This guard fails if any literal drops out of the surface or wires under a node other than the
 * shared {@code uxmessentials.spawn.set} (the same node {@code /setspawn} uses).
 */
class MultiSpawnSurfaceDriftTest {

    private static CommandSpec teleportSpec(String literal) {
        FeatureModule teleport = new DefaultModuleRegistry()
                .byId(ModuleId.of("teleport"))
                .orElseThrow(() -> new AssertionError("teleport module must be registered"));
        return teleport.commands().stream()
                .filter(spec -> spec.literal().equals(literal))
                .findFirst()
                .orElseThrow(() -> new AssertionError("teleport surface must expose a /" + literal + " command"));
    }

    @Test
    void teleportSurfaceExposesTheThreeMultiSpawnCommands() {
        assertThat(teleportSpec("setmainspawn").literal()).isEqualTo("setmainspawn");
        assertThat(teleportSpec("removespawn").literal()).isEqualTo("removespawn");
        assertThat(teleportSpec("mirrorspawn").literal()).isEqualTo("mirrorspawn");
    }

    @Test
    void allThreeShareTheSpawnSetNode() {
        assertThat(teleportSpec("setmainspawn").permission()).isEqualTo("uxmessentials.spawn.set");
        assertThat(teleportSpec("removespawn").permission()).isEqualTo("uxmessentials.spawn.set");
        assertThat(teleportSpec("mirrorspawn").permission()).isEqualTo("uxmessentials.spawn.set");
    }
}
