package com.uxplima.uxmessentials.velocity.commandcontrol;

import java.nio.file.Path;
import java.util.Objects;

import com.uxplima.uxmessentials.commandcontrol.application.CommandControlConfig;
import com.uxplima.uxmessentials.commandcontrol.domain.ChannelHidePolicy;
import com.uxplima.uxmessentials.commandcontrol.domain.CommandRateLimiter;
import com.uxplima.uxmessentials.commandcontrol.domain.HidePolicy;
import com.uxplima.uxmessentials.commandcontrol.domain.RuleSet;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.velocitypowered.api.proxy.ProxyServer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.configurate.ConfigurationNode;

/**
 * Wires the proxy command-control layer from {@code config.conf}: it resolves the {@code command-control}
 * section into the pure {@code :core} {@link CommandControlConfig}, builds the rule set, hide policy, and
 * command-spam limiter exactly as the backend adapter does, and registers the {@link CommandControlListener}
 * on the proxy event bus. A disabled section (the shipped default) wires nothing.
 *
 * <p>The world-scoped variants are backend-only, so the proxy uses the base rule set and hide policy. The
 * {@code plugin-channel-hide} block is read and validated but not enforced here: Velocity's API does not
 * expose the client-facing channel-advertisement packets, so filtering them would need a packet library
 * (out of scope). When it is switched on, a warning is logged pointing the operator at the backend module,
 * rather than silently doing nothing.
 */
public final class ProxyCommandControl {

    /** The node that exempts a holder from the proxy command gate and tree scrub: sees and runs everything. */
    public static final String BYPASS = "uxmessentials.commandcontrol.bypass";

    /** The node that reveals the hidden proxy-native commands the plugin-hide otherwise removes and blocks. */
    public static final String VIEW = "uxmessentials.commandcontrol.viewproxycommands";

    /** The node that exempts a holder from the proxy command-spam limiter (never counted, never actioned). */
    public static final String SPAM_BYPASS = "uxmessentials.commandcontrol.spam.bypass";

    private static final String CONFIG_ROOT = "command-control";

    private ProxyCommandControl() {}

    /**
     * Load the config under {@code dataDirectory}, and when {@code command-control.enabled} is set register
     * the listener on {@code proxy} attributed to {@code plugin}. A load failure is logged and the feature is
     * skipped rather than failing proxy init.
     */
    public static void enable(Object plugin, ProxyServer proxy, Logger logger, Path dataDirectory) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(proxy, "proxy");
        Objects.requireNonNull(logger, "logger");
        Objects.requireNonNull(dataDirectory, "dataDirectory");
        ConfigStore store = loadStore(logger, dataDirectory);
        if (store == null) {
            return;
        }
        CommandControlConfig config = CommandControlConfig.from(store);
        if (!config.enabled()) {
            return;
        }
        proxy.getEventManager().register(plugin, buildListener(config, store, proxy, logger));
        warnIfChannelHideConfigured(config, logger);
        logger.info(
                "uxmEssentials command-control active on the proxy (spam-guard {})",
                config.toRateLimiter().isEnabled() ? "on" : "off");
    }

    /** Extract the default config if missing and wrap the loaded tree scoped to {@code command-control}. */
    private static @Nullable ConfigStore loadStore(Logger logger, Path dataDirectory) {
        try {
            ConfigurationNode root = CommandControlConfigLoader.load(dataDirectory);
            return new NodeConfigStore(root).scoped(CONFIG_ROOT);
        } catch (RuntimeException | org.spongepowered.configurate.ConfigurateException failure) {
            logger.error("failed to load proxy command-control config; the feature stays off", failure);
            return null;
        }
    }

    private static CommandControlListener buildListener(
            CommandControlConfig config, ConfigStore store, ProxyServer proxy, Logger logger) {
        RuleSet rules = config.toRuleSet(BYPASS);
        HidePolicy hide = config.toHidePolicy(VIEW);
        CommandRateLimiter limiter = config.toRateLimiter();
        ProxyGroupSource groups = ProxyGroupSources.create(proxy, logger);
        ProxyCommandMessages messages = ProxyCommandMessages.from(store);
        ProxyCommandTreeFilter filter =
                new ProxyCommandTreeFilter(rules, hide, config.tabCompletionEnabled(), config.blockNamespaceBypass());
        return new CommandControlListener(
                rules,
                hide,
                filter,
                limiter,
                groups,
                messages,
                config.blockNamespaceBypass(),
                config.denyListCommands(),
                config.useUnknownCommandMessage());
    }

    private static void warnIfChannelHideConfigured(CommandControlConfig config, Logger logger) {
        ChannelHidePolicy channelHide = config.toChannelHidePolicy();
        if (channelHide.isEnabled()) {
            logger.warn("command-control plugin-channel-hide is enabled but not enforced at the proxy: Velocity's API"
                    + " does not expose client-facing channel advertisements. Use the backend commandcontrol"
                    + " module's plugin-channel-hide to strip channels per backend.");
        }
    }
}
