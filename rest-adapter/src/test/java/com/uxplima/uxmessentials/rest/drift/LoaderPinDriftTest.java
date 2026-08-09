package com.uxplima.uxmessentials.rest.drift;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.uxplima.uxmessentials.loader.LoaderDependencies;
import com.uxplima.uxmessentials.rest.UxmRestLoader;
import org.junit.jupiter.api.Test;

/**
 * The add-on's runtime libraries are pinned to the same versions everything else in the build uses.
 *
 * <p>{@link UxmRestLoader} writes its coordinates out rather than reading them from anywhere, because a
 * {@code PluginLoader} runs before any plugin classloader exists and has nothing to read them from. That leaves a
 * second copy of a version number, which is the sort of thing that agrees on the day it is written and disagrees a
 * year later, when the symptom is a {@code NoSuchMethodError} in whichever jar lost.
 *
 * <p>So both copies are checked against their source of truth: configurate against the pin every loader shares, and
 * gson against the version catalogue the rest of the build compiles with.
 */
class LoaderPinDriftTest {

    private static final Pattern CATALOG_VERSION = Pattern.compile("(?m)^\\s*gson\\s*=\\s*\"([^\"]+)\"");

    @Test
    void configurateIsPinnedToTheVersionEveryOtherLoaderResolves() {
        assertThat(UxmRestLoader.CONFIGURATE_HOCON).isEqualTo(LoaderDependencies.configurateHocon());
    }

    @Test
    void gsonIsPinnedToTheVersionTheBuildCompilesAgainst() throws IOException {
        assertThat(UxmRestLoader.GSON).isEqualTo("com.google.code.gson:gson:" + catalogGsonVersion());
    }

    /** The {@code gson} version from {@code gradle/libs.versions.toml}, which is the build's source of truth. */
    private static String catalogGsonVersion() throws IOException {
        Path catalog = repositoryRoot().resolve("gradle").resolve("libs.versions.toml");
        Matcher matcher = CATALOG_VERSION.matcher(Files.readString(catalog));
        if (!matcher.find()) {
            throw new IllegalStateException("no gson version in " + catalog);
        }
        return matcher.group(1);
    }

    private static Path repositoryRoot() {
        Path here = Path.of("").toAbsolutePath();
        while (!Files.exists(here.resolve("settings.gradle.kts"))) {
            Path parent = here.getParent();
            if (parent == null) {
                throw new IllegalStateException(
                        "no settings.gradle.kts above " + Path.of("").toAbsolutePath());
            }
            here = parent;
        }
        return here;
    }
}
