package com.uxplima.uxmessentials.playerstate.adapter.outbound.playerdata;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

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

    private final PlayerDataFiles files;
    private final ItemCodec codec;
    private final Logger log;

    public FileOfflinePlayerStorage(PlayerDataFiles files, ItemCodec codec, Logger log) {
        this.files = Objects.requireNonNull(files, "files");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public OfflineSnapshot read(UUID player, ContainerLayout layout) {
        try {
            PlayerDataFiles.Read read = files.read(player, layout);
            if (read instanceof PlayerDataFiles.Refused(SaveOutcome reason)) {
                return OfflineSnapshot.unreadable(reason, layout);
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
            return OfflineSnapshot.unreadable(SaveOutcome.FAILED, layout);
        } catch (LinkageError moved) {
            log.error("The server's internals for offline player data changed; " + player + " was not read", moved);
            return OfflineSnapshot.unreadable(SaveOutcome.UNAVAILABLE, layout);
        }
    }

    @Override
    public SaveOutcome save(UUID player, ContainerLayout layout, OfflineSnapshot base, @Nullable ItemStack[] contents) {
        try {
            List<SlotWrite> writes = new ArrayList<>();
            for (int slot : base.changedSlots(contents)) {
                ItemStack item = slot < contents.length ? contents[slot] : null;
                writes.add(
                        new SlotWrite(slot, item == null || OfflineSnapshot.nothing(item) ? null : codec.encode(item)));
            }
            if (writes.isEmpty()) {
                return SaveOutcome.SAVED;
            }
            return files.write(player, layout, base.stored(), writes);
        } catch (IOException | RuntimeException failed) {
            log.error("Failed to save offline player data for " + player + "; the file is unchanged", failed);
            return SaveOutcome.FAILED;
        } catch (LinkageError moved) {
            log.error("The server's internals for offline player data changed; " + player + " was not saved", moved);
            return SaveOutcome.FAILED;
        }
    }
}
