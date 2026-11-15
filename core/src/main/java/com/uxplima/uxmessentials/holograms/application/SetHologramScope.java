package com.uxplima.uxmessentials.holograms.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.holograms.application.port.HologramRepository;
import com.uxplima.uxmessentials.holograms.application.port.HologramView;
import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramError;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.holograms.domain.HologramsMessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.LocalServer;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.ServerScope;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.jspecify.annotations.Nullable;

/**
 * {@code /hologram scope <name> <scope|clear>}: choose which servers of the network show a hologram. The saved
 * snapshot is rendered through the scoped view, so it leaves this server at once when the new scope excludes it,
 * and every peer reloads the row from the change frame and shows or hides it the same way.
 */
public final class SetHologramScope {

    /** The word that clears a scope, so the hologram shows on every server again. */
    public static final String CLEAR = "clear";

    private final HologramRepository repository;
    private final HologramView view;
    private final Notifier notifier;
    private final LocalServer local;

    public SetHologramScope(HologramRepository repository, HologramView view, Notifier notifier, LocalServer local) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.view = Objects.requireNonNull(view, "view");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.local = Objects.requireNonNull(local, "local");
    }

    /**
     * Scope hologram {@code name} to the scope {@code written} names: {@code clear} for every server, otherwise
     * what {@link LocalServer#parse} reads. A word that names no scope is refused before anything is looked up.
     */
    public Result<Unit, HologramError> set(PlayerRef actor, HologramName name, String written) {
        Objects.requireNonNull(written, "written");
        if (written.strip().equalsIgnoreCase(CLEAR)) {
            return set(actor, name, (ServerScope) null);
        }
        Optional<ServerScope> scope = local.parse(written);
        if (scope.isEmpty()) {
            notifier.send(actor, HologramError.INVALID_SCOPE.messageKey(), Map.of("scope", written));
            return Result.err(HologramError.INVALID_SCOPE);
        }
        return set(actor, name, scope.get());
    }

    /** The scope words the command completes: {@code clear}, then this server and its shared sets. */
    public List<String> suggestions() {
        List<String> words = new ArrayList<>();
        words.add(CLEAR);
        words.addAll(local.suggestions());
        return List.copyOf(words);
    }

    /** Scope hologram {@code name} to {@code scope}, or to every server when it is {@code null}. */
    public Result<Unit, HologramError> set(PlayerRef actor, HologramName name, @Nullable ServerScope scope) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        Optional<Hologram> existing = repository.find(name);
        if (existing.isEmpty()) {
            notifier.send(actor, HologramError.NOT_FOUND.messageKey(), Map.of("name", name.value()));
            return Result.err(HologramError.NOT_FOUND);
        }
        Hologram updated = existing.get().withScope(scope);
        repository.save(updated);
        view.render(updated);
        if (scope == null) {
            notifier.send(actor, HologramsMessageKey.HOLOGRAM_SCOPE_CLEARED, Map.of("name", name.value()));
        } else {
            notifier.send(
                    actor,
                    HologramsMessageKey.HOLOGRAM_SCOPE_SET,
                    Map.of("name", name.value(), "scope", scope.value()));
        }
        return Result.ok();
    }
}
