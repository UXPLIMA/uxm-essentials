package com.uxplima.uxmessentials.migration.convert.map;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.kits.domain.KitId;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * One parsed-and-mapped player, expressed entirely in domain terms: the owner, the homes that mapped to
 * {@link Home} aggregates, the raw balance to seed a wallet with, the mail waiting for them, and the one-time kits
 * they already claimed.
 *
 * <p>The balance is carried as a raw {@link BigDecimal} rather than a {@code Money} because the target
 * {@code Currency} is resolved at write time from the live economy config, keeping this mapper output
 * free of any runtime-configured currency instance. The writer pairs the figure with the default
 * currency and applies the {@code balance-policy}.
 *
 * @param owner the player this record belongs to
 * @param homes the homes mapped from the source, already clamped to valid names
 * @param balance the source money figure, empty when the source carried none
 * @param mail the mail waiting for the player, in source order
 * @param kitClaims the one-time kits the player already claimed in the source, with when they claimed each
 */
@NullMarked
public record ImportedUser(
        PlayerRef owner,
        List<Home> homes,
        Optional<BigDecimal> balance,
        List<ImportedMail> mail,
        Map<KitId, Instant> kitClaims) {

    public ImportedUser {
        Objects.requireNonNull(owner, "owner");
        homes = List.copyOf(Objects.requireNonNull(homes, "homes"));
        Objects.requireNonNull(balance, "balance");
        mail = List.copyOf(Objects.requireNonNull(mail, "mail"));
        kitClaims = Map.copyOf(Objects.requireNonNull(kitClaims, "kitClaims"));
    }

    /** A player whose source records no kit claims, as every source but EssentialsX is. */
    public ImportedUser(PlayerRef owner, List<Home> homes, Optional<BigDecimal> balance, List<ImportedMail> mail) {
        this(owner, homes, balance, mail, Map.of());
    }
}
