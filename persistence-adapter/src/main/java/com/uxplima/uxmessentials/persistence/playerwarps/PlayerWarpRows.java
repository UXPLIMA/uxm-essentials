package com.uxplima.uxmessentials.persistence.playerwarps;

import java.time.Instant;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.jooq.tables.PlayerWarps;
import com.uxplima.uxmessentials.persistence.jooq.tables.records.PlayerWarpsRecord;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.warps.domain.WelcomeMessage;
import org.jooq.Record;

/**
 * The anti-corruption mapping between a {@code player_warps} row and the domain {@link PlayerWarp}. UUIDs are
 * stored as their canonical 36-character text and the creation time as epoch milliseconds, so the column
 * shape is identical on every backend; the public flag is stored as an {@code INT} ({@code 1} public,
 * {@code 0} private) for the same portability reason. This class is the single place that translation lives.
 *
 * <p>The owner name is not persisted (only the uuid is), so a {@link PlayerWarp} rebuilt from a row carries
 * the owner uuid with the uuid string as a placeholder name — the repository passes the queried owner uuid
 * through and a live name is resolved by the adapter when one is available.
 */
final class PlayerWarpRows {

    private static final PlayerWarps PLAYER_WARPS = PlayerWarps.PLAYER_WARPS;
    private static final int PUBLIC = 1;
    private static final com.google.gson.Gson GSON = new com.google.gson.Gson();
    private static final java.lang.reflect.Type WELCOME_MESSAGES_TYPE =
            new com.google.gson.reflect.TypeToken<java.util.List<WelcomeMessage>>() {}.getType();

    private PlayerWarpRows() {}

    /** Rebuild a {@link PlayerWarp} from a queried row. */
    static PlayerWarp toPlayerWarp(Record row) {
        WorldRef world = new WorldRef(UUID.fromString(row.get(PLAYER_WARPS.WORLD)), row.get(PLAYER_WARPS.WORLD_NAME));
        Position position = new Position(
                world,
                row.get(PLAYER_WARPS.X),
                row.get(PLAYER_WARPS.Y),
                row.get(PLAYER_WARPS.Z),
                row.get(PLAYER_WARPS.YAW),
                row.get(PLAYER_WARPS.PITCH));
        UUID ownerUuid = UUID.fromString(row.get(PLAYER_WARPS.OWNER));
        PlayerRef owner = new PlayerRef(ownerUuid, ownerUuid.toString());
        return new PlayerWarp(
                owner,
                PlayerWarpName.of(row.get(PLAYER_WARPS.NAME)),
                position,
                row.get(PLAYER_WARPS.IS_PUBLIC) == PUBLIC,
                Instant.ofEpochMilli(row.get(PLAYER_WARPS.CREATED_AT)),
                row.get(PLAYER_WARPS.VISITORS),
                java.util.Optional.ofNullable(row.get(PLAYER_WARPS.PASSWORD)),
                row.get(PLAYER_WARPS.IS_LOCKED) != 0,
                deserializeWelcomeMessages(
                        row.get(PLAYER_WARPS.WELCOME_MESSAGE), row.get(PLAYER_WARPS.WELCOME_MESSAGE_TYPE)),
                java.util.Optional.ofNullable(row.get(PLAYER_WARPS.DEPARTURE_SOUND)),
                java.util.Optional.ofNullable(row.get(PLAYER_WARPS.ARRIVAL_SOUND)),
                java.util.Optional.ofNullable(row.get(PLAYER_WARPS.DEPARTURE_PARTICLE)),
                java.util.Optional.ofNullable(row.get(PLAYER_WARPS.ARRIVAL_PARTICLE)),
                java.util.Optional.ofNullable(row.get(PLAYER_WARPS.WARMUP_SECONDS)),
                java.util.Optional.ofNullable(row.get(PLAYER_WARPS.COOLDOWN_SECONDS)),
                java.util.Optional.ofNullable(row.get(PLAYER_WARPS.ICON_MATERIAL)));
    }

    /** Populate a {@link PlayerWarpsRecord} from a domain {@link PlayerWarp} for an upsert. */
    static void apply(PlayerWarpsRecord record, PlayerWarp warp) {
        Position location = warp.location();
        record.setOwner(warp.owner().uuid().toString())
                .setName(warp.name().value())
                .setWorld(location.world().uid().toString())
                .setWorldName(location.world().name())
                .setX(location.x())
                .setY(location.y())
                .setZ(location.z())
                .setYaw(location.yaw())
                .setPitch(location.pitch())
                .setIsPublic(warp.isPublic() ? PUBLIC : 0)
                .setCreatedAt(warp.createdAt().toEpochMilli())
                .setVisitors(warp.visitors())
                .setPassword(warp.password().orElse(null))
                .setIsLocked(warp.isLocked() ? 1 : 0)
                .setWelcomeMessage(serializeWelcomeMessages(warp.welcomeMessages()))
                .setWelcomeMessageType(warp.welcomeMessages().isEmpty() ? "CHAT" : "JSON")
                .setDepartureSound(warp.departureSound().orElse(null))
                .setArrivalSound(warp.arrivalSound().orElse(null))
                .setDepartureParticle(warp.departureParticle().orElse(null))
                .setArrivalParticle(warp.arrivalParticle().orElse(null))
                .setWarmupSeconds(warp.warmupOverrideSeconds().orElse(null))
                .setCooldownSeconds(warp.cooldownOverrideSeconds().orElse(null))
                .setIconMaterial(warp.iconMaterial().orElse(null));
    }

    private static java.util.List<WelcomeMessage> deserializeWelcomeMessages(String welcomeMessage, String type) {
        if (welcomeMessage == null || welcomeMessage.isBlank()) {
            return java.util.List.of();
        }
        if (welcomeMessage.startsWith("[")) {
            try {
                java.util.List<WelcomeMessage> list = GSON.fromJson(welcomeMessage, WELCOME_MESSAGES_TYPE);
                if (list != null) {
                    return list;
                }
            } catch (Exception e) {
                // fallback if JSON is invalid
            }
        }
        String messageType = type != null ? type : "CHAT";
        return java.util.List.of(new WelcomeMessage(welcomeMessage, messageType));
    }

    private static @org.jspecify.annotations.Nullable String serializeWelcomeMessages(
            java.util.List<WelcomeMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        return GSON.toJson(messages);
    }
}
