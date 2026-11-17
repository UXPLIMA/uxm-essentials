package com.uxplima.uxmessentials.migration.adapter;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.holograms.application.port.HologramRepository;
import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.kits.adapter.outbound.KitItemCodec;
import com.uxplima.uxmessentials.kits.application.port.KitRepository;
import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.kits.domain.KitItem;
import com.uxplima.uxmessentials.migration.AccountNames;
import com.uxplima.uxmessentials.migration.ConflictPolicy;
import com.uxplima.uxmessentials.migration.ImportOptions;
import com.uxplima.uxmessentials.migration.ImportRecord;
import com.uxplima.uxmessentials.migration.PlayerWarpRecordWriter;
import com.uxplima.uxmessentials.migration.RecordOutcome;
import com.uxplima.uxmessentials.migration.RecordResult;
import com.uxplima.uxmessentials.migration.RecordWriter;
import com.uxplima.uxmessentials.migration.UserRecordWriter;
import com.uxplima.uxmessentials.migration.convert.map.ImportedKit;
import com.uxplima.uxmessentials.migration.convert.map.ImportedModeration;
import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.domain.IpBan;
import com.uxplima.uxmessentials.moderation.domain.JailState;
import com.uxplima.uxmessentials.moderation.domain.ModerationProfile;
import com.uxplima.uxmessentials.moderation.domain.MuteState;
import com.uxplima.uxmessentials.moderation.domain.TempbanState;
import com.uxplima.uxmessentials.moderation.domain.Warn;
import com.uxplima.uxmessentials.shared.adapter.outbound.IpLiteral;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WarpName;
import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import org.jspecify.annotations.NullMarked;

/**
 * The live {@link RecordWriter}: applies each mapped record to the target contexts' durable repositories,
 * honouring the conflict and balance policies. A player's homes, balance and mail go through
 * {@link UserRecordWriter}, which reports what it held back. Every other write is an idempotent upsert
 * keyed by stable identity, a home by {@code (owner, slot)}, a warp by {@code name}, a wallet balance by
 * {@code (uuid, currency)}, so a re-run never duplicates a record, and the wallet balance is always
 * <em>set</em>, never added, so a re-run can never double-credit. It writes through the same repository
 * contracts {@code /sethome}, {@code /setwarp}, and the economy ledger use, so an import can never create
 * a state a live command could not.
 */
@NullMarked
public final class RepositoryRecordWriter implements RecordWriter {

    private final UserRecordWriter users;
    private final WarpRepository warps;
    private final ModerationRepository moderation;
    private final KitRepository kits;
    private final HologramRepository holograms;
    private final WorldRepository worlds;
    private final PlayerWarpRecordWriter playerWarps;
    private final AccountNames accounts;
    private final Clock clock;
    private final Logger log;

