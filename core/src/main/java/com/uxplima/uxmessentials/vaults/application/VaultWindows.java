package com.uxplima.uxmessentials.vaults.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vaults.domain.Vault;
import com.uxplima.uxmessentials.vaults.domain.VaultContents;
import com.uxplima.uxmessentials.vaults.domain.VaultId;

/**
 * The vault windows currently open, one per vault. A vault lives in at most one window at a time: two windows seeded
 * from the same row and written back on close would let the later close restore what the earlier one had taken out,
 * an item dupe. A second viewer is refused.
 *
 * <p>The viewer already holding a vault may open it again (a second {@code /vault 2}, a click in the selector). Their
 * old window is taken over, not written back: the new window starts from the old window's live items and only the
 * window that finally closes is written. Writing the old window back separately, then seeding the new one from the
 * row read before, would put the same items in two places whenever the row changed in between, for example when the
 * vault was deleted and the old window's items were handed back.
 *
 * <p>The window handle {@code W} is the adapter's GUI; this class never looks inside it, it only asks the caller for
 * its live items when it is taken over.
 *
 * @param <W> the adapter's window type
 */
public final class VaultWindows<W> {

    private final Map<VaultId, Window<W>> open = new ConcurrentHashMap<>();

    /**
     * Claim {@code vault} for {@code window}, shown to {@code viewer}. Returns the items to fill the new window with:
     * the live items of the viewer's own window it takes over ({@code takeOver} reads and empties that window), or
     * else the vault's stored items. Empty when another viewer holds the vault, and then nothing is claimed.
     */
    public Optional<VaultContents> claim(
            PlayerRef viewer, PlayerRef owner, Vault vault, W window, Function<W, VaultContents> takeOver) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(vault, "vault");
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(takeOver, "takeOver");
        Window<W> fresh = new Window<>(viewer, owner, vault, window);
        AtomicReference<Window<W>> displaced = new AtomicReference<>();
        // The mapping function only swaps references, so no I/O runs while the map's bin is held.
        Window<W> holder = open.compute(vault.id(), (id, current) -> {
            if (current == null) {
                return fresh;
            }
            if (!current.viewer().uuid().equals(viewer.uuid())) {
                return current;
            }
            displaced.set(current);
            return fresh;
        });
        if (holder != fresh) {
            return Optional.empty();
        }
        Window<W> retired = displaced.get();
        return Optional.of(retired == null ? vault.contents() : takeOver.apply(retired.window()));
    }

    /**
     * Release {@code window}'s claim on {@code id}, returning the window to write back when it still held the claim.
     * Empty when the window was taken over or already released, and then nothing is to be written.
     */
    public Optional<Window<W>> release(VaultId id, W window) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(window, "window");
        AtomicReference<Window<W>> released = new AtomicReference<>();
        open.computeIfPresent(id, (key, current) -> {
            if (current.window() != window) {
                return current;
            }
            released.set(current);
            return null;
        });
        return Optional.ofNullable(released.get());
    }

    /** Release every claim, returning each window to write back; used when the module stops. */
    public List<Window<W>> releaseAll() {
        List<Window<W>> released = new ArrayList<>();
        for (Map.Entry<VaultId, Window<W>> entry : Map.copyOf(open).entrySet()) {
            if (open.remove(entry.getKey(), entry.getValue())) {
                released.add(entry.getValue());
            }
        }
        return released;
    }

    /** How many windows are open. */
    public int count() {
        return open.size();
    }

    /**
     * One open window.
     *
     * @param viewer who the window is shown to
     * @param owner whose vault it is
     * @param vault the vault as it was when the window opened
     * @param window the adapter's GUI
     * @param <W> the adapter's window type
     */
    public record Window<W>(PlayerRef viewer, PlayerRef owner, Vault vault, W window) {

        public Window {
            Objects.requireNonNull(viewer, "viewer");
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(vault, "vault");
            Objects.requireNonNull(window, "window");
        }

        /** True when staff opened another player's vault. */
        public boolean staffView() {
            return !viewer.uuid().equals(owner.uuid());
        }
    }
}
