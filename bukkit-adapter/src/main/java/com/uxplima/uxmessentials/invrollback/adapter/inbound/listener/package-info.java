/**
 * The invrollback context's inbound listeners:
 * {@link com.uxplima.uxmessentials.invrollback.adapter.inbound.listener.SnapshotCaptureListener} freezes a
 * player's inventory on death and on logout, serializing it on the tick thread and hopping the DB write off the
 * tick thread through the {@code Scheduler.async} port.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.invrollback.adapter.inbound.listener;
