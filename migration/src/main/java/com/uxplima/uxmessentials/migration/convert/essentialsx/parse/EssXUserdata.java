package com.uxplima.uxmessentials.migration.convert.essentialsx.parse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.NullMarked;

/**
 * One parsed EssentialsX {@code userdata/<uuid>.yml} file, source-shaped: the player uuid, the
 * last-known name, the named homes (each a raw {@link EssXLocation}), the optional balance, and the
 * mail entries. Defensive defaults are applied at parse time. A missing balance is {@link
 * Optional#empty()} (mapped to an empty wallet, not a failed record), missing homes/mail are empty
 * lists, so the mapper downstream never has to second-guess a missing key.
 *
 * @param uuid the player's account identifier
 * @param name the name the account last logged in under, or its uuid when the file does not record one
 * @param homes the named homes, by EssentialsX home name
 * @param balance the EssentialsX {@code money} figure, empty when absent
 * @param mail the mail entries in source order
 * @param moderation the parsed mute/jail block, {@link EssXModeration#none()} for a clean player
 * @param kitTimestamps when the player last claimed each kit, epoch milliseconds by lower-case kit name; only kits with
 *     a recorded claim (a time above zero) are kept
 * @param lastSeen when the player last logged in or out, empty when the file does not say
 */
@NullMarked
public record EssXUserdata(
        UUID uuid,
        String name,
        Map<String, EssXLocation> homes,
        Optional<BigDecimal> balance,
        List<EssXMail> mail,
        EssXModeration moderation,
        Map<String, Long> kitTimestamps,
        Optional<Instant> lastSeen) {

    public EssXUserdata {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(name, "name");
        homes = Map.copyOf(Objects.requireNonNull(homes, "homes"));
        Objects.requireNonNull(balance, "balance");
        mail = List.copyOf(Objects.requireNonNull(mail, "mail"));
        Objects.requireNonNull(moderation, "moderation");
        kitTimestamps = Map.copyOf(Objects.requireNonNull(kitTimestamps, "kitTimestamps"));
        Objects.requireNonNull(lastSeen, "lastSeen");
    }

    /** The same player under {@code otherName}, for a name found outside the file. */
    public EssXUserdata withName(String otherName) {
        return new EssXUserdata(uuid, otherName, homes, balance, mail, moderation, kitTimestamps, lastSeen);
    }
}
