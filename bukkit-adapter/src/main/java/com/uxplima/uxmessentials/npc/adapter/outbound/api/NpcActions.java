package com.uxplima.uxmessentials.npc.adapter.outbound.api;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.api.action.UxmFailure;
import com.uxplima.uxmessentials.api.action.UxmNpcActions;
import com.uxplima.uxmessentials.api.action.UxmOutcome;
import com.uxplima.uxmessentials.api.view.UxmLocation;
import com.uxplima.uxmessentials.npc.application.CreateNpc;
import com.uxplima.uxmessentials.npc.application.DeleteNpc;
import com.uxplima.uxmessentials.npc.application.MoveNpcTo;
import com.uxplima.uxmessentials.npc.application.SetNpcClickCommand;
import com.uxplima.uxmessentials.npc.application.SetNpcDisplayName;
import com.uxplima.uxmessentials.npc.application.SetNpcSkin;
import com.uxplima.uxmessentials.npc.application.port.NpcRepository;
import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.npc.domain.NpcError;
import com.uxplima.uxmessentials.npc.domain.NpcName;
import com.uxplima.uxmessentials.npc.domain.NpcSkin;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.ApiValues;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.AsyncActions;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.application.port.SkinTextures;
import com.uxplima.uxmessentials.shared.application.port.WorldLookup;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.SkinTexture;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.jspecify.annotations.NullMarked;

/**
 * The published NPC writes, over the same use cases {@code /npc} runs.
 *
 * <p>Which means the same creation limit and the same ownership: the actor a verb names is charged for the NPC and
 * recorded as its owner, so a plugin cannot put up NPCs on nobody's behalf and leave an operator with a set nobody
 * is accountable for.
 *
 * <p>Everything runs on a worker thread. Each write is a database write, and the renderer takes itself to the
 * right thread to spawn or despawn, so nothing here needs a tick thread to do its work on.
 *
 * <p>A skin is named by the account that wears it rather than by a base64 texture, and is resolved through the same
 * server-wide lookup {@code /npc skin} uses, which works on an offline-mode server too. The resolve happens on the
 * worker thread the write is already on, so it is a blocking fetch that blocks nothing that matters.
 */
@NullMarked
public final class NpcActions implements UxmNpcActions {

    private final NpcRepository repository;
    private final CreateNpc creator;
    private final DeleteNpc remove;
    private final MoveNpcTo moveTo;
    private final SetNpcSkin skins;
    private final SetNpcDisplayName displayNames;
    private final SetNpcClickCommand clickCommands;
    private final SkinTextures textures;
    private final PlayerLookup players;
    private final WorldLookup worlds;
    private final Scheduler scheduler;

    public NpcActions(
            NpcRepository repository,
            CreateNpc creator,
            DeleteNpc remove,
            MoveNpcTo moveTo,
            SetNpcSkin skins,
            SetNpcDisplayName displayNames,
            SetNpcClickCommand clickCommands,
            SkinTextures textures,
            PlayerLookup players,
            WorldLookup worlds,
            Scheduler scheduler) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.creator = Objects.requireNonNull(creator, "creator");
        this.remove = Objects.requireNonNull(remove, "remove");
        this.moveTo = Objects.requireNonNull(moveTo, "moveTo");
        this.skins = Objects.requireNonNull(skins, "skins");
        this.displayNames = Objects.requireNonNull(displayNames, "displayNames");
        this.clickCommands = Objects.requireNonNull(clickCommands, "clickCommands");
        this.textures = Objects.requireNonNull(textures, "textures");
        this.players = Objects.requireNonNull(players, "players");
        this.worlds = Objects.requireNonNull(worlds, "worlds");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public CompletableFuture<UxmOutcome> create(UUID actorId, String name, UxmLocation where) {
        Objects.requireNonNull(where, "where");
        Optional<Position> at = ApiValues.position(worlds, where);
        if (at.isEmpty()) {
            return completed(noSuchWorld(where));
        }
        return named(actorId, name, (actor, npc) -> creator.create(actor, npc, at.get(), null));
    }

    @Override
    public CompletableFuture<UxmOutcome> delete(UUID actorId, String name) {
        return named(actorId, name, remove::delete);
    }

    @Override
    public CompletableFuture<UxmOutcome> move(UUID actorId, String name, UxmLocation where) {
        Objects.requireNonNull(where, "where");
        return answering(actorId, name, (actor, npc) -> moveWithinItsWorld(actor, npc, where));
    }

    @Override
    public CompletableFuture<UxmOutcome> setSkin(UUID actorId, String name, String skinOwner) {
        Objects.requireNonNull(skinOwner, "skinOwner");
        return named(actorId, name, (actor, npc) -> {
            Optional<SkinTexture> texture = textures.fetchNow(skinOwner);
            if (texture.isEmpty()) {
                return Result.err(NpcError.NOT_FOUND);
            }
            return skins.setSkin(
                    actor, npc, new NpcSkin(texture.get().value(), texture.get().signature()));
        });
    }

    @Override
    public CompletableFuture<UxmOutcome> clearSkin(UUID actorId, String name) {
        return named(actorId, name, skins::clearSkin);
    }

