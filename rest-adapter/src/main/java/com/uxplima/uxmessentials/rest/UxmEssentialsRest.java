package com.uxplima.uxmessentials.rest;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.logging.Level;

import org.bukkit.plugin.java.JavaPlugin;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;

import com.uxplima.uxmessentials.api.bukkit.UxmEssentialsApi;
import com.uxplima.uxmessentials.rest.auth.AuthFilter;
import com.uxplima.uxmessentials.rest.auth.RateLimiter;
import com.uxplima.uxmessentials.rest.auth.TokenStore;
import com.uxplima.uxmessentials.rest.bridge.EventBridge;
import com.uxplima.uxmessentials.rest.http.RestServer;
import com.uxplima.uxmessentials.rest.socket.EventStream;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.spongepowered.configurate.ConfigurateException;

/**
 * The entry point of the optional {@code uxmEssentials-rest} jar.
 *
 * <p>It ships switched off. An operator who drops it in {@code plugins/} gets a config file, one line in the log
 * saying where the switch is, and no open port: a jar that starts listening because it was installed is a jar that
 * turns "I tried the add-on" into an incident.
 *
 * <p>When it is on, enabling does four things in order: read the config, open the token store, register
 * {@code /uxmapi}, and bind. If any of them fails the port never opens, which is the only failure direction worth
 * having.
 *
 * <p>Everything it serves comes through the published developer API. It holds no reference into the host beyond
 * that interface, which is why it can be updated, removed, or left behind a version without the host noticing.
 *
 * <p>Writes are attributed per token rather than per jar. Every request builds its action surface with the label of
 * the token that made it, so a ban placed over HTTP reads as {@code uxmEssentials-rest/panel} in the audit log and
 * an operator can tell one caller from another.
 */
@NullMarked
public final class UxmEssentialsRest extends JavaPlugin {

    private @Nullable RestServer server;
    private @Nullable EventStream events;
    private @Nullable EventBridge bridge;

    @Override
    public void onEnable() {
        RestConfig config = readConfig();
        if (!config.enabled()) {
            getLogger().info("REST API off. Turn it on in " + configPath() + " and restart.");
            return;
        }
        UxmEssentialsApi api = UxmEssentialsApi.get();
        if (api == null) {
            getLogger().severe("uxmEssentials is not running its API, so there is nothing to serve. Staying off.");
            return;
        }
        TokenStore tokens = TokenStore.open(getDataFolder().toPath());
        getLifecycleManager()
                .registerEventHandler(
                        LifecycleEvents.COMMANDS,
                        event -> event.registrar()
                                .register(new TokenCommand(tokens).build(), "uxmEssentials REST API tokens"));
        start(config, api, tokens);
    }

    @Override
    public void onDisable() {
        if (bridge != null) {
            bridge.close();
            bridge = null;
        }
        if (events != null) {
            events.close();
            events = null;
        }
        if (server != null) {
            server.close();
            server = null;
        }
    }

    private void start(RestConfig config, UxmEssentialsApi api, TokenStore tokens) {
        AuthFilter filter = new AuthFilter(tokens, new RateLimiter(config.requestsPerMinute(), Clock.systemUTC()));
        EventStream stream = new EventStream(Routes.EVENTS, getLogger());
        try {
            server = RestServer.start(
                    config.bind(),
                    config.port(),
                    Routes.build(api, caller -> api.actions(this, caller)),
                    filter,
                    stream,
                    getLogger());
        } catch (IOException failure) {
            getLogger().log(Level.SEVERE, "could not listen on " + config.bind() + ":" + config.port(), failure);
            return;
        }
        events = stream;
        EventBridge watcher = new EventBridge(stream, getLogger());
        watcher.register(this, getServer().getPluginManager());
        bridge = watcher;
        getLogger()
                .info("REST API listening on " + config.bind() + ":" + config.port()
                        + ". Make a token with /uxmapi token create <label>.");
        warnIfExposed(config);
    }

    /**
     * Say something when the listener is bound to more than the loopback address.
     *
     * <p>Not a refusal: an operator may well have a firewall and a proxy in front of it, and a plugin that decides
     * it knows their network better than they do is a plugin they stop using. One line, once, so the decision is at
     * least a decision.
     */
    private void warnIfExposed(RestConfig config) {
        if (config.isExposed()) {
            getLogger()
                    .warning("The REST API is bound to " + config.bind()
                            + ", which is reachable from outside this machine. Put it behind a reverse proxy with TLS,"
                            + " or bind 127.0.0.1 and tunnel to it.");
        }
    }

    private RestConfig readConfig() {
        try {
            return RestConfigLoader.load(getDataFolder().toPath());
        } catch (ConfigurateException | RuntimeException unreadable) {
            getLogger().log(Level.SEVERE, "could not read " + configPath() + ", staying off", unreadable);
            return RestConfig.DORMANT;
        }
    }

    private String configPath() {
        return Path.of(getDataFolder().getName(), "config", "rest.conf").toString();
    }
}
