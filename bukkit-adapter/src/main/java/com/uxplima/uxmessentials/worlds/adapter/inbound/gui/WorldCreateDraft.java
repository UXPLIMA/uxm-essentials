package com.uxplima.uxmessentials.worlds.adapter.inbound.gui;

import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.worlds.domain.BuiltInGenerators;
import com.uxplima.uxmessentials.worlds.domain.GeneratorRef;
import com.uxplima.uxmessentials.worlds.domain.WorldCreateOptions;
import com.uxplima.uxmessentials.worlds.domain.WorldDefaults;
import com.uxplima.uxmessentials.worlds.domain.WorldEnvironment;
import com.uxplima.uxmessentials.worlds.domain.WorldGenType;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The in-flight configuration the {@link WorldCreateMenu} create screen carries while a staff member builds a new
 * world: the chosen name (empty until typed), environment, generation type, optional built-in generator, and optional
 * seed. It is an immutable value the create screen re-renders from as the menu subject; each selector cycle or input
 * submission produces a fresh draft the screen re-opens with. A new draft starts from the configured
 * {@link WorldDefaults}, so the screen shows the environment and type a world gets when nobody changes them, and the
 * create use case fills everything the screen does not offer from the same defaults as {@code /worlds create}.
 *
 * <p>The {@code generator} field holds a bare built-in id ({@code void}/{@code flat}) or {@code null} for vanilla
 * generation. Only the two built-ins are offered in the GUI, the same set {@code WorldGenerationMenu} reports and the
 * {@code /worlds create} generator tab-completion suggests. {@link #toOptions()} maps a built-in id onto the
 * namespaced {@link BuiltInGenerators} ref the engine resolves.
 */
@NullMarked
public record WorldCreateDraft(
        String name,
        WorldEnvironment environment,
        WorldGenType worldType,
        @Nullable String generator,
        Optional<Long> seed) {

    public WorldCreateDraft {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(worldType, "worldType");
        Objects.requireNonNull(seed, "seed");
    }

    /** A fresh draft with no name, the configured environment and type, no generator, and no seed. */
    public static WorldCreateDraft from(WorldDefaults defaults) {
        Objects.requireNonNull(defaults, "defaults");
        return new WorldCreateDraft("", defaults.environment(), defaults.worldType(), null, Optional.empty());
    }

    public WorldCreateDraft withName(String value) {
        return new WorldCreateDraft(value, environment, worldType, generator, seed);
    }

    public WorldCreateDraft withEnvironment(WorldEnvironment value) {
        return new WorldCreateDraft(name, value, worldType, generator, seed);
    }

    public WorldCreateDraft withWorldType(WorldGenType value) {
        return new WorldCreateDraft(name, environment, value, generator, seed);
    }

    public WorldCreateDraft withGenerator(@Nullable String value) {
        return new WorldCreateDraft(name, environment, worldType, value, seed);
    }

    public WorldCreateDraft withSeed(Optional<Long> value) {
        return new WorldCreateDraft(name, environment, worldType, generator, value);
    }

    /** The chosen built-in generator id, or empty for vanilla generation. */
    public Optional<String> generatorId() {
        return Optional.ofNullable(generator);
    }

    /** Whether a non-blank name has been entered, the create button only acts once a name is present. */
    public boolean hasName() {
        return !name.isBlank();
    }

    /**
     * What the screen chose: the environment and type it shows, the seed if one was entered, and the namespaced
     * built-in generator ref (or none for vanilla).
     */
    public WorldCreateOptions toOptions() {
        Optional<GeneratorRef> generatorRef = generatorId().map(BuiltInGenerators::ref);
        return new WorldCreateOptions(Optional.of(environment), Optional.of(worldType), seed, generatorRef);
    }
}
