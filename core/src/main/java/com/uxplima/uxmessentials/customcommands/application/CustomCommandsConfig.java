package com.uxplima.uxmessentials.customcommands.application;

import java.time.Duration;
import java.util.Objects;

import com.uxplima.uxmessentials.customcommands.domain.ActionChain;
import com.uxplima.uxmessentials.customcommands.domain.CommandDuration;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;

/**
 * What the operator may allow a definition to do, read once from {@code modules/customcommands/config.conf}. The two
 * privileged action heads are gated here rather than in a file, because a command file is content and this is
 * policy: an operator who lets somebody author commands can still refuse to let those commands run as the console.
 *
 * <p>The depth and delay ceilings bound what one command can cost the server: a chain that calls itself, or one
 * that schedules a thousand steps an hour out, is refused by these numbers rather than by hoping nobody writes one.
 */
public record CustomCommandsConfig(
        boolean enabled,
        boolean allowConsoleActions,
        boolean allowOpActions,
        int maxChainDepth,
        Duration maxDelay,
        int maxDelayedSteps,
        boolean logPrivilegedActions,
        String currency) {

    /** The shipped ceiling on a single {@code delay:} step. */
    private static final Duration DEFAULT_MAX_DELAY = Duration.ofSeconds(60);

    /** The shipped ceiling on how many delayed steps one chain may schedule. */
    private static final int DEFAULT_MAX_DELAYED_STEPS = 20;

    /** The shipped ceiling on how deep a command may call itself. */
    private static final int DEFAULT_MAX_CHAIN_DEPTH = 5;

    public CustomCommandsConfig {
        Objects.requireNonNull(maxDelay, "maxDelay");
        Objects.requireNonNull(currency, "currency");
        maxChainDepth = Math.max(1, maxChainDepth);
        maxDelayedSteps = Math.max(0, maxDelayedSteps);
    }

    /** Read the module's own block, falling back to the shipped defaults for anything absent or unreadable. */
    public static CustomCommandsConfig from(ConfigStore config) {
        Objects.requireNonNull(config, "config");
        return new CustomCommandsConfig(
                config.getBoolean("enabled", true),
                config.getBoolean("allow-console-actions", true),
                config.getBoolean("allow-op-actions", false),
                config.getInt("max-chain-depth", DEFAULT_MAX_CHAIN_DEPTH),
                CommandDuration.parse(config.getString("max-delay", "60s")).orElse(DEFAULT_MAX_DELAY),
                config.getInt("max-delayed-steps", DEFAULT_MAX_DELAYED_STEPS),
                config.getBoolean("log-privileged-actions", true),
                config.getString("default-currency", "vault"));
    }

    /** The chain limits these settings impose, as the domain reads them. */
    public ActionChain.ChainLimits chainLimits() {
        return new ActionChain.ChainLimits(maxDelay, maxDelayedSteps);
    }
}
