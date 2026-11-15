package com.uxplima.uxmessentials.shared.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * This server as the network sees it: its own id and the shared sets it belongs to. It answers which
 * {@link ServerScope scopes} apply here, the one rule guard regions, holograms, NPCs and the main spawn follow.
 *
 * <p>A server name a scope cannot hold (a space, a letter outside A to Z, more than 64 characters) leaves the server
 * without a scope of its own: its sets and the network still apply, and nothing can be scoped to it alone. Spawn
 * resolution and rendering keep working rather than failing on every join.
 *
 * @param serverId this server's network name
 * @param sharedSets the shared sets this server belongs to, in the order the operator listed them
 */
public record LocalServer(String serverId, List<String> sharedSets) {

    public LocalServer {
        Objects.requireNonNull(serverId, "serverId");
        sharedSets = List.copyOf(sharedSets);
    }

    /** This server's own scope, or empty when its name cannot be one. */
    public Optional<ServerScope> serverScope() {
        String own = "server:" + serverId;
        return ServerScope.isScope(own) ? Optional.of(new ServerScope(own)) : Optional.empty();
    }

    /** Every scope that applies here, the server's own first and then each shared set. */
    public List<ServerScope> loadedScopes() {
        List<ServerScope> scopes = new ArrayList<>();
        serverScope().ifPresent(scopes::add);
        for (String set : sharedSets) {
            scopes.add(ServerScope.set(set));
        }
        return List.copyOf(scopes);
    }

    /** True when something with {@code scope} applies here; no scope at all applies on every server. */
    public boolean covers(@Nullable ServerScope scope) {
        if (scope == null) {
            return true;
        }
        return scope.shared() ? sharedSets.contains(scope.name()) : scope.name().equals(serverId);
    }

    /** The scope words a command completes: {@code server}, then each scope that applies here in stored form. */
    public List<String> suggestions() {
        List<String> words = new ArrayList<>();
        if (serverScope().isPresent()) {
            words.add("server");
        }
        for (ServerScope scope : loadedScopes()) {
            words.add(scope.value());
        }
        return List.copyOf(words);
    }

    /**
     * Read a scope an operator typed: {@code server} names this server, a stored form ({@code server:<id>} or
     * {@code set:<name>}) names itself, and the bare name of a set this server belongs to names that set, the same
     * words {@code /guard scope} takes. Anything else is empty.
     */
    public Optional<ServerScope> parse(String written) {
        String text = Objects.requireNonNull(written, "written").strip();
        if (text.equalsIgnoreCase("server")) {
            return serverScope();
        }
        if (ServerScope.isScope(text)) {
            return Optional.of(new ServerScope(text));
        }
        if (sharedSets.contains(text)) {
            return Optional.of(ServerScope.set(text));
        }
        return Optional.empty();
    }
}
