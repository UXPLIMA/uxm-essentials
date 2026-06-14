/**
 * The staff context's sealed domain-event family: the {@code StaffEvent} seal and its three concrete
 * records, {@code StaffModeEntered} and {@code StaffModeExited} (a staff member flipped staff mode, after
 * the durable loadout commit/restore) and {@code StaffChatSent} (a line was fanned out on the staff
 * channel). Every concrete event is a record (value equality backs the in-process bus and equality-based
 * test consumers); the adapter bridges each to a Bukkit event. Nothing here is a sanction.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.staff.domain.event;
