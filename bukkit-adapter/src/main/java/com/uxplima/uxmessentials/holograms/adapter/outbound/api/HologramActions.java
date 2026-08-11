package com.uxplima.uxmessentials.holograms.adapter.outbound.api;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import com.uxplima.uxmessentials.api.action.UxmFailure;
import com.uxplima.uxmessentials.api.action.UxmHologramsActions;
import com.uxplima.uxmessentials.api.action.UxmOutcome;
import com.uxplima.uxmessentials.api.view.UxmLocation;
import com.uxplima.uxmessentials.holograms.application.AddHologramLine;
import com.uxplima.uxmessentials.holograms.application.CreateHologram;
import com.uxplima.uxmessentials.holograms.application.DeleteHologram;
import com.uxplima.uxmessentials.holograms.application.MoveHologram;
import com.uxplima.uxmessentials.holograms.application.RemoveHologramLine;
import com.uxplima.uxmessentials.holograms.application.SetHologramClickCommand;
import com.uxplima.uxmessentials.holograms.application.SetHologramLine;
import com.uxplima.uxmessentials.holograms.domain.HologramError;
import com.uxplima.uxmessentials.holograms.domain.HologramLine;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.ApiValues;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.AsyncActions;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.application.port.WorldLookup;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.jspecify.annotations.NullMarked;

/**
 * The published hologram writes, over the same use cases {@code /hologram} runs.
 *
 * <p>So a hologram put up from a plugin is an ordinary hologram: it is stored in the same table, it renders the
 * same way, and an operator can edit or delete it with the commands afterwards rather than having to go back to
 * whatever put it there.
 *
 * <p>Line numbers count from one here and from zero inside; the translation happens once, on the way in, because
 * one is what the commands show and what a hologram reads as on screen.
 *
 * <p>Everything runs on a worker thread: each write is a database write, and the renderer takes itself to the right
 * thread to draw the result.
 */
@NullMarked
public final class HologramActions implements UxmHologramsActions {

    private final CreateHologram creator;
    private final DeleteHologram remove;
    private final MoveHologram mover;
    private final AddHologramLine lineAdder;
    private final SetHologramLine lineSetter;
    private final RemoveHologramLine lineRemover;
    private final SetHologramClickCommand clickCommands;
    private final PlayerLookup players;
    private final WorldLookup worlds;
    private final Scheduler scheduler;

