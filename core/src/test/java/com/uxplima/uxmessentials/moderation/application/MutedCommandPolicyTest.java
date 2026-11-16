package com.uxplima.uxmessentials.moderation.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;

import com.uxplima.uxmessentials.moderation.application.port.CommandNames;
import org.junit.jupiter.api.Test;

/**
 * A muted player cannot reach a listed command by another of its names: its namespaced form, an alias the
 * command lists, or an alias whose own entry knows nothing of the command it points at.
 */
class MutedCommandPolicyTest {

    /**
     * The command list as Paper reports it: an older plugin's alias resolves to its command, so {@code whisperto}
     * answers with the names of {@code essentials:msg}; a newer plugin's alias is its own entry knowing only
     * itself, while its command lists it, so {@code dm} is found only through {@code chat}.
     */
    private static final Map<String, Set<String>> SERVER = Map.of(
            "msg", Set.of("msg", "whisperto"),
            "essentials:msg", Set.of("msg", "whisperto"),
            "whisperto", Set.of("msg", "whisperto"),
            "chat", Set.of("chat", "dm"),
            "dm", Set.of("dm"),
            "home", Set.of("home", "h"));

    private static final CommandNames NAMES = word -> SERVER.getOrDefault(word, Set.of());

    @Test
    void aListedWordAlsoStopsItsNamespacedForms() {
        MutedCommandPolicy policy = new MutedCommandPolicy(Set.of("msg", "me"), CommandNames.NONE);

        assertThat(policy.isBlocked("minecraft:msg")).isTrue();
        assertThat(policy.isBlocked("essentials:msg")).isTrue();
        assertThat(policy.isBlocked("/MINECRAFT:ME")).isTrue();
        assertThat(policy.isBlocked("minecraft:home")).isFalse();
    }

    @Test
    void aNamespacedEntryStopsTheBareWordToo() {
        MutedCommandPolicy policy = new MutedCommandPolicy(Set.of("/essentials:me"), CommandNames.NONE);

        assertThat(policy.isBlocked("me")).isTrue();
        assertThat(policy.isBlocked("bukkit:me")).isTrue();
    }

    @Test
    void anAliasThatResolvesToAListedCommandIsStopped() {
        MutedCommandPolicy policy = new MutedCommandPolicy(Set.of("msg"), NAMES);

        assertThat(policy.isBlocked("whisperto")).isTrue();
    }

    @Test
    void anAliasTheListedCommandNamesIsStoppedThoughItsOwnEntryKnowsNothing() {
        MutedCommandPolicy policy = new MutedCommandPolicy(Set.of("chat"), NAMES);

        assertThat(policy.isBlocked("dm")).isTrue();
    }

    @Test
    void aCommandSharingNoNameWithTheListRuns() {
        MutedCommandPolicy policy = new MutedCommandPolicy(Set.of("msg", "chat"), NAMES);

        assertThat(policy.isBlocked("home")).isFalse();
        assertThat(policy.isBlocked("h")).isFalse();
        assertThat(policy.isBlocked("unknown")).isFalse();
        assertThat(policy.isBlocked("minecraft:")).isFalse();
    }

    @Test
    void anEmptyListBlocksNothing() {
        MutedCommandPolicy policy = new MutedCommandPolicy(Set.of(" ", "/"), NAMES);

        assertThat(policy.isEmpty()).isTrue();
        assertThat(policy.isBlocked("msg")).isFalse();
    }
}
