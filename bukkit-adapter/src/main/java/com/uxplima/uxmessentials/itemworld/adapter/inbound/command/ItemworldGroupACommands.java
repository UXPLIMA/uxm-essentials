package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;

import java.util.ArrayList;
import java.util.List;

import com.uxplima.uxmessentials.itemworld.adapter.ItemworldServices;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import org.jspecify.annotations.NullMarked;

/**
 * Builds the inbound Brigadier surface for itemworld groups A — item utils, virtual workstations, and cleanup
 * (docs/10-feature-modules.md §15.10) — as {@link CommandRegistration}s over the constructed
 * {@link ItemworldServices}. Collected in one greppable table so the literal/permission pairing matches the
 * permissions reference and the kernel's {@code ItemworldCommandSurface}; the plugin's
 * {@code LifecycleEvents.COMMANDS} handler registers each on the next fire. The remaining groups (powertool,
 * mob/entity, time/weather aliases, admin-fun) land with the second itemworld adapter and join this list in
 * the context wiring.
 *
 * <p>Every command here gates on its sub-feature group plus its per-command disable through
 * {@link ItemworldCommandSupport#enabled} before any mutation, validates its inputs at the boundary, and (for
 * the abusable bulk {@code /give}) audit-logs through {@link ItemworldServices#audit()}. The {@code /repair},
 * {@code /repairall}, {@code /hat} and {@code /more} verbs are owned here, not in playerstate (§15.6), so they
 * register here and the two modules do not double-register.
 */
@NullMarked
public final class ItemworldGroupACommands {

    private ItemworldGroupACommands() {}

    /** Every itemworld group-A command, in surface order grouped by sub-feature group. */
    public static List<CommandRegistration> all(ItemworldServices services) {
        List<CommandRegistration> commands = new ArrayList<>();
        addItemUtils(commands, services);
        addWorkstations(commands, services);
        addCleanup(commands, services);
        return List.copyOf(commands);
    }

    private static void addItemUtils(List<CommandRegistration> commands, ItemworldServices services) {
        commands.add(new GiveCommand(services));
        commands.add(new ItemCommand(services));
        commands.add(new ItemNameCommand(services));
        commands.add(new ItemLoreCommand(services));
        commands.add(new ItemFlagCommand(services));
        commands.add(new SkullCommand(services));
        commands.add(new MoreCommand(services));
        commands.add(new RepairCommand(services));
        commands.add(new RepairAllCommand(services));
        commands.add(new EnchantCommand(services));
        commands.add(new HatCommand(services));
        commands.add(new ItemDbCommand(services));
    }

    private static void addWorkstations(List<CommandRegistration> commands, ItemworldServices services) {
        for (Workstation station : Workstation.values()) {
            commands.add(new WorkstationCommand(station, services));
        }
    }

    private static void addCleanup(List<CommandRegistration> commands, ItemworldServices services) {
        commands.add(new DisposalCommand(services));
        commands.add(new CondenseCommand(services));
    }
}
