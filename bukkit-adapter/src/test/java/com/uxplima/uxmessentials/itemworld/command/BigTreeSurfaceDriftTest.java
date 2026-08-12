package com.uxplima.uxmessentials.itemworld.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.bootstrap.di.DefaultModuleRegistry;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import org.junit.jupiter.api.Test;

/**
 * Pins {@code /bigtree} into the itemworld context's command surface, the way
 * {@code VerticalSurfaceDriftTest} pins the vertical teleport family. {@code /bigtree} grows the
 * large variant of a tree and is the admin-fun sibling of {@code /tree}; both share
 * one {@code tree} permission, so this guard fails if {@code /bigtree} ever drops out of the surface
 * or wires under a permission node other than the {@code uxmessentials.tree.use} node {@code /tree}
 * already uses, which would otherwise drift the permissions reference and the permission catalogue.
 */
class BigTreeSurfaceDriftTest {

    private static CommandSpec itemworldSpec(String literal) {
        FeatureModule itemworld = new DefaultModuleRegistry()
                .byId(ModuleId.of("itemworld"))
                .orElseThrow(() -> new AssertionError("itemworld module must be registered"));
        return itemworld.commands().stream()
                .filter(spec -> spec.literal().equals(literal))
                .findFirst()
                .orElseThrow(() -> new AssertionError("itemworld surface must expose a /" + literal + " command"));
    }

    @Test
    void itemworldSurfaceExposesBigTree() {
        assertThat(itemworldSpec("bigtree").literal()).isEqualTo("bigtree");
    }

    @Test
    void bigTreeReusesTheSharedTreePermission() {
        assertThat(itemworldSpec("bigtree").permission()).isEqualTo("uxmessentials.tree.use");
    }

    @Test
    void bigTreeShareIsTheSamePermissionAsTree() {
        assertThat(itemworldSpec("bigtree").permission())
                .isEqualTo(itemworldSpec("tree").permission());
    }
}
