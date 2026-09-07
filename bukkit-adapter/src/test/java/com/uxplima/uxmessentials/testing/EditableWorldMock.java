package com.uxplima.uxmessentials.testing;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.bukkit.Location;
import org.bukkit.TreeType;

import org.jspecify.annotations.Nullable;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/**
 * A {@link WorldMock} that accepts a spawn point carrying a yaw, and that grows a tree.
 *
 * <p>MockBukkit declares both operations and throws {@code UnimplementedOperationException} from them. That
 * exception extends {@code TestAbortedException}, so JUnit records a test that reaches one as skipped rather
 * than failed, and every assertion written after the call is never evaluated. The world is where those two
 * holes sit, so the doubles live here rather than in each test.
 *
 * <p>{@code setSpawnLocation(int, int, int)} without a yaw is implemented upstream, and the yaw-carrying
 * overload is delegated onto the {@code Location} form, which is also implemented, so the spawn a caller
 * writes is the spawn {@code getSpawnLocation()} reads back, yaw included.
 *
 * <p>A tree is recorded rather than built. A mock world has no block generator, so what a caller can be held
 * to is that it asked for the right tree at the right place; whether the sapling had room is the server's
 * business and not this plugin's.
 */
public class EditableWorldMock extends WorldMock {

    /** Every {@code generateTree} call, in order, as (location, type). */
    private final List<GrownTree> grownTrees = new ArrayList<>();

    /** What {@code generateTree} answers: a real server says false when the sapling has no room. */
    private boolean treesGrow = true;

    /**
     * The spawn as last set through the yaw-carrying overload. MockBukkit's own spawn field keeps only the
     * block coordinates, so the yaw would be lost between the write and the read.
     */
    private @Nullable Location spawn;

    /** A world under {@code server}, named {@code name} and registered with it. */
    public static EditableWorldMock addTo(ServerMock server, String name) {
        EditableWorldMock world = new EditableWorldMock();
        world.setName(name);
        server.addWorld(world);
        return world;
    }

    @Override
    public boolean setSpawnLocation(int x, int y, int z, float yaw) {
        this.spawn = new Location(this, x, y, z, yaw, 0.0f);
        return super.setSpawnLocation(x, y, z);
    }

    /** The spawn point including its yaw, which is the whole reason the applier calls the four-argument form. */
    @Override
    public Location getSpawnLocation() {
        Location set = spawn;
        return set == null ? super.getSpawnLocation() : set.clone();
    }

    @Override
    public boolean generateTree(Location location, Random random, TreeType type) {
        grownTrees.add(new GrownTree(location.clone(), type));
        return treesGrow;
    }

    /** Every tree this world was asked to grow, in order. */
    public List<GrownTree> grownTrees() {
        return List.copyOf(grownTrees);
    }

    /** Make the next {@code generateTree} refuse, the way a server does when the sapling has no room. */
    public void refuseTrees() {
        this.treesGrow = false;
    }

    /** One {@code generateTree} call: where it was asked for, and which tree. */
    public record GrownTree(Location location, TreeType type) {}
}
