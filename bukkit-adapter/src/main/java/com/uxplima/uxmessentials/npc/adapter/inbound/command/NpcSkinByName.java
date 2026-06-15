package com.uxplima.uxmessentials.npc.adapter.inbound.command;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.npc.application.NpcMessageKey;
import com.uxplima.uxmessentials.npc.application.NpcNotifier;
import com.uxplima.uxmessentials.npc.application.SetNpcSkin;
import com.uxplima.uxmessentials.npc.application.port.NpcRepository;
import com.uxplima.uxmessentials.npc.application.port.SkinService;
import com.uxplima.uxmessentials.npc.domain.NpcName;
import com.uxplima.uxmessentials.npc.domain.NpcSkin;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The {@code /npc skin <name> name:<username>} flow, kept Bukkit-free so the orchestration is unit-testable: it
 * touches only the application ports, the {@link SkinService}, the {@link Scheduler}, and the {@link NpcNotifier},
 * with no {@code org.bukkit} type, so a test drives it with fakes and asserts the message/apply sequence without
 * a live server.
 *
 * <p>The flow: reject up front when no NPC exists under the name (so a typo never triggers a Mojang call); send
 * the operator an immediate "fetching" line; then resolve the skin off-thread through the service. On completion
 * it hops back onto the global region thread before touching the stored model — a present skin is applied through
 * {@link SetNpcSkin} (which saves and re-renders), an empty result is reported as a fetch failure. The command
 * thread is never blocked on the future.
 */
@NullMarked
public final class NpcSkinByName {

    private final SkinService skinService;
    private final SetNpcSkin setSkin;
    private final NpcRepository repository;
    private final NpcNotifier notifier;
    private final Scheduler scheduler;

    public NpcSkinByName(
            SkinService skinService,
            SetNpcSkin setSkin,
            NpcRepository repository,
            NpcNotifier notifier,
            Scheduler scheduler) {
        this.skinService = Objects.requireNonNull(skinService, "skinService");
        this.setSkin = Objects.requireNonNull(setSkin, "setSkin");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    /**
     * Resolve {@code username}'s skin and apply it to NPC {@code name} for {@code actor}. Returns {@code false}
     * (and reports {@link NpcMessageKey#NPC_NOT_FOUND}) when no such NPC exists — nothing is fetched; otherwise
     * sends the fetching feedback, kicks the off-thread lookup, and returns {@code true} immediately.
     */
    public boolean apply(PlayerRef actor, NpcName name, String username) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(username, "username");
        if (!repository.exists(name)) {
            notifier.send(actor, NpcMessageKey.NPC_NOT_FOUND, Map.of("name", name.value()));
            return false;
        }
        notifier.send(actor, NpcMessageKey.NPC_SKIN_FETCHING, Map.of("player", username));
        // The service's future never completes exceptionally, so the dependent stage carries no error to chain;
        // the unused handle is the fire-and-forget completion, deliberately not awaited on the command thread.
        var unused = skinService.fetchByName(username).thenAccept(skin -> onResolved(actor, name, username, skin));
        return true;
    }

    private void onResolved(PlayerRef actor, NpcName name, String username, Optional<NpcSkin> skin) {
        // The fetch completes on the async pool; hop back onto the global region thread before saving/rendering.
        scheduler.onGlobal(() -> {
            if (skin.isPresent()) {
                setSkin.setSkin(actor, name, skin.get());
            } else {
                notifier.send(actor, NpcMessageKey.NPC_SKIN_FETCH_FAILED, Map.of("player", username));
            }
        });
    }
}
