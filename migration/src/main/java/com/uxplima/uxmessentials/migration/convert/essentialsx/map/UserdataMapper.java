package com.uxplima.uxmessentials.migration.convert.essentialsx.map;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.homes.domain.HomeLabel;
import com.uxplima.uxmessentials.homes.domain.HomeSlot;
import com.uxplima.uxmessentials.kits.domain.KitId;
import com.uxplima.uxmessentials.migration.convert.essentialsx.parse.EssXLocation;
import com.uxplima.uxmessentials.migration.convert.essentialsx.parse.EssXMail;
import com.uxplima.uxmessentials.migration.convert.essentialsx.parse.EssXUserdata;
import com.uxplima.uxmessentials.migration.convert.map.ImportedMail;
import com.uxplima.uxmessentials.migration.convert.map.ImportedUser;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.NullMarked;

/**
 * Translates a parsed EssentialsX userdata record into domain aggregates. Pure
 * ACL: no Bukkit, no YAML, no I/O: those stay in {@code parse/}. It maps homes whose world the server
 * knows into {@link Home} value objects (a home in an unknown world is dropped, not fatal), carries the raw
 * balance forward for the writer to seed a wallet with, turns each mail entry into an {@link ImportedMail}, and keeps
 * the claim time of each one-time kit the player already took.
 */
@NullMarked
public final class UserdataMapper {

    private final WorldNameResolver worlds;
    private final MailMapper mailMapper = new MailMapper();

    public UserdataMapper(WorldNameResolver worlds) {
        this.worlds = Objects.requireNonNull(worlds, "worlds");
    }

    /** Map {@code src} as a player who claimed no one-time kit. */
    public ImportedUser map(EssXUserdata src) {
        return map(src, Set.of());
    }

    /**
     * Map {@code src}, keeping the claims of the kits in {@code oneTimeKits}. A claim of a repeatable kit is only a
     * cooldown, and one of a kit {@code kits.yml} no longer has names nothing to claim, so both are left out.
     */
    public ImportedUser map(EssXUserdata src, Set<KitId> oneTimeKits) {
        Objects.requireNonNull(src, "src");
        Objects.requireNonNull(oneTimeKits, "oneTimeKits");
        PlayerRef owner = new PlayerRef(src.uuid(), src.name());
        return new ImportedUser(
                owner,
                mapHomes(owner, src.homes()),
                src.balance(),
                mapMail(src.mail()),
                kitClaims(src.kitTimestamps(), oneTimeKits));
    }

    private static Map<KitId, Instant> kitClaims(Map<String, Long> timestamps, Set<KitId> oneTimeKits) {
        Map<KitId, Instant> claims = new HashMap<>();
        for (KitId kit : oneTimeKits) {
            Long claimedAt = timestamps.get(kit.value());
            if (claimedAt != null) {
                claims.put(kit, Instant.ofEpochMilli(claimedAt));
            }
        }
        return claims;
    }

    private List<ImportedMail> mapMail(List<EssXMail> rawMail) {
        List<ImportedMail> mail = new ArrayList<>(rawMail.size());
        for (EssXMail entry : rawMail) {
            mailMapper.map(entry).ifPresent(mail::add);
        }
        return mail;
    }

    private List<Home> mapHomes(PlayerRef owner, Map<String, EssXLocation> rawHomes) {
        // Sort by name for deterministic slot assignment across JVM runs (Map.copyOf loses insertion order).
        List<Map.Entry<String, EssXLocation>> sorted =
                rawHomes.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList();
        List<Home> homes = new ArrayList<>();
        int slot = 0;
        for (Map.Entry<String, EssXLocation> entry : sorted) {
            Optional<Home> mapped = toHome(owner, HomeSlot.of(slot), entry.getKey(), entry.getValue());
            if (mapped.isPresent()) {
                homes.add(mapped.get());
                slot++;
            }
        }
        return homes;
    }

    private Optional<Home> toHome(PlayerRef owner, HomeSlot slot, String rawName, EssXLocation location) {
        return position(location).map(pos -> {
            Home base = Home.create(owner, slot, pos, Instant.EPOCH);
            Optional<HomeLabel> label = labelOf(rawName);
            return label.isEmpty() ? base : base.withLabel(label, Instant.EPOCH);
        });
    }

    /**
     * Converts a raw EssentialsX home name to a {@link HomeLabel}. Names that are blank after stripping
     * are silently dropped (yields empty). Names longer than {@link HomeLabel#MAX_LENGTH} are truncated to
     * that length rather than aborting the import.
     */
    private static Optional<HomeLabel> labelOf(String rawName) {
        String trimmed = rawName.strip();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }
        if (trimmed.length() > HomeLabel.MAX_LENGTH) {
            trimmed = trimmed.substring(0, HomeLabel.MAX_LENGTH);
        }
        return Optional.of(HomeLabel.of(trimmed));
    }

    private Optional<Position> position(EssXLocation location) {
        return worlds.resolve(location.world()).map(world -> toPosition(world, location));
    }

    private static Position toPosition(WorldRef world, EssXLocation src) {
        return new Position(world, src.x(), src.y(), src.z(), src.yaw(), src.pitch());
    }
}
