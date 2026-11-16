package com.uxplima.uxmessentials.teleport.adapter.outbound.nms;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.teleport.application.port.LogoutPositions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.util.Util;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * The {@link LogoutPositions} the server itself keeps: the position saved in an offline player's
 * {@code players/data/<uuid>.dat} when they left. The file is read and written with the server's own NBT codecs,
 * and the world is the Bukkit world uid the server stores beside the position and reads first on login.
 *
 * <p>The directory is resolved once, on the thread that builds this, so the async lane only touches files. A write
 * goes to a temporary file that then replaces the real one, the way the server saves a player, so a login that
 * reads the file meanwhile sees the old position or the new one, never half a file. The caller writes only for a
 * player who is not online here; a player whose data the server holds in memory would overwrite it on leaving.
 *
 * <p>Like the offline inventory storage, this class is deliberately not {@code @NullMarked}: the NMS surface it
 * touches is unannotated. It is reached only through the null-marked {@link LogoutPositions} port.
 */
public final class PlayerDataLogoutPositions implements LogoutPositions {

    private static final String POSITION = "Pos";
    private static final String ROTATION = "Rotation";
    private static final String MOTION = "Motion";
    private static final String FALL_DISTANCE = "fall_distance";
    private static final String WORLD_MOST = "WorldUUIDMost";
    private static final String WORLD_LEAST = "WorldUUIDLeast";
    private static final String DIMENSION = "Dimension";
    private static final String VEHICLE = "RootVehicle";

    private final Path directory;
    private final Logger log;

    public PlayerDataLogoutPositions(Logger log) {
        this.log = Objects.requireNonNull(log, "log");
        this.directory = ((CraftServer) Bukkit.getServer()).getServer().getWorldPath(LevelResource.PLAYER_DATA_DIR);
    }

    @Override
    public Optional<SavedSpot> read(UUID player) {
        Path file = dataFile(player);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
            Optional<Vec3> position = root.read(POSITION, Vec3.CODEC);
            Optional<Long> most = root.getLong(WORLD_MOST);
            Optional<Long> least = root.getLong(WORLD_LEAST);
            if (position.isEmpty() || most.isEmpty() || least.isEmpty()) {
                return Optional.empty();
            }
            Vec2 rotation = root.read(ROTATION, Vec2.CODEC).orElse(Vec2.ZERO);
            Vec3 at = position.get();
            return Optional.of(
                    new SavedSpot(new UUID(most.get(), least.get()), at.x, at.y, at.z, rotation.x, rotation.y));
        } catch (IOException ex) {
            log.error("Failed to read the saved position of " + player, ex);
            return Optional.empty();
        }
    }

    @Override
    public boolean write(UUID player, Position at) {
        Objects.requireNonNull(at, "at");
        Path file = dataFile(player);
        if (!Files.isRegularFile(file)) {
            return false;
        }
        try {
            CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
            root.store(POSITION, Vec3.CODEC, new Vec3(at.x(), at.y(), at.z()));
            root.store(ROTATION, Vec2.CODEC, new Vec2(at.yaw(), at.pitch()));
            root.store(MOTION, Vec3.CODEC, Vec3.ZERO);
            root.putDouble(FALL_DISTANCE, 0.0);
            root.putLong(WORLD_MOST, at.world().uid().getMostSignificantBits());
            root.putLong(WORLD_LEAST, at.world().uid().getLeastSignificantBits());
            // The dimension is only read when no world uid is stored, and a saved vehicle would carry the player back
            // to where they left; neither may contradict the new position.
            root.remove(DIMENSION);
            root.remove(VEHICLE);
            Path written = Files.createTempFile(directory, player + "-", ".dat");
            NbtIo.writeCompressed(root, written);
            // The server's own replace: the old file becomes the backup a login falls back to while the new one
            // is moved into place, and is put back if that move fails.
            if (Util.safeReplaceOrMoveFile(file, written, directory.resolve(player + ".dat_old"), false)) {
                return true;
            }
            Files.deleteIfExists(written);
            return false;
        } catch (IOException ex) {
            log.error("Failed to write the saved position of " + player, ex);
            return false;
        }
    }

    private Path dataFile(UUID player) {
        return directory.resolve(Objects.requireNonNull(player, "player") + ".dat");
    }
}
