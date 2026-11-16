package com.uxplima.uxmessentials.playerstate.adapter.outbound.nms;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.playerstate.adapter.outbound.playerdata.ItemCodec;
import com.uxplima.uxmessentials.playerstate.adapter.outbound.playerdata.TagPayload;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * The {@link ItemCodec} that reads and writes a stored item with the very codec the server persists items with, so
 * an item written here is one the server reads back on login exactly as it would its own. This and finding the
 * player data directory are the playerstate context's only uses of the server's internals.
 *
 * <p>This class is deliberately not {@code @NullMarked}: the internals it touches are unannotated, so it stays outside
 * the null analysis, reached only through the null-marked {@link ItemCodec}.
 */
public final class NmsItemCodec implements ItemCodec {

    /** An unnamed root compound's header: the compound type and a name of length zero. */
    private static final byte[] ROOT_HEADER = {Tag.TAG_COMPOUND, 0, 0};

    private final MinecraftServer server;

    private NmsItemCodec(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    /**
     * The codec over this server. Called on the thread that wires the module; throws a {@link LinkageError} when the
     * server's internals moved, which the caller turns into offline storage being unavailable.
     */
    public static ItemCodec ofServer() {
        return new NmsItemCodec(((CraftServer) Bukkit.getServer()).getServer());
    }

    /** This server's player data directory. Throws a {@link LinkageError} when the server's internals moved. */
    public static Path playerDataDirectory() {
        return ((CraftServer) Bukkit.getServer()).getServer().getWorldPath(LevelResource.PLAYER_DATA_DIR);
    }

    @Override
    public int dataVersion() {
        return SharedConstants.getCurrentVersion().dataVersion().version();
    }

    @Override
    public Optional<ItemStack> decode(TagPayload compound) throws IOException {
        byte[] payload = compound.bytes();
        byte[] whole = Arrays.copyOf(ROOT_HEADER, ROOT_HEADER.length + payload.length);
        System.arraycopy(payload, 0, whole, ROOT_HEADER.length, payload.length);
        CompoundTag tag = NbtIo.read(new DataInputStream(new ByteArrayInputStream(whole)));
        return net.minecraft.world.item.ItemStack.CODEC
                .parse(ops(), tag)
                .result()
                .filter(stack -> !stack.isEmpty())
                .map(CraftItemStack::asBukkitCopy);
    }

    @Override
    public TagPayload encode(ItemStack item) throws IOException {
        net.minecraft.world.item.ItemStack stack = CraftItemStack.asNMSCopy(item);
        if (stack.isEmpty()) {
            throw new IOException("an empty item has no stored form");
        }
        Tag tag = net.minecraft.world.item.ItemStack.CODEC
                .encodeStart(ops(), stack)
                .getOrThrow(IOException::new);
        if (!(tag instanceof CompoundTag compound)) {
            throw new IOException("an item encoded to tag type " + tag.getId() + ", not a compound");
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            NbtIo.write(compound, out);
        }
        byte[] whole = bytes.toByteArray();
        if (whole.length < ROOT_HEADER.length
                || !Arrays.equals(whole, 0, ROOT_HEADER.length, ROOT_HEADER, 0, ROOT_HEADER.length)) {
            throw new IOException("the server wrote an item compound with an unexpected header");
        }
        return new TagPayload(Arrays.copyOfRange(whole, ROOT_HEADER.length, whole.length));
    }

    private RegistryOps<Tag> ops() {
        return server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
    }
}
