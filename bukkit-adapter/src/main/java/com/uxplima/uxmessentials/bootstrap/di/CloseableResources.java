package com.uxplima.uxmessentials.bootstrap.di;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

import org.bukkit.event.Listener;

import com.uxplima.uxmessentials.shared.adapter.inbound.command.CatalogBinding;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.LocaleBinding;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.UsageBinding;
import com.uxplima.uxmessentials.worlds.adapter.outbound.WorldGeneratorResolver;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Everything {@link PluginModule#wire} acquired, closed in reverse on plugin disable.
 *
 * <p>Module stop hooks are pushed in wiring order and run in the reverse of it, so dependents tear
 * down before prerequisites. The command nodes collected here are the already-module-filtered set
 * the plugin's {@code LifecycleEvents.COMMANDS} handler publishes — a disabled module contributes
 * nothing, so its command literal never reaches the dispatcher. Every published command is wrapped by
 * the shared {@link LocaleBinding} so the requesting player's locale is bound at the inbound boundary
 * (docs/13-i18n §5) before any handler resolves a message. The chokepoint runs three binding steps in
 * order: the resolved {@link CatalogBinding} renames, realiases or drops each registration so an operator's
 * {@code commands/*.conf} edits change what gets published; then the {@link UsageBinding} injects a coloured
 * usage executor onto any root that has arguments but no root executor; then the {@link LocaleBinding} binds
 * the locale, picking up the injected executor so the usage line resolves in the player's language.
 */
@NullMarked
public final class CloseableResources implements AutoCloseable {

    private final Deque<Runnable> stopHooks = new ArrayDeque<>();
    private final List<CommandRegistration> commands = new ArrayList<>();
    private final List<Listener> listeners = new ArrayList<>();
    private @Nullable LocaleBinding localeBinding;
    private @Nullable CatalogBinding catalogBinding;
    private @Nullable UsageBinding usageBinding;
    private @Nullable WorldGeneratorResolver worldGeneratorResolver;

    /** Registers a teardown hook (typically a module's {@code stop}); closed in reverse order. */
    public void onClose(Runnable hook) {
        stopHooks.push(Objects.requireNonNull(hook, "hook"));
    }

    /** Adds a command to publish on the next {@code LifecycleEvents.COMMANDS} fire. */
    public void addCommand(CommandRegistration command) {
        commands.add(Objects.requireNonNull(command, "command"));
    }

    /** Adds a Bukkit listener to register on enable; only ever from an enabled module. */
    public void addListener(Listener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /** Sets the boundary {@link LocaleBinding} every published command is wrapped with. */
    public void localeBinding(LocaleBinding binding) {
        this.localeBinding = Objects.requireNonNull(binding, "binding");
    }

    /** Sets the resolved {@link CatalogBinding} applied before the locale wrap to rename/realias/drop. */
    public void catalogBinding(CatalogBinding binding) {
        this.catalogBinding = Objects.requireNonNull(binding, "binding");
    }

    /** Sets the {@link UsageBinding} applied between the catalog and the locale wrap to inject usage executors. */
    public void usageBinding(UsageBinding binding) {
        this.usageBinding = Objects.requireNonNull(binding, "binding");
    }

    /**
     * Captures the worlds context's built-in generator resolver so the plugin's
     * {@code getDefaultWorldGenerator} hook can serve {@code uxmEssentials:void|flat} worlds loaded from
     * server.properties. Set during {@code wireWorlds}; stays null when worlds is disabled (the module never
     * wires), which the hook reads as "fall back to vanilla generation".
     */
    public void worldGeneratorResolver(WorldGeneratorResolver resolver) {
        this.worldGeneratorResolver = Objects.requireNonNull(resolver, "resolver");
    }

    /** The captured worlds generator resolver, or null when worlds is disabled (vanilla fallback). */
    public @Nullable WorldGeneratorResolver worldGeneratorResolver() {
        return worldGeneratorResolver;
    }

    /** The raw, pre-binding registrations, so the catalog can be resolved over the code defaults. */
    List<CommandRegistration> registered() {
        return List.copyOf(commands);
    }

    /**
     * The module-filtered commands to register. The catalog rename/realias/drop is applied first, then a
     * usage executor is injected onto any arg-only root, then each survivor is wrapped to bind the
     * requester's locale.
     */
    public List<CommandRegistration> commands() {
        CatalogBinding catalog = this.catalogBinding;
        List<CommandRegistration> resolved =
                catalog == null ? List.copyOf(commands) : catalog.apply(List.copyOf(commands));
        UsageBinding usage = this.usageBinding;
        if (usage != null) {
            resolved = resolved.stream().map(usage::wrap).toList();
        }
        LocaleBinding binding = this.localeBinding;
        if (binding == null) {
            return resolved;
        }
        return resolved.stream()
                .map(binding::wrap)
                .map(CommandRegistration.class::cast)
                .toList();
    }

    /** The module-filtered listeners to register. */
    public List<Listener> listeners() {
        return List.copyOf(listeners);
    }

    @Override
    public void close() {
        while (!stopHooks.isEmpty()) {
            stopHooks.pop().run();
        }
        commands.clear();
        listeners.clear();
    }
}
