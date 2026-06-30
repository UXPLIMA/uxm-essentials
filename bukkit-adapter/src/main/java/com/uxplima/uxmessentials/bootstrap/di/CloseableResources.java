package com.uxplima.uxmessentials.bootstrap.di;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

import org.bukkit.event.Listener;

import com.uxplima.uxmessentials.shared.adapter.inbound.command.CatalogBinding;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.GuiRootBinding;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.LocaleBinding;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.UsageBinding;
import com.uxplima.uxmessentials.shared.adapter.outbound.action.ServerConnector;
import com.uxplima.uxmessentials.shared.adapter.outbound.currency.Currencies;
import com.uxplima.uxmessentials.shared.adapter.outbound.hooks.Hooks;
import com.uxplima.uxmessentials.shared.application.port.PlayerDataStore;
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
 * (docs/13-i18n §5) before any handler resolves a message. The chokepoint runs the binding steps in
 * order: the resolved {@link CatalogBinding} renames, realiases or drops each registration so an operator's
 * {@code commands/*.conf} edits change what gets published; then the {@link GuiRootBinding} installs a
 * bare-input GUI opener on any command whose {@code gui} flag is on; then the {@link UsageBinding} injects a
 * coloured usage executor onto any root that still has arguments but no root executor; then the
 * {@link LocaleBinding} binds the locale, picking up the installed executor so the line resolves in the
 * player's language.
 */
@NullMarked
public final class CloseableResources implements AutoCloseable {

    private final Deque<Runnable> stopHooks = new ArrayDeque<>();
    private final List<CommandRegistration> commands = new ArrayList<>();
    private final List<Listener> listeners = new ArrayList<>();
    private @Nullable LocaleBinding localeBinding;
    private @Nullable CatalogBinding catalogBinding;
    private @Nullable GuiRootBinding guiRootBinding;
    private @Nullable UsageBinding usageBinding;
    private @Nullable WorldGeneratorResolver worldGeneratorResolver;
    private @Nullable Hooks hooks;
    private @Nullable Currencies currencies;
    private @Nullable PlayerDataStore playerData;
    private @Nullable ServerConnector serverConnector;

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

    /** Sets the {@link GuiRootBinding} applied after the catalog to install bare-input GUI openers. */
    public void guiRootBinding(GuiRootBinding binding) {
        this.guiRootBinding = Objects.requireNonNull(binding, "binding");
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

    /**
     * Captures the resolved optional-plugin hook façade so later integration features (Vault economy and
     * permission, the multi-currency providers, HeadDatabase, the item providers) read their capability from
     * one place. Resolved once at wiring, when plugin presence is settled for the run.
     */
    public void hooks(Hooks resolved) {
        this.hooks = Objects.requireNonNull(resolved, "resolved");
    }

    /** The resolved optional-plugin hooks, or null before wiring has constructed them. */
    public @Nullable Hooks hooks() {
        return hooks;
    }

    /**
     * Captures the multi-currency façade so the Phase-2 economy actions and Phase-3 requirements read every
     * back-end (Vault, Exp, PlayerPoints, CoinsEngine, zEssentials) from one place. Built once at wiring, beside
     * the hooks it depends on.
     */
    public void currencies(Currencies resolved) {
        this.currencies = Objects.requireNonNull(resolved, "resolved");
    }

    /** The multi-currency façade, or null before wiring has constructed it. */
    public @Nullable Currencies currencies() {
        return currencies;
    }

    /**
     * Captures the persistent player-data store so the Phase-2 data actions (SET/ADD/SUB/MUL/DIV) and the Phase-6
     * {@code %..._value_<key>%} placeholders read and write per-player data from one place. Built once at wiring,
     * beside the cross-cutting menu substrate; its join/quit cache lifecycle is registered as a normal listener.
     */
    public void playerData(PlayerDataStore resolved) {
        this.playerData = Objects.requireNonNull(resolved, "resolved");
    }

    /** The persistent player-data store, or null before wiring has constructed it. */
    public @Nullable PlayerDataStore playerData() {
        return playerData;
    }

    /**
     * Captures the proxy {@link ServerConnector} so the Phase-2 {@code [connect]} movement action can move a
     * viewer to another backend through one shared BungeeCord/Velocity channel. Built once at wiring beside the
     * other menu substrate; with no proxy in front the underlying frame is harmlessly discarded, so a
     * single-server install reads this as a logged no-op rather than an error.
     */
    public void serverConnector(ServerConnector resolved) {
        this.serverConnector = Objects.requireNonNull(resolved, "resolved");
    }

    /** The proxy server connector, or null before wiring has constructed it. */
    public @Nullable ServerConnector serverConnector() {
        return serverConnector;
    }

    /** The raw, pre-binding registrations, so the catalog can be resolved over the code defaults. */
    List<CommandRegistration> registered() {
        return List.copyOf(commands);
    }

    /**
     * The module-filtered commands to register. The catalog rename/realias/drop is applied first, then a
     * bare-input GUI opener is installed on any command whose {@code gui} flag is on, then a usage executor
     * is injected onto any arg-only root still without one, then each survivor is wrapped to bind the
     * requester's locale.
     */
    public List<CommandRegistration> commands() {
        CatalogBinding catalog = this.catalogBinding;
        List<CommandRegistration> resolved =
                catalog == null ? List.copyOf(commands) : catalog.apply(List.copyOf(commands));
        GuiRootBinding guiRoot = this.guiRootBinding;
        if (guiRoot != null) {
            resolved = resolved.stream().map(guiRoot::wrap).toList();
        }
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