    public RepositoryRecordWriter(
            UserRecordWriter users,
            WarpRepository warps,
            ModerationRepository moderation,
            KitRepository kits,
            HologramRepository holograms,
            WorldRepository worlds,
            PlayerWarpRecordWriter playerWarps,
            AccountNames accounts,
            Clock clock,
            Logger log) {
        this.users = Objects.requireNonNull(users, "users");
        this.warps = Objects.requireNonNull(warps, "warps");
        this.moderation = Objects.requireNonNull(moderation, "moderation");
        this.kits = Objects.requireNonNull(kits, "kits");
        this.holograms = Objects.requireNonNull(holograms, "holograms");
        this.worlds = Objects.requireNonNull(worlds, "worlds");
        this.playerWarps = Objects.requireNonNull(playerWarps, "playerWarps");
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public RecordResult write(ImportRecord record, ImportOptions options) {
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(options, "options");
        return switch (record) {
            case ImportRecord.AccountRecord account ->
                RecordResult.of(accounts.record(account.account()) ? RecordOutcome.WRITTEN : RecordOutcome.SKIPPED);
            case ImportRecord.UserRecord user -> users.write(user.user(), options);
            case ImportRecord.WarpRecord warp ->
                RecordResult.of(writeWarp(warp.warp().warp(), options));
            case ImportRecord.PlayerWarpRecord warp -> RecordResult.of(playerWarps.write(warp.warp()));
            case ImportRecord.KitRecord kit -> RecordResult.of(writeKit(kit.kit(), options));
            case ImportRecord.ModerationRecord rec -> RecordResult.of(writeModeration(rec.moderation(), options));
            case ImportRecord.BanRecord ban -> RecordResult.of(writeBan(ban, options));
            case ImportRecord.IpBanRecord ban -> RecordResult.of(writeIpBan(ban.ban(), options));
            case ImportRecord.WarnRecord warn -> RecordResult.of(writeWarn(warn));
            case ImportRecord.HologramRecord hologram ->
                RecordResult.of(writeHologram(hologram.hologram().hologram(), options));
            case ImportRecord.WorldRecord world ->
                RecordResult.of(writeWorld(world.world().world(), options));
        };
    }

    private RecordOutcome writeWorld(ManagedWorld world, ImportOptions options) {
        boolean existed = worlds.exists(world.name());
        if (existed && options.onConflict() == ConflictPolicy.SKIP) {
            return RecordOutcome.SKIPPED;
        }
        worlds.save(world);
        return existed ? RecordOutcome.OVERWRITTEN : RecordOutcome.WRITTEN;
    }

    private RecordOutcome writeHologram(Hologram hologram, ImportOptions options) {
        HologramName name = hologram.name();
        boolean existed = holograms.exists(name);
        if (existed && options.onConflict() == ConflictPolicy.SKIP) {
            return RecordOutcome.SKIPPED;
        }
        holograms.save(hologram);
        return existed ? RecordOutcome.OVERWRITTEN : RecordOutcome.WRITTEN;
    }

    private RecordOutcome writeWarp(Warp warp, ImportOptions options) {
        WarpName name = warp.name();
        boolean existed = warps.exists(name);
        if (existed && options.onConflict() == ConflictPolicy.SKIP) {
            return RecordOutcome.SKIPPED;
        }
        warps.save(warp);
        return existed ? RecordOutcome.OVERWRITTEN : RecordOutcome.WRITTEN;
    }

    private RecordOutcome writeKit(ImportedKit imported, ImportOptions options) {
        KitDefinition raw = imported.definition();
        boolean existed = kits.exists(raw.id());
        if (existed && options.onConflict() == ConflictPolicy.SKIP) {
            // An imported kit is always reserved; a kept one that is not stays open to every player, which the
            // operator likely did not mean if it came from an earlier import.
            kits.find(raw.id())
                    .filter(kept -> !kept.requiresPermission())
                    .ifPresent(kept -> log.warn(
                            "kit {} was kept as it is and needs no permission; set permission = true in its file to"
                                    + " reserve it",
                            kept.id().value()));
            return RecordOutcome.SKIPPED;
        }
        // The mapper carried each EssentialsX item as its raw descriptor; the bukkit side resolves it to a stack
        // and re-encodes it into the kit context's own serialized form, exactly as a live /kit create would. An
        // item whose material does not resolve on this server is dropped so one bad line never fails the kit.
        kits.save(raw.withItems(convertItems(raw.items())));
        return existed ? RecordOutcome.OVERWRITTEN : RecordOutcome.WRITTEN;
    }

    private List<KitItem> convertItems(List<KitItem> rawItems) {
        List<KitItem> converted = new ArrayList<>();
        for (KitItem raw : rawItems) {
            EssentialsXKitItemConverter.toStack(raw.data())
                    .map(KitItemCodec::encode)
                    .ifPresent(converted::add);
        }
        return converted;
    }

    private RecordOutcome writeModeration(ImportedModeration imported, ImportOptions options) {
        ModerationProfile profile = imported.profile();
        PlayerRef target = profile.target();
        boolean existed = isSanctioned(moderation.load(target));
        if (existed && options.onConflict() == ConflictPolicy.SKIP) {
            return RecordOutcome.SKIPPED;
        }
        // Lazily materialise the seen row so an offline target's FK-bearing sanction write never breaks
        // integrity (ModerationRepository#ensureUserExists). Each sanction is a keyed upsert (set, never
        // append), so a re-run replaces rather than duplicates the imported mute/jail.
        moderation.ensureUserExists(target, Instant.EPOCH);
        applySanctions(profile);
        return existed ? RecordOutcome.OVERWRITTEN : RecordOutcome.WRITTEN;
    }

    private void applySanctions(ModerationProfile profile) {
        if (!(profile.mute() instanceof MuteState.None)) {
            moderation.saveMute(profile.target(), profile.mute());
        }
        if (!(profile.jail() instanceof JailState.None)) {
            moderation.saveJail(profile.target(), profile.jail());
        }
    }

    private static boolean isSanctioned(ModerationProfile existing) {
        return !(existing.mute() instanceof MuteState.None) || !(existing.jail() instanceof JailState.None);
    }

    private RecordOutcome writeBan(ImportRecord.BanRecord rec, ImportOptions options) {
        PlayerRef target = rec.target();
        boolean existed = moderation.loadTempban(target) instanceof TempbanState.Active;
        if (existed && options.onConflict() == ConflictPolicy.SKIP) {
            return RecordOutcome.SKIPPED;
        }
        // Materialise the seen row so an offline target's FK-bearing ban write never breaks integrity; the
        // tempban is a keyed upsert (set, never append), so a re-run replaces the row rather than duplicating
        // it: a permanent ban rides in as a far-future Active, exactly the row /ban writes.
        moderation.ensureUserExists(target, clock.instant());
        moderation.saveTempban(target, rec.ban());
        return existed ? RecordOutcome.OVERWRITTEN : RecordOutcome.WRITTEN;
    }

    private RecordOutcome writeIpBan(IpBan imported, ImportOptions options) {
        IpBan ban = canonical(imported);
        boolean existed = moderation.activeIpBan(ban.ip(), clock.instant()).isPresent();
        if (existed && options.onConflict() == ConflictPolicy.SKIP) {
            return RecordOutcome.SKIPPED;
        }
        // The IP ban is keyed by address, so the upsert replaces a same-address row rather than duplicating it.
        moderation.saveIpBan(ban);
        return existed ? RecordOutcome.OVERWRITTEN : RecordOutcome.WRITTEN;
    }

    /**
     * {@code ban} with its address in the form a connecting player's address is read in, so an imported ban written
     * as compressed or upper-case IPv6 bars the players it names. Text that is not an address is kept as written.
     */
    static IpBan canonical(IpBan ban) {
        return IpLiteral.canonical(ban.ip())
                .filter(ip -> !ip.equals(ban.ip()))
                .map(ip -> new IpBan(ip, ban.until(), ban.reason(), ban.target(), ban.issuer(), ban.issuedAt()))
                .orElse(ban);
    }

    private RecordOutcome writeWarn(ImportRecord.WarnRecord rec) {
        PlayerRef target = rec.target();
        // The warning history is append-only, a single row cannot be "overwritten", so idempotence is by
        // content under every policy: a re-run that already holds an equivalent warning (same issuer, reason
        // and issue instant) skips rather than appending a duplicate.
        if (hasEquivalentWarn(target, rec.warn())) {
            return RecordOutcome.SKIPPED;
        }
        moderation.ensureUserExists(target, clock.instant());
        moderation.appendWarn(target, rec.warn());
        return RecordOutcome.WRITTEN;
    }

    private boolean hasEquivalentWarn(PlayerRef target, Warn warn) {
        return moderation.warns(target, warn.issuedAt()).stream().anyMatch(existing -> existing.equals(warn));
    }
}
