package com.uxplima.uxmessentials.itemworld.adapter.outbound;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.itemworld.domain.ItemworldMessageKey;
import com.uxplima.uxmessentials.playerstate.adapter.outbound.OfflinePlayerStorage;
import com.uxplima.uxmessentials.playerstate.adapter.outbound.playerdata.ContainerLayout;
import com.uxplima.uxmessentials.playerstate.adapter.outbound.playerdata.OfflineSnapshot;
import com.uxplima.uxmessentials.playerstate.adapter.outbound.playerdata.SaveOutcome;
import com.uxplima.uxmessentials.playerstate.adapter.outbound.playerdata.SaveResult;
import com.uxplima.uxmessentials.playerstate.domain.PlayerstateMessageKey;
import com.uxplima.uxmessentials.shared.domain.message.MessageKey;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The inventory and ender chest a player who is not on this server left in their data file, for the item commands
 * that copy or clear one. Reads and writes go through the same storage {@code /invsee} uses, so a save never writes
 * over a slot that changed since it was read, and a player joining owns their file.
 *
 * <p>A container is used only when every item in it was understood and the file was written by this server's version:
 * an item this server cannot read would be dropped from a copy, and cleared without anyone having seen it.
 *
 * <p>Every method touches the disk: call it off the tick.
 */
@NullMarked
public final class OfflineContainers {

    private final Supplier<OfflinePlayerStorage> storage;

    /** @param storage the storage now in use, read at each call since the module that owns it may be reloaded */
    public OfflineContainers(Supplier<OfflinePlayerStorage> storage) {
        this.storage = Objects.requireNonNull(storage, "storage");
    }

    /** What reading or clearing a stored container came to. */
    public enum Outcome {
        DONE(null),
        PRESENT(PlayerstateMessageKey.OFFLINE_JOINING),
        NO_DATA(PlayerstateMessageKey.OFFLINE_NO_DATA),
        NOT_UNDERSTOOD(ItemworldMessageKey.OFFLINE_NOT_UNDERSTOOD),
        CHANGED(ItemworldMessageKey.OFFLINE_CHANGED),
        READ_FAILED(PlayerstateMessageKey.OFFLINE_READ_FAILED),
        SAVE_FAILED(ItemworldMessageKey.OFFLINE_SAVE_FAILED),
        UNAVAILABLE(PlayerstateMessageKey.OFFLINE_UNAVAILABLE);

        private final @Nullable MessageKey problem;

        Outcome(@Nullable MessageKey problem) {
            this.problem = problem;
        }

        /** The line that tells the sender why nothing was done, with the player as {@code {player}}; empty for done. */
        public Optional<MessageKey> problem() {
            return Optional.ofNullable(problem);
        }
    }

    /** A stored container as read: its items when the read was done, otherwise why it was not. */
    public static final class Read {

        private final Outcome outcome;
        private final @Nullable ItemStack[] items;

        Read(Outcome outcome, @Nullable ItemStack[] items) {
            this.outcome = Objects.requireNonNull(outcome, "outcome");
            this.items = items.clone();
        }

        /** {@link Outcome#DONE} when {@link #items()} holds the container, otherwise why it does not. */
        public Outcome outcome() {
            return outcome;
        }

        /** One item per slot, {@code null} for an empty slot; empty unless the read was done. */
        public @Nullable ItemStack[] items() {
            return items.clone();
        }
    }

    /** Read {@code player}'s stored container, for a copy. */
    public Read read(UUID player, ContainerLayout layout) {
        OfflineSnapshot<ItemStack> snapshot = usable(player, layout);
        Outcome outcome = outcomeOf(snapshot);
        return new Read(outcome, outcome == Outcome.DONE ? snapshot.items() : new ItemStack[0]);
    }

    /** Empty every slot of {@code player}'s stored container. */
    public Outcome clear(UUID player, ContainerLayout layout) {
        OfflineSnapshot<ItemStack> snapshot = usable(player, layout);
        Outcome read = outcomeOf(snapshot);
        if (read != Outcome.DONE) {
            return read;
        }
        @Nullable ItemStack[] items = snapshot.items();
        Map<Integer, @Nullable ItemStack> emptied = new HashMap<>();
        for (int slot = 0; slot < items.length; slot++) {
            if (items[slot] != null) {
                emptied.put(slot, null);
            }
        }
        if (emptied.isEmpty()) {
            return Outcome.DONE;
        }
        // A clear that left some slots full would not be the clear that was asked for, so a conflict writes nothing.
        SaveResult saved = storage.get().save(player, layout, snapshot, emptied, conflicts -> false);
        return switch (saved.outcome()) {
            case SAVED -> saved.conflicts().isEmpty() ? Outcome.DONE : Outcome.CHANGED;
            case PLAYER_PRESENT -> Outcome.PRESENT;
            case NO_DATA -> Outcome.NO_DATA;
            case CHANGED -> Outcome.CHANGED;
            case FAILED -> Outcome.SAVE_FAILED;
            case UNAVAILABLE -> Outcome.UNAVAILABLE;
        };
    }

    private OfflineSnapshot<ItemStack> usable(UUID player, ContainerLayout layout) {
        return storage.get().read(Objects.requireNonNull(player, "player"), Objects.requireNonNull(layout, "layout"));
    }

    private static Outcome outcomeOf(OfflineSnapshot<ItemStack> snapshot) {
        Optional<SaveOutcome> problem = snapshot.problem();
        if (problem.isPresent()) {
            return switch (problem.get()) {
                case PLAYER_PRESENT -> Outcome.PRESENT;
                case NO_DATA -> Outcome.NO_DATA;
                case UNAVAILABLE -> Outcome.UNAVAILABLE;
                case SAVED, CHANGED, FAILED -> Outcome.READ_FAILED;
            };
        }
        return snapshot.editable() ? Outcome.DONE : Outcome.NOT_UNDERSTOOD;
    }
}
