package com.uxplima.uxmessentials.customcommands.application;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

import com.uxplima.uxmessentials.customcommands.domain.ActionChain;
import com.uxplima.uxmessentials.customcommands.domain.CommandDuration;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;

/**
 * What the operator may allow a definition to do, read once from {@code modules/customcommands/config.conf}. The two
 * privileged action heads are gated here rather than in a file, because a command file is content and this is
 * policy: an operator who lets somebody author commands can still refuse to let those commands run as the console.
 *
 * <p>The depth and delay ceilings bound what one command can cost the server: a chain that calls itself, or one
 * that schedules a thousand steps an hour out, is refused by these numbers rather than by hoping nobody writes one.
 *
 * <p>{@code onBackendMissing} is policy for the same reason: whether a price may be skipped while the economy is down
 * is the operator's call, not a command file's.
 */
public record CustomCommandsConfig(
        boolean enabled,
        boolean allowConsoleActions,
        boolean allowOpActions,
        int maxChainDepth,
        Duration maxDelay,
        int maxDelayedSteps,
        boolean logPrivilegedActions,
        String currency,
        FeeBackendPolicy onBackendMissing) {

    /** The shipped ceiling on a single {@code delay:} step. */
    private static final Duration DEFAULT_MAX_DELAY = Duration.ofSeconds(60);

    /** The shipped ceiling on how many delayed steps one chain may schedule. */
    private static final int DEFAULT_MAX_DELAYED_STEPS = 20;

    /** The shipped ceiling on how deep a command may call itself. */
    private static final int DEFAULT_MAX_CHAIN_DEPTH = 5;

    public CustomCommandsConfig {
        Objects.requireNonNull(maxDelay, "maxDelay");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(onBackendMissing, "onBackendMissing");
        maxChainDepth = Math.max(1, maxChainDepth);
        maxDelayedSteps = Math.max(0, maxDelayedSteps);
    }

    /** The file the module's block is read from, named in the warnings an operator acts on. */
    private static final String FILE = "modules/customcommands/config.conf";

    /** Read the module's own block, falling back to the shipped defaults for anything absent or unreadable. */
    public static CustomCommandsConfig from(ConfigStore config, Logger log) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(log, "log");
        return new CustomCommandsConfig(
                config.getBoolean("enabled", true),
                config.getBoolean("allow-console-actions", true),
                config.getBoolean("allow-op-actions", false),
                config.getInt("max-chain-depth", DEFAULT_MAX_CHAIN_DEPTH),
                CommandDuration.parse(config.getString("max-delay", "60s")).orElse(DEFAULT_MAX_DELAY),
                config.getInt("max-delayed-steps", DEFAULT_MAX_DELAYED_STEPS),
                config.getBoolean("log-privileged-actions", true),
                config.getString("default-currency", "vault"),
                feeBackendPolicy(config.getString("on-backend-missing", "allow"), log));
    }

    /**
     * Read {@code on-backend-missing}. An unknown word falls back to allowing, the behaviour a server had before the
     * key existed, so a typo never starts refusing every priced command; the warning says what was ignored.
     */
    private static FeeBackendPolicy feeBackendPolicy(String raw, Logger log) {
        return switch (raw.strip().toLowerCase(Locale.ROOT)) {
            case "allow" -> FeeBackendPolicy.ALLOW;
            case "deny" -> FeeBackendPolicy.DENY;
            default -> {
                log.warn(
                        "{}: on-backend-missing = '{}' is not allow or deny; using allow, so priced custom commands"
                                + " run for free while their currency backend is missing",
                        FILE,
                        raw);
                yield FeeBackendPolicy.ALLOW;
            }
        };
    }

    /** The chain limits these settings impose, as the domain reads them. */
    public ActionChain.ChainLimits chainLimits() {
        return new ActionChain.ChainLimits(maxDelay, maxDelayedSteps);
    }
}