    public HologramActions(
            CreateHologram creator,
            DeleteHologram remove,
            MoveHologram mover,
            AddHologramLine lineAdder,
            SetHologramLine lineSetter,
            RemoveHologramLine lineRemover,
            SetHologramClickCommand clickCommands,
            PlayerLookup players,
            WorldLookup worlds,
            Scheduler scheduler) {
        this.creator = Objects.requireNonNull(creator, "creator");
        this.remove = Objects.requireNonNull(remove, "remove");
        this.mover = Objects.requireNonNull(mover, "mover");
        this.lineAdder = Objects.requireNonNull(lineAdder, "lineAdder");
        this.lineSetter = Objects.requireNonNull(lineSetter, "lineSetter");
        this.lineRemover = Objects.requireNonNull(lineRemover, "lineRemover");
        this.clickCommands = Objects.requireNonNull(clickCommands, "clickCommands");
        this.players = Objects.requireNonNull(players, "players");
        this.worlds = Objects.requireNonNull(worlds, "worlds");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public CompletableFuture<UxmOutcome> create(UUID actorId, String name, UxmLocation where, String firstLine) {
        HologramLine line = line(firstLine);
        return at(
                where, position -> named(actorId, name, (actor, holo) -> creator.create(actor, holo, position, line)));
    }

    @Override
    public CompletableFuture<UxmOutcome> delete(UUID actorId, String name) {
        return named(actorId, name, remove::delete);
    }

    @Override
    public CompletableFuture<UxmOutcome> move(UUID actorId, String name, UxmLocation where) {
        return at(where, position -> named(actorId, name, (actor, holo) -> mover.move(actor, holo, position)));
    }

    @Override
    public CompletableFuture<UxmOutcome> addLine(UUID actorId, String name, String text) {
        HologramLine line = line(text);
        return named(actorId, name, (actor, holo) -> lineAdder.add(actor, holo, line));
    }

    @Override
    public CompletableFuture<UxmOutcome> setLine(UUID actorId, String name, int line, String text) {
        HologramLine replacement = line(text);
        int index = zeroBased(line);
        return named(actorId, name, (actor, holo) -> lineSetter.set(actor, holo, index, replacement));
    }

    @Override
    public CompletableFuture<UxmOutcome> removeLine(UUID actorId, String name, int line) {
        int index = zeroBased(line);
        return named(actorId, name, (actor, holo) -> lineRemover.remove(actor, holo, index));
    }

    @Override
    public CompletableFuture<UxmOutcome> setClickCommand(UUID actorId, String name, String command) {
        Objects.requireNonNull(command, "command");
        String bare = command.startsWith("/") ? command.substring(1) : command;
        if (bare.isBlank()) {
            throw new IllegalArgumentException("a click command must not be blank: call clearClickCommand instead");
        }
        return named(actorId, name, (actor, holo) -> clickCommands.set(actor, holo, bare));
    }

    @Override
    public CompletableFuture<UxmOutcome> clearClickCommand(UUID actorId, String name) {
        return named(actorId, name, (actor, holo) -> clickCommands.set(actor, holo, null));
    }

    /** Resolve the world before scheduling anything: a world nobody loaded is an answer, not work. */
    private CompletableFuture<UxmOutcome> at(
            UxmLocation where, Function<Position, CompletableFuture<UxmOutcome>> body) {
        Objects.requireNonNull(where, "where");
        return ApiValues.position(worlds, where)
                .map(body)
                .orElseGet(() -> CompletableFuture.completedFuture(
                        UxmOutcome.failed(UxmFailure.NOT_FOUND, "no loaded world is named " + where.world())));
    }

    /** Check the name's shape and resolve the actor, then run {@code body} on a worker thread. */
    private CompletableFuture<UxmOutcome> named(UUID actorId, String name, Write body) {
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(name, "name");
        Optional<HologramName> holo = HologramQueries.parse(name);
        if (holo.isEmpty()) {
            return CompletableFuture.completedFuture(UxmOutcome.failed(
                    UxmFailure.REFUSED,
                    "a hologram name is at most " + HologramName.MAX_LENGTH + " characters: " + name));
        }
        PlayerRef actor = ApiValues.subject(players, actorId);
        return AsyncActions.perform(scheduler, () -> {
            Result<Unit, HologramError> done = body.apply(actor, holo.get());
            return done.isErr() ? refusal(done.errorOrThrow()) : UxmOutcome.ok();
        });
    }

    /** Line text as the domain stores it, rejected here rather than three stack frames deeper. */
    private static HologramLine line(String text) {
        Objects.requireNonNull(text, "text");
        return HologramLine.of(text);
    }

    private static int zeroBased(int line) {
        if (line < 1) {
            throw new IllegalArgumentException("hologram lines count from one, and " + line + " does not");
        }
        return line - 1;
    }

    private static UxmOutcome refusal(HologramError error) {
        return switch (error) {
            case NOT_FOUND -> UxmOutcome.failed(UxmFailure.NOT_FOUND, "no hologram goes by that name");
            case NAME_TAKEN -> UxmOutcome.failed(UxmFailure.ALREADY_EXISTS, "a hologram already goes by that name");
            case LINE_INDEX_OUT_OF_RANGE ->
                UxmOutcome.failed(UxmFailure.NOT_FOUND, "the hologram has no line with that number");
            case WOULD_BE_EMPTY ->
                UxmOutcome.failed(UxmFailure.REFUSED, "a hologram keeps at least one line: delete it instead");
            case NOT_TEXT_HOLOGRAM -> UxmOutcome.failed(UxmFailure.REFUSED, "that hologram is not made of text");
            default -> UxmOutcome.failed(UxmFailure.REFUSED, "the hologram module refused it: " + error.name());
        };
    }

    /** One hologram write, named so the nine call sites read the same. */
    @FunctionalInterface
    private interface Write {
        Result<Unit, HologramError> apply(PlayerRef actor, HologramName name);
    }
}
