package com.uxplima.uxmessentials.moderation.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.uxplima.uxmessentials.moderation.application.port.CommandNames;
import org.junit.jupiter.api.Test;

/**
 * A muted player cannot reach a listed command by another of its names: its namespaced form, an alias the
 * command lists, or an alias whose own entry knows nothing of the command it points at. A word on the allowed list
 * always runs, and the names of the listed commands are read from the server once, not on every command.
 */
class MutedCommandPolicyTest {

    /**
     * The command list as Paper reports it: an older plugin's alias resolves to its command, so {@code whisperto}
     * answers with the names of {@code essentials:msg}; a newer plugin's alias is its own entry knowing only
     * itself, while its command lists it, so {@code dm} is found only through {@code chat}. A ticket plugin's
     * {@code ticketreply} also answers to {@code reply}.
     */
    private final Map<String, Set<String>> server = new HashMap<>(Map.of(
            "msg", Set.of("msg", "whisperto"),
            "essentials:msg", Set.of("msg", "whisperto"),
            "whisperto", Set.of("msg", "whisperto"),
            "chat", Set.of("chat", "dm"),
            "dm", Set.of("dm"),
            "home", Set.of("home", "h"),
            "ticketreply", Set.of("ticketreply", "reply"),
            "reply", Set.of("ticketreply", "reply")));

    private final List<String> lookups = new ArrayList<>();
    private final CommandNames names = word -> {
        lookups.add(word);
        return server.getOrDefault(word, Set.of());
    };

    private MutedCommandPolicy blocking(String... entries) {
        return new MutedCommandPolicy(Set.of(entries), Set.of(), names);
    }

    @Test
    void aListedWordAlsoStopsItsNamespacedForms() {
        MutedCommandPolicy policy = new MutedCommandPolicy(Set.of("msg", "me"), Set.of(), CommandNames.NONE);

        assertThat(policy.isBlocked("minecraft:msg")).isTrue();
        assertThat(policy.isBlocked("essentials:msg")).isTrue();
        assertThat(policy.isBlocked("/MINECRAFT:ME")).isTrue();
        assertThat(policy.isBlocked("minecraft:home")).isFalse();
    }

    @Test
    void aNamespacedEntryStopsTheBareWordToo() {
        MutedCommandPolicy policy = new MutedCommandPolicy(Set.of("/essentials:me"), Set.of(), CommandNames.NONE);

        assertThat(policy.isBlocked("me")).isTrue();
        assertThat(policy.isBlocked("bukkit:me")).isTrue();
    }

    @Test
    void anAliasThatResolvesToAListedCommandIsStopped() {
        assertThat(blocking("msg").isBlocked("whisperto")).isTrue();
    }

    @Test
    void anAliasTheListedCommandNamesIsStoppedThoughItsOwnEntryKnowsNothing() {
        assertThat(blocking("chat").isBlocked("dm")).isTrue();
    }

    @Test
    void anEntryStopsEveryNameOfTheCommandThatOwnsTheWord() {
        assertThat(blocking("reply").isBlocked("ticketreply")).isTrue();
    }

    @Test
    void anAllowedWordRunsThoughTheCommandOwnsAListedName() {
        MutedCommandPolicy policy = new MutedCommandPolicy(Set.of("reply"), Set.of("ticketreply"), names);

        assertThat(policy.isBlocked("ticketreply")).isFalse();
        assertThat(policy.isBlocked("tickets:ticketreply")).isFalse();
        assertThat(policy.isBlocked("reply")).isTrue();
    }

    @Test
    void aCommandSharingNoNameWithTheListRuns() {
        MutedCommandPolicy policy = blocking("msg", "chat");

        assertThat(policy.isBlocked("home")).isFalse();
        assertThat(policy.isBlocked("h")).isFalse();
        assertThat(policy.isBlocked("unknown")).isFalse();
        assertThat(policy.isBlocked("minecraft:")).isFalse();
        assertThat(policy.isBlocked("")).isFalse();
        assertThat(policy.isBlocked("/")).isFalse();
    }

    @Test
    void theListedCommandsAreLookedUpOnceAndAgainOnlyWhenForgotten() {
        MutedCommandPolicy policy = blocking("msg", "chat");
        policy.isBlocked("home");
        lookups.clear();

        policy.isBlocked("home");
        policy.isBlocked("spawn");

        assertThat(lookups).containsExactly("home", "spawn");

        server.put("chat", Set.of("chat", "dm", "pm"));
        policy.forgetCommandNames();
        assertThat(policy.isBlocked("pm")).isTrue();
    }

    @Test
    void anEmptyListBlocksNothingAndLooksNothingUp() {
        MutedCommandPolicy policy = blocking(" ", "/");

        assertThat(policy.isEmpty()).isTrue();
        assertThat(policy.isBlocked("msg")).isFalse();
        assertThat(lookups).isEmpty();
    }
}
