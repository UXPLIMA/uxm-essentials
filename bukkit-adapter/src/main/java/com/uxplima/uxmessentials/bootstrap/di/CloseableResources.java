package com.uxplima.uxmessentials.bootstrap.di;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

import org.bukkit.event.Listener;

import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.LocaleBinding;
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
 * (docs/13-i18n §5) before any handler resolves a message.
 */
@NullMarked
public final class CloseableResources implements AutoCloseable {

    private final Deque<Runnable> stopHooks = new ArrayDeque<>();
    private final List<CommandRegistration> commands = new ArrayList<>();
    private final List<Listener> listeners = new ArrayList<>();
    private @Nullable LocaleBinding localeBinding;

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

    /** The module-filtered commands to register, each wrapped to bind the requester's locale. */
    public List<CommandRegistration> commands() {
        LocaleBinding binding = this.localeBinding;
        if (binding == null) {
            return List.copyOf(commands);
        }
        return commands.stream()
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
