package com.uxplima.uxmessentials.security.adapter;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Lets an offline-mode server's login plugin finish before this module asks for a second factor.
 *
 * <p>On a cracked server a connecting client has proved nothing at all: the name is whatever they typed. A login
 * plugin (AuthMe and the several forks of it) is what turns that into an account, and until it has, the player at the
 * keyboard is nobody in particular. Freezing them for a second factor before then is both wrong and useless: wrong
 * because we would be asking an unauthenticated stranger for the account holder's PIN, and useless because the login
 * plugin has them frozen for its own prompt anyway, so the two fight over the same screen.
 *
 * <p>So when a login plugin is present, the join decision waits, and what starts it is that plugin saying the player
 * is authenticated. Order restored: password first, second factor second, which is what "two factor" has always
 * meant.
 *
 * <p>The hook deliberately carries no compile-time dependency on any login plugin. Each is described by the name of
 * its login event, resolved by reflection at startup and registered through Bukkit's own dynamic
 * {@link org.bukkit.plugin.PluginManager#registerEvent} path. A server with none of them installed resolves nothing
 * and the module behaves exactly as it did; a server with one gets the handoff without this plugin depending on it,
 * and adding support for another is one more line in the list below.
 */
@NullMarked
public final class LoginPluginHandoff {

    /**
     * The login events worth waiting on, most-used first. All of them carry a {@code getPlayer()}, which is the only
     * thing this class needs from them.
     */
    private static final List<String> LOGIN_EVENTS = List.of(
            "fr.xephi.authme.events.LoginEvent",
            "com.nickuc.login.api.events.bukkit.LoginEvent",
            "me.vagdedes.authenticator.events.PlayerLoginEvent",
            "net.craftersland.customauth.events.AuthEvent");

    private final Plugin plugin;
    private final Logger log;
    private @Nullable Listener registered;

    public LoginPluginHandoff(Plugin plugin, Logger log) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.log = Objects.requireNonNull(log, "log");
    }

    /**
     * Hook whichever login plugin is installed, calling {@code onAuthenticated} when it reports a player logged in.
     *
     * @return whether a login plugin was found, which is the caller's signal to hold the join decision back
     */
    public boolean hook(Consumer<Player> onAuthenticated) {
        Objects.requireNonNull(onAuthenticated, "onAuthenticated");
        Optional<Class<? extends Event>> found = firstAvailable();
        if (found.isEmpty()) {
            return false;
        }
        Class<? extends Event> eventType = found.get();
        Listener listener = new Listener() {};
        plugin.getServer()
                .getPluginManager()
                .registerEvent(
                        eventType,
                        listener,
                        EventPriority.MONITOR,
                        (ignored, event) -> {
                            if (eventType.isInstance(event)) {
                                playerOf(event).ifPresent(onAuthenticated);
                            }
                        },
                        plugin);
        registered = listener;
        log.info("event=security_login_plugin_hooked event_class={}", eventType.getName());
        return true;
    }

    /** Drop the hook, so a module disable or reload leaves no listener behind on the other plugin's event. */
    public void unhook() {
        if (registered != null) {
            HandlerList.unregisterAll(registered);
            registered = null;
        }
    }

    private static Optional<Class<? extends Event>> firstAvailable() {
        for (String name : LOGIN_EVENTS) {
            try {
                Class<?> found = Class.forName(name);
                if (Event.class.isAssignableFrom(found)) {
                    return Optional.of(found.asSubclass(Event.class));
                }
            } catch (ClassNotFoundException notInstalled) {
                // Expected for every login plugin this server does not run, which is most of them.
            }
        }
        return Optional.empty();
    }

    /**
     * The player a login event is about, read reflectively because the event types are not on our classpath. A login
     * event without the accessor is not one we can use, so it is skipped rather than guessed at.
     */
    private Optional<Player> playerOf(Event event) {
        try {
            Object player = event.getClass().getMethod("getPlayer").invoke(event);
            return player instanceof Player live ? Optional.of(live) : Optional.empty();
        } catch (ReflectiveOperationException | RuntimeException unusable) {
            log.warn(
                    "event=security_login_plugin_event_unreadable event_class={}",
                    event.getClass().getName());
            return Optional.empty();
        }
    }
}
