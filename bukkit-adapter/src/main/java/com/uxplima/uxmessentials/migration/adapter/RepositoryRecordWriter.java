package com.uxplima.uxmessentials.migration.adapter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

import com.uxplima.uxmessentials.economy.application.port.WalletRepository;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.Wallet;
import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.migration.BalancePolicy;
import com.uxplima.uxmessentials.migration.ConflictPolicy;
import com.uxplima.uxmessentials.migration.ImportOptions;
import com.uxplima.uxmessentials.migration.ImportRecord;
import com.uxplima.uxmessentials.migration.RecordOutcome;
import com.uxplima.uxmessentials.migration.RecordWriter;
import com.uxplima.uxmessentials.migration.convert.essentialsx.map.ImportedModeration;
import com.uxplima.uxmessentials.migration.convert.essentialsx.map.ImportedUser;
import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.domain.JailState;
import com.uxplima.uxmessentials.moderation.domain.ModerationProfile;
import com.uxplima.uxmessentials.moderation.domain.MuteState;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WarpName;
import org.jspecify.annotations.NullMarked;

/**
 * The live {@link RecordWriter}: applies each mapped record to the target contexts' durable repositories,
 * honouring the conflict and balance policies (docs/12-migration §6). Every write is an idempotent upsert
 * keyed by stable identity — a home by {@code (owner, name)}, a warp by {@code name}, a wallet balance by
 * {@code (uuid, currency)} — so a re-run never duplicates a record, and the wallet balance is always
 * <em>set</em>, never added, so a re-run can never double-credit. It writes through the same repository
 * contracts {@code /sethome}, {@code /setwarp}, and the economy ledger use, so an import can never create
 * a state a live command could not.
 */
@NullMarked
public final class RepositoryRecordWriter implements RecordWriter {

    private final HomeRepository homes;
    private final WarpRepository warps;
    private final WalletRepository wallets;
    private final ModerationRepository moderation;
    private final Currency defaultCurrency;

    public RepositoryRecordWriter(
            HomeRepository homes,
            WarpRepository warps,
            WalletRepository wallets,
            ModerationRepository moderation,
            Currency defaultCurrency) {
        this.homes = Objects.requireNonNull(homes, "homes");
        this.warps = Objects.requireNonNull(warps, "warps");
        this.wallets = Objects.requireNonNull(wallets, "wallets");
        this.moderation = Objects.requireNonNull(moderation, "moderation");
        this.defaultCurrency = Objects.requireNonNull(defaultCurrency, "defaultCurrency");
    }

    @Override
    public RecordOutcome write(ImportRecord record, ImportOptions options) {
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(options, "options");
        return switch (record) {
            case ImportRecord.UserRecord user -> writeUser(user.user(), options);
            case ImportRecord.WarpRecord warp -> writeWarp(warp.warp().warp(), options);
            case ImportRecord.KitRecord ignored -> RecordOutcome.WRITTEN;
            case ImportRecord.ModerationRecord rec -> writeModeration(rec.moderation(), options);
        };
    }

    private RecordOutcome writeUser(ImportedUser user, ImportOptions options) {
        writeHomes(user, options);
        user.balance().ifPresent(balance -> writeBalance(user.owner(), balance, options.balancePolicy()));
        return RecordOutcome.WRITTEN;
    }

    private void writeHomes(ImportedUser user, ImportOptions options) {
        for (Home home : user.homes()) {
            if (shouldWriteHome(home, options.onConflict())) {
                homes.save(home);
            }
        }
    }

    private boolean shouldWriteHome(Home home, ConflictPolicy policy) {
        if (policy != ConflictPolicy.SKIP) {
            return true; // overwrite and merge both upsert the home (merge adds only new names, same call)
        }
        return homes.load(home.owner()).find(home.name()).isEmpty();
    }

    private void writeBalance(PlayerRef owner, BigDecimal raw, BalancePolicy policy) {
        if (policy == BalancePolicy.SKIP_IF_PRESENT && hasBalance(owner)) {
            return;
        }
        wallets.ensureOwner(owner);
        // Money.of normalises to the currency's precision; the upsert sets (never adds) the balance, so a
        // re-run can never double-credit (docs/12-migration §6).
        wallets.upsertBalance(owner, Money.of(defaultCurrency, raw));
    }

    private boolean hasBalance(PlayerRef owner) {
        return wallets.findByOwner(owner)
                .map(wallet -> existingAmount(wallet).signum() != 0)
                .orElse(false);
    }

    private BigDecimal existingAmount(Wallet wallet) {
        return wallet.balanceOf(defaultCurrency).amount();
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

    private RecordOutcome writeModeration(ImportedModeration imported, ImportOptions options) {
        ModerationProfile profile = imported.profile();
        PlayerRef target = profile.target();
        boolean existed = isSanctioned(moderation.load(target));
        if (existed && options.onConflict() == ConflictPolicy.SKIP) {
            return RecordOutcome.SKIPPED;
        }
        // Lazily materialise the seen row so an offline target's FK-bearing sanction write never breaks
        // integrity (ModerationRepository#ensureUserExists). Each sanction is a keyed upsert (set, never
        // append), so a re-run replaces rather than duplicates the imported mute/jail (docs/12-migration §6).
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
}
