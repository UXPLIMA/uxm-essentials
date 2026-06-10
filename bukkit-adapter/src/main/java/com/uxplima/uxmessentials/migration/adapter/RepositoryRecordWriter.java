package com.uxplima.uxmessentials.migration.adapter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.economy.application.port.WalletRepository;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.Wallet;
import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.kits.adapter.outbound.KitItemCodec;
import com.uxplima.uxmessentials.kits.application.port.KitRepository;
import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.kits.domain.KitItem;
import com.uxplima.uxmessentials.migration.BalancePolicy;
import com.uxplima.uxmessentials.migration.ConflictPolicy;
import com.uxplima.uxmessentials.migration.ImportOptions;
import com.uxplima.uxmessentials.migration.ImportRecord;
import com.uxplima.uxmessentials.migration.RecordOutcome;
import com.uxplima.uxmessentials.migration.RecordWriter;
import com.uxplima.uxmessentials.migration.convert.essentialsx.map.ImportedKit;
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
    private final KitRepository kits;
    private final Currency defaultCurrency;

    public RepositoryRecordWriter(
            HomeRepository homes,
            WarpRepository warps,
            WalletRepository wallets,
            ModerationRepository moderation,
            KitRepository kits,
            Currency defaultCurrency) {
        this.homes = Objects.requireNonNull(homes, "homes");
        this.warps = Objects.requireNonNull(warps, "warps");
        this.wallets = Objects.requireNonNull(wallets, "wallets");
        this.moderation = Objects.requireNonNull(moderation, "moderation");
        this.kits = Objects.requireNonNull(kits, "kits");
        this.defaultCurrency = Objects.requireNonNull(defaultCurrency, "defaultCurrency");
    }

    @Override
    public RecordOutcome write(ImportRecord record, ImportOptions options) {
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(options, "options");
        return switch (record) {
            case ImportRecord.UserRecord user -> writeUser(user.user(), options);
            case ImportRecord.WarpRecord warp -> writeWarp(warp.warp().warp(), options);
            case ImportRecord.KitRecord kit -> writeKit(kit.kit(), options);
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

    private RecordOutcome writeKit(ImportedKit imported, ImportOptions options) {
        KitDefinition raw = imported.definition();
        boolean existed = kits.exists(raw.id());
        if (existed && options.onConflict() == ConflictPolicy.SKIP) {
            return RecordOutcome.SKIPPED;
        }
        // The mapper carried each EssentialsX item as its raw descriptor; the bukkit side resolves it to a stack
        // and re-encodes it into the kit context's own serialized form, exactly as a live /createkit would. An
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
