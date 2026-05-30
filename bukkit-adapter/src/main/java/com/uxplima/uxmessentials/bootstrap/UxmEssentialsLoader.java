package com.uxplima.uxmessentials.bootstrap;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;

import org.jspecify.annotations.NullMarked;

/**
 * Runtime classpath hook required by {@code paper-plugin.yml}'s {@code loader:} directive.
 *
 * <p>The body is intentionally empty: the build shades every third-party library with relocation
 * (see docs/04-build.md §16), so there is nothing to resolve at runtime. The class still has to
 * exist because Paper calls {@link #classloader} once at boot.
 */
@NullMarked
public final class UxmEssentialsLoader implements PluginLoader {

    @Override
    public void classloader(PluginClasspathBuilder classpath) {
        // No-op: Shadow bakes the relocated dependencies into the jar at build time.
    }
}
