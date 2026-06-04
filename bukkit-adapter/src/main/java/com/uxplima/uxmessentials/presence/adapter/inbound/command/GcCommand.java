package com.uxplima.uxmessentials.presence.adapter.inbound.command;

import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.presence.adapter.PresenceServices;
import com.uxplima.uxmessentials.presence.application.PresenceMessageKey;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /gc} (aliases {@code /lag}, {@code /tps}, {@code /mem}, {@code uxmessentials.gc.use}): a one-line
 * server-health read-out — the one-minute TPS, process uptime, heap memory (used/free/max) and the loaded chunk
 * and entity totals across every world. The staff diagnostic EssentialsX surfaces under the same name; it sits
 * with the presence context's other server/player info reads ({@code /list}, {@code /whois}, {@code /realname}).
 * A pure read: no use case, no state mutation, just a snapshot of the live runtime and one resolved reply, so
 * the console may run it too.
 */
@NullMarked
public final class GcCommand extends PresenceCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.gc.use";
    private static final long BYTES_PER_MIB = 1024L * 1024L;
    private static final double MAX_TPS = 20.0;

    public GcCommand(PresenceServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("gc")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::show)
                .build();
    }

    @Override
    public List<String> aliases() {
        return List.of("lag", "tps", "mem");
    }

    @Override
    public String description() {
        return "Show server health: TPS, uptime and memory.";
    }

    private int show(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        feedback.send(sender, PresenceMessageKey.GC_RESULT, health());
        return Command.SINGLE_SUCCESS;
    }

    /** The live runtime snapshot the result line renders: TPS, memory in MiB, world totals and uptime parts. */
    private static Map<String, String> health() {
        Runtime runtime = Runtime.getRuntime();
        long maxMib = runtime.maxMemory() / BYTES_PER_MIB;
        long usedMib = (runtime.totalMemory() - runtime.freeMemory()) / BYTES_PER_MIB;
        long freeMib = maxMib - usedMib;
        long uptimeSeconds = ManagementFactory.getRuntimeMXBean().getUptime() / 1000L;
        WorldTotals totals = worldTotals();
        return Map.ofEntries(
                Map.entry("tps1m", oneDecimal(Math.min(Bukkit.getServer().getTPS()[0], MAX_TPS))),
                Map.entry("memUsed", String.valueOf(usedMib)),
                Map.entry("memFree", String.valueOf(freeMib)),
                Map.entry("memMax", String.valueOf(maxMib)),
                Map.entry("chunks", String.valueOf(totals.chunks())),
                Map.entry("entities", String.valueOf(totals.entities())),
                Map.entry("hours", String.valueOf(uptimeSeconds / 3600L)),
                Map.entry("minutes", String.valueOf((uptimeSeconds % 3600L) / 60L)),
                Map.entry("seconds", String.valueOf(uptimeSeconds % 60L)));
    }

    /** TPS rounded to a single decimal place without locale-sensitive number formatting. */
    private static String oneDecimal(double value) {
        long tenths = Math.round(value * 10.0);
        return (tenths / 10L) + "." + (tenths % 10L);
    }

    private static WorldTotals worldTotals() {
        long chunks = 0L;
        long entities = 0L;
        for (World world : Bukkit.getWorlds()) {
            chunks += world.getLoadedChunks().length;
            entities += world.getEntities().size();
        }
        return new WorldTotals(chunks, entities);
    }

    private record WorldTotals(long chunks, long entities) {}
}
