/**
 * Application layer of the staff bounded context: the {@code StaffModule} feature-module declaration, the
 * {@code EnterStaffMode}/{@code ExitStaffMode} use cases (the item-loss-safe commit-before-swap and
 * restore-then-delete toggle of staff mode), {@code SendStaffChat} (the staff-channel fan-out, mirroring
 * {@code HelpOp}), the {@code StaffNotifier} (MessageKey resolution + delivery), the
 * {@code StaffCommandSurface} (platform-neutral command specs for {@code /staffmode} and
 * {@code /staffchat}+{@code /sc}), and the {@code StaffMessageKey} catalog. Use cases orchestrate the domain
 * and the existing presence/playerstate/messaging modules through the outbound ports in
 * {@code application/port}; no Bukkit, Paper, Kyori, or logging type appears here, and no use case applies a
 * sanction.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.staff.application;
