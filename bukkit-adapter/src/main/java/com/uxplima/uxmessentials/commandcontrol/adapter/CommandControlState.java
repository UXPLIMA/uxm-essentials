package com.uxplima.uxmessentials.commandcontrol.adapter;

import java.util.Collection;
import java.util.Objects;
import java.util.function.Function;

import com.uxplima.uxmessentials.commandcontrol.application.CommandControlConfig;
import com.uxplima.uxmessentials.commandcontrol.application.LanguageWords;
import com.uxplima.uxmessentials.commandcontrol.domain.CommandRateLimiter;
import com.uxplima.uxmessentials.commandcontrol.domain.WorldHidePolicies;
import com.uxplima.uxmessentials.commandcontrol.domain.WorldRuleSets;
import org.jspecify.annotations.NullMarked;

/**
 * The command-control rules in force, swapped whole when {@code /uxmess reload} re-reads the module's config. Every
 * listener, the published check and the placeholders read {@link #current()} on each use instead of holding the rules
 * they were built with, so an edited list applies to the next command without a restart, and a command checked
 * mid-reload sees one coherent snapshot rather than half of each.
 */
@NullMarked
public final class CommandControlState {

    private final Function<String, Collection<String>> pluginCommands;
    private volatile Snapshot current;

    private CommandControlState(Function<String, Collection<String>> pluginCommands, Snapshot first) {
        this.pluginCommands = pluginCommands;
        this.current = first;
    }

    /**
     * The state built from {@code config}, its {@code plugin:<name>} entries opened through {@code pluginCommands}.
     * Opening them reads the server's command map, so it runs on the server's main thread.
     */
    public static CommandControlState of(
            CommandControlConfig config, Function<String, Collection<String>> pluginCommands) {
        Objects.requireNonNull(pluginCommands, "pluginCommands");
        return new CommandControlState(pluginCommands, Snapshot.of(config, pluginCommands));
    }

    /** The rules in force right now. */
    public Snapshot current() {
        return current;
    }

    /** Put the rules {@code config} describes in force, replacing the previous ones in one step. */
    public void replace(CommandControlConfig config) {
        current = Snapshot.of(config, pluginCommands);
    }

    /** Open the {@code plugin:<name>} entries again, for the commands registered since they were last opened. */
    public void refresh() {
        replace(current.config());
    }

    /**
     * One coherent set of rules, derived from a single read of the config.
     *
     * @param config the config the rules were read from, for its switches, with its {@code plugin:} entries unopened
     * @param worldRules the world-scoped command lists
     * @param worldHidePolicies the world-scoped plugin-hide
     * @param languageWords the command words each language's readers are sent alone
     * @param rateLimiter the command-spam limit
     */
    public record Snapshot(
            CommandControlConfig config,
            WorldRuleSets worldRules,
            WorldHidePolicies worldHidePolicies,
            LanguageWords languageWords,
            CommandRateLimiter rateLimiter) {

        public Snapshot {
            Objects.requireNonNull(config, "config");
            Objects.requireNonNull(worldRules, "worldRules");
            Objects.requireNonNull(worldHidePolicies, "worldHidePolicies");
            Objects.requireNonNull(languageWords, "languageWords");
            Objects.requireNonNull(rateLimiter, "rateLimiter");
        }

        static Snapshot of(CommandControlConfig config, Function<String, Collection<String>> pluginCommands) {
            CommandControlConfig opened = config.withPluginCommands(pluginCommands);
            return new Snapshot(
                    config,
                    opened.toWorldRuleSets(CommandControlWiring.BYPASS_PERMISSION),
                    config.toWorldHidePolicies(CommandControlWiring.VIEW_PERMISSION),
                    config.toLanguageWords(),
                    config.toRateLimiter());
        }

        /** Whether a {@code namespace:} prefix is folded back to the bare command before the lists are read. */
        public boolean blockNamespaceBypass() {
            return config.blockNamespaceBypass();
        }
    }
}