    @Override
    public CompletableFuture<UxmOutcome> setDisplayName(UUID actorId, String name, String displayName) {
        Objects.requireNonNull(displayName, "displayName");
        if (displayName.isBlank()) {
            throw new IllegalArgumentException("a blank display name hides the label: call hideDisplayName instead");
        }
        return named(actorId, name, (actor, npc) -> displayNames.setDisplayName(actor, npc, displayName));
    }

    /** The blank value is the module's own "show nothing" sentinel, and the reason this is a separate verb. */
    @Override
    public CompletableFuture<UxmOutcome> hideDisplayName(UUID actorId, String name) {
        return named(actorId, name, (actor, npc) -> displayNames.setDisplayName(actor, npc, ""));
    }

    @Override
    public CompletableFuture<UxmOutcome> clearDisplayName(UUID actorId, String name) {
        return named(actorId, name, (actor, npc) -> displayNames.setDisplayName(actor, npc, null));
    }

    @Override
    public CompletableFuture<UxmOutcome> setClickCommand(UUID actorId, String name, String command) {
        Objects.requireNonNull(command, "command");
        String bare = command.startsWith("/") ? command.substring(1) : command;
        if (bare.isBlank()) {
            throw new IllegalArgumentException("a click command must not be blank: call clearClickCommand instead");
        }
        return named(actorId, name, (actor, npc) -> clickCommands.setCommand(actor, npc, bare));
    }

    @Override
    public CompletableFuture<UxmOutcome> clearClickCommand(UUID actorId, String name) {
        return named(actorId, name, (actor, npc) -> clickCommands.setCommand(actor, npc, null));
    }

    /**
     * An NPC moves inside the world it stands in. Moving one across worlds would mean despawning it for everybody
     * watching and spawning it for a different set, which is a copy-and-delete rather than a move, so a location in
     * another world is refused here rather than half-honoured by ignoring the world name.
     */
    private UxmOutcome moveWithinItsWorld(PlayerRef actor, NpcName name, UxmLocation where) {
        Optional<Npc> existing = repository.find(name);
        if (existing.isEmpty()) {
            return refusal(NpcError.NOT_FOUND);
        }
        String standsIn = existing.get().location().world().name();
        if (!standsIn.equals(where.world())) {
            return UxmOutcome.failed(
                    UxmFailure.REFUSED, "an npc moves inside its own world, and this one stands in " + standsIn);
        }
        return outcome(moveTo.moveTo(actor, name, where.x(), where.y(), where.z(), where.yaw(), where.pitch()));
    }

    /** Check the name's shape and resolve the actor, then run {@code body} on a worker thread. */
    private CompletableFuture<UxmOutcome> named(UUID actorId, String name, Write body) {
        return answering(actorId, name, (actor, npc) -> outcome(body.apply(actor, npc)));
    }

    /** The same, for the one verb that has a refusal of its own to give rather than one the domain models. */
    private CompletableFuture<UxmOutcome> answering(UUID actorId, String name, Answer body) {
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(name, "name");
        Optional<NpcName> npc = NpcQueries.parse(name);
        if (npc.isEmpty()) {
            return completed(UxmOutcome.failed(
                    UxmFailure.REFUSED, "an npc name is at most " + NpcName.MAX_LENGTH + " characters: " + name));
        }
        PlayerRef actor = ApiValues.subject(players, actorId);
        return AsyncActions.perform(scheduler, () -> body.apply(actor, npc.get()));
    }

    private static UxmOutcome outcome(Result<Unit, NpcError> done) {
        return done.isErr() ? refusal(done.errorOrThrow()) : UxmOutcome.ok();
    }

    private static CompletableFuture<UxmOutcome> completed(UxmOutcome outcome) {
        return CompletableFuture.completedFuture(outcome);
    }

    private static UxmOutcome noSuchWorld(UxmLocation where) {
        return UxmOutcome.failed(UxmFailure.NOT_FOUND, "no loaded world is named " + where.world());
    }

    private static UxmOutcome refusal(NpcError error) {
        return switch (error) {
            case NOT_FOUND -> UxmOutcome.failed(UxmFailure.NOT_FOUND, "no npc goes by that name, in that world");
            case NAME_TAKEN -> UxmOutcome.failed(UxmFailure.ALREADY_EXISTS, "an npc already goes by that name");
            case LIMIT_REACHED -> UxmOutcome.failed(UxmFailure.REFUSED, "the actor is at their npc limit");
            case SKIN_ONLY_PLAYER ->
                UxmOutcome.failed(UxmFailure.REFUSED, "only a fake-player npc wears a skin, and this one is not one");
            default -> UxmOutcome.failed(UxmFailure.REFUSED, "the npc module refused it: " + error.name());
        };
    }

    /** One NPC write, named so the nine call sites that end in a modelled result read the same. */
    @FunctionalInterface
    private interface Write {
        Result<Unit, NpcError> apply(PlayerRef actor, NpcName name);
    }

    /** A write that answers for itself, for the verb whose refusal the domain does not model. */
    @FunctionalInterface
    private interface Answer {
        UxmOutcome apply(PlayerRef actor, NpcName name);
    }
}
