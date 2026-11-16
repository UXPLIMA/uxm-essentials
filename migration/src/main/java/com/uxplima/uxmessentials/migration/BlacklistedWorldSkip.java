package com.uxplima.uxmessentials.migration;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import com.uxplima.uxmessentials.shared.domain.WorldBlacklist;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.NullMarked;

/**
 * Skips an imported warp whose world is on its module's {@code world-blacklist}, before the writer it wraps sees the
 * record. An import writes warps straight through the repositories, not through the create commands, so without this
 * it would put a warp where {@code /warp create} or {@code /setpwarp} would refuse one. Server warps are held to the
 * warps list and player warps to the playerwarps list. Every other record passes through untouched.
 *
 * <p>A skipped warp is counted as {@link RecordOutcome#SKIPPED}, and its audit line carries
 * {@code held_back=world_blacklist} and the world's name, so a dry run shows it before a live run would. It wraps the
 * live and the dry-run writer alike, so both report the same lines. The lists are read on every record, so a reload of
 * either module's config applies to the next import.
 */
@NullMarked
public final class BlacklistedWorldSkip implements RecordWriter {

    private final RecordWriter delegate;
    private final Supplier<WorldBlacklist> warps;
    private final Supplier<WorldBlacklist> playerWarps;

    /**
     * @param delegate the writer a record that is not held back goes on to
     * @param warps the warps module's list, for server warps
     * @param playerWarps the playerwarps module's list, for player warps
     */
    public BlacklistedWorldSkip(
            RecordWriter delegate, Supplier<WorldBlacklist> warps, Supplier<WorldBlacklist> playerWarps) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.warps = Objects.requireNonNull(warps, "warps");
        this.playerWarps = Objects.requireNonNull(playerWarps, "playerWarps");
    }

    @Override
    public RecordResult write(ImportRecord record, ImportOptions options) {
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(options, "options");
        Optional<WorldRef> barred =
                switch (record) {
                    case ImportRecord.WarpRecord warp ->
                        barred(warps, warp.warp().warp().location().world());
                    case ImportRecord.PlayerWarpRecord warp ->
                        barred(playerWarps, warp.warp().location().world());
                    default -> Optional.empty();
                };
        if (barred.isPresent()) {
            return new RecordResult(
                    RecordOutcome.SKIPPED, List.of("held_back=world_blacklist", "world=" + token(barred.get())));
        }
        return delegate.write(record, options);
    }

    private static Optional<WorldRef> barred(Supplier<WorldBlacklist> list, WorldRef world) {
        return list.get().bars(world) ? Optional.of(world) : Optional.empty();
    }

    /** An audit field holds no spaces, so a world name that has some keeps its words joined. */
    private static String token(WorldRef world) {
        String name = world.name().strip().replaceAll("\\s+", "_");
        return name.isEmpty() ? "_" : name;
    }
}
