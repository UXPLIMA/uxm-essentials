package com.uxplima.uxmessentials.warps.adapter.inbound.gui;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.warps.application.port.WarpCategoryRepository;
import com.uxplima.uxmessentials.warps.domain.WarpCategory;
import org.jspecify.annotations.NullMarked;

/**
 * Writes warp categories on the async lane, one category's writes one after another, for the category menus.
 *
 * <p>A write goes to the database, so it cannot run on the click's thread. An edit reads the category as stored when
 * its turn comes and changes that, so two quick edits of one category both land, and an edit of a category deleted
 * meanwhile does not bring it back. Once the write has finished, what the operator sees next runs on their own thread:
 * the category as stored, the manager when it is gone, and a failure line first when the write threw. The failure
 * still reaches the async lane's log.
 */
@NullMarked
public final class WarpCategoryEdits {

    private final WarpCategoryRepository categories;
    private final Scheduler scheduler;
    private final SerialWarpEdits queue;

    public WarpCategoryEdits(WarpCategoryRepository categories, Scheduler scheduler) {
        this.categories = Objects.requireNonNull(categories, "categories");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.queue = new SerialWarpEdits(scheduler);
    }

    /** Save {@code category} as it is, a new one or one the operator replaces whole. */
    void create(PlayerRef viewer, WarpCategory category, After after) {
        write(viewer, category.id(), after, () -> {
            categories.save(category);
            return Optional.of(category);
        });
    }

    /** Change the stored category {@code id}; nothing is written when it is gone. */
    void edit(PlayerRef viewer, String id, UnaryOperator<WarpCategory> change, After after) {
        write(viewer, id, after, () -> {
            Optional<WarpCategory> changed = categories.find(id).map(change);
            changed.ifPresent(categories::save);
            return changed;
        });
    }

    /** Delete the category {@code id}. */
    void delete(PlayerRef viewer, String id, After after) {
        write(viewer, id, after, () -> {
            categories.delete(id);
            return Optional.empty();
        });
    }

    private void write(PlayerRef viewer, String id, After after, Supplier<Optional<WarpCategory>> work) {
        queue.run("category:" + id, () -> {
            Optional<WarpCategory> shown = Optional.empty();
            boolean failed = true;
            try {
                shown = work.get();
                failed = false;
            } finally {
                if (failed) {
                    shown = stored(id);
                }
                answer(viewer, after, shown, failed);
            }
        });
    }

    /** The category as stored, for the menu a failed write reopens; nothing when even the read fails. */
    private Optional<WarpCategory> stored(String id) {
        try {
            return categories.find(id);
        } catch (RuntimeException unreadable) {
            return Optional.empty();
        }
    }

    private void answer(PlayerRef viewer, After after, Optional<WarpCategory> shown, boolean failed) {
        scheduler.onEntity(viewer, () -> {
            if (failed) {
                after.failed();
            }
            shown.ifPresentOrElse(after::show, after::gone);
        });
    }

    /** What the operator sees once a write has finished, run on their own thread. */
    interface After {

        /** Reopen the category's settings with it as now stored. */
        void show(WarpCategory category);

        /** The category is not stored (deleted, or never written): go back to the manager. */
        void gone();

        /** Tell the operator the write failed; {@link #show} or {@link #gone} follows. */
        void failed();
    }
}
