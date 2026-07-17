/**
 * The invrollback context's {@code /invrestore} GUI surface: the engine-backed list of a target's snapshots
 * ({@link com.uxplima.uxmessentials.invrollback.adapter.inbound.gui.SnapshotListView}), the read-only snapshot
 * preview raw-inventory leaf and its click listener
 * ({@link com.uxplima.uxmessentials.invrollback.adapter.inbound.gui.SnapshotPreviewView} /
 * {@link com.uxplima.uxmessentials.invrollback.adapter.inbound.gui.SnapshotPreviewHolder} /
 * {@link com.uxplima.uxmessentials.invrollback.adapter.inbound.gui.SnapshotPreviewListener}), and the
 * {@link com.uxplima.uxmessentials.invrollback.adapter.inbound.gui.SnapshotRestorer} that applies a chosen
 * snapshot to a live inventory after safety-snapshotting the pre-restore state.
 */
@NullMarked
package com.uxplima.uxmessentials.invrollback.adapter.inbound.gui;

import org.jspecify.annotations.NullMarked;
