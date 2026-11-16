package com.uxplima.uxmessentials.playerstate.adapter.outbound.playerdata;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.playerstate.adapter.outbound.OfflinePlayerStorage;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.Nullable;

/**
 * The {@link OfflinePlayerStorage} over the player data files: {@link PlayerDataFiles} reads and writes the file, the
 * {@link ItemCodec} turns its slots into items and back.
 *
 * <p>A container opens editable only when every item in it was understood and the file was written by this server's
 * version. An item this server cannot read would show as an empty slot, and putting something there would replace it
 * for good; data from an older version is converted by the server when the player next joins, and an item written
 * into it now in the current format would be converted a second time.
 */
public final class FileOfflinePlayerStorage implements OfflinePlayerStorage {

    private static final ItemStack[] NO_ITEMS = new ItemStack[0];

    private final PlayerDataFiles files;
    private final ItemCodec codec;
    private final Logger log;

    public FileOfflinePlayerStorage(PlayerDataFiles files, ItemCodec codec, Logger log) {
        this.files = Objects.requireNonNull(files, "files");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public OfflineSnapshot<ItemStack> read(UUID player, ContainerLayout layout) {
        try {
            PlayerDataFiles.Read read = files.read(player, layout);
            if (read instanceof PlayerDataFiles.Refused(SaveOutcome reason)) {
                return OfflineSnapshot.unreadable(reason, NO_ITEMS);
            }
            StoredSlots stored = ((PlayerDataFiles.Found) read).slots();
            @Nullable ItemStack[] items = new ItemStack[layout.size()];
            boolean understood = true;
            for (Map.Entry<Integer, TagPayload> slot : stored.filled().entrySet()) {
                Optional<ItemStack> item = codec.decode(slot.getValue());
                if (item.isPresent()) {
                    items[slot.getKey()] = item.get();
                } else {
                    understood = false;
                }
            }
            return OfflineSnapshot.found(items, stored, understood && stored.dataVersion() == codec.dataVersion());
        } catch (IOException | RuntimeException failed) {
            log.error("Failed to read offline player data for " + player, failed);
            return OfflineSnapshot.unreadable(SaveOutcome.FAILED, NO_ITEMS);
        } catch (LinkageError moved) {
            log.error("The server's internals for offline player data changed; " + player + " was not read", moved);
            return OfflineSnapshot.unreadable(SaveOutcome.UNAVAILABLE, NO_ITEMS);
        }
    }

    @Override
    public SaveResult save(
            UUID player,
            ContainerLayout layout,
            OfflineSnapshot<ItemStack> base,
            Map<Integer, @Nullable ItemStack> changes,
            Predicate<Set<Integer>> partly) {
        try {
            List<SlotWrite> writes = new ArrayList<>();
            for (Map.Entry<Integer, @Nullable ItemStack> change : changes.entrySet()) {
                ItemStack item = change.getValue();
                boolean empty = item == null || item.getType().isAir() || item.getAmount() <= 0;
                writes.add(new SlotWrite(change.getKey(), empty ? null : codec.encode(item)));
            }
            if (writes.isEmpty()) {
                return SaveResult.of(SaveOutcome.SAVED);
            }
            return files.write(player, layout, base.stored(), writes, partly);
        } catch (IOException | RuntimeException failed) {
            log.error("Failed to save offline player data for " + player + "; the file is unchanged", failed);
            return SaveResult.of(SaveOutcome.FAILED);
        } catch (LinkageError moved) {
            log.error("The server's internals for offline player data changed; " + player + " was not saved", moved);
            return SaveResult.of(SaveOutcome.FAILED);
        }
    }
}
