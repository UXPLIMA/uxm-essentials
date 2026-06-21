package com.uxplima.uxmessentials.persistence.warps;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.jooq.tables.Warps;
import com.uxplima.uxmessentials.persistence.jooq.tables.records.WarpsRecord;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WarpCost;
import com.uxplima.uxmessentials.warps.domain.WarpName;
import com.uxplima.uxmessentials.warps.domain.WelcomeMessage;
import org.jooq.Record;

/**
 * The anti-corruption mapping between a {@code warps} row and the domain {@link Warp}. UUIDs are stored as
 * their canonical 36-character text and the creation time as epoch milliseconds, so the column shape is
 * identical on every backend; the optional cost and required-permission columns are nullable, mapping to a
 * {@link WarpCost#free()} cost and an empty {@link Optional} respectively. This class is the single place
 * that translation lives.
 *
 * <p>The owner's display name is not persisted (only the uuid is), so the repository hands in a uuid-to-name
 * resolver (an adapter-supplied profile lookup); the rebuilt {@link Warp} then carries the live owner name so
 * {@code /warpinfo}, the GUI, and the placeholders render the name rather than a raw uuid.
 */
final class WarpRows {

    private static final Warps WARPS = Warps.WARPS;
    private static final com.google.gson.Gson GSON = new com.google.gson.Gson();
    private static final java.lang.reflect.Type WELCOME_MESSAGES_TYPE =
            new com.google.gson.reflect.TypeToken<java.util.List<WelcomeMessage>>() {}.getType();

    private WarpRows() {}

    /** Rebuild a {@link Warp} from a queried row, resolving the owner's display name through {@code names}. */
    static Warp toWarp(Record row, java.util.function.Function<UUID, String> names) {
        WorldRef world = new WorldRef(UUID.fromString(row.get(WARPS.WORLD)), row.get(WARPS.WORLD_NAME));
        Position position = new Position(
                world, row.get(WARPS.X), row.get(WARPS.Y), row.get(WARPS.Z), row.get(WARPS.YAW), row.get(WARPS.PITCH));
        UUID ownerUuid = UUID.fromString(row.get(WARPS.OWNER));
        PlayerRef owner = new PlayerRef(ownerUuid, names.apply(ownerUuid));
        return new Warp(
                WarpName.of(row.get(WARPS.NAME)),
                position,
                owner,
                Instant.ofEpochMilli(row.get(WARPS.CREATED_AT)),
                cost(row.get(WARPS.COST), row.get(WARPS.COST_CURRENCY)),
                Optional.ofNullable(row.get(WARPS.REQUIRED_PERMISSION)),
                row.get(WARPS.VISITORS),
                Optional.ofNullable(row.get(WARPS.PASSWORD)),
                row.get(WARPS.IS_LOCKED) != 0,
                deserializeWelcomeMessages(row.get(WARPS.WELCOME_MESSAGE), row.get(WARPS.WELCOME_MESSAGE_TYPE)),
                Optional.ofNullable(row.get(WARPS.DEPARTURE_SOUND)),
                Optional.ofNullable(row.get(WARPS.ARRIVAL_SOUND)),
                Optional.ofNullable(row.get(WARPS.DEPARTURE_PARTICLE)),
                Optional.ofNullable(row.get(WARPS.ARRIVAL_PARTICLE)),
                Optional.ofNullable(row.get(WARPS.WARMUP_SECONDS)),
                Optional.ofNullable(row.get(WARPS.COOLDOWN_SECONDS)),
                Optional.ofNullable(row.get(WARPS.ICON_MATERIAL)));
    }

    /** Populate a {@link WarpsRecord} from a domain {@link Warp} for an upsert. */
    static void apply(WarpsRecord record, Warp warp) {
        Position location = warp.location();
        record.setName(warp.name().value())
                .setWorld(location.world().uid().toString())
                .setWorldName(location.world().name())
                .setX(location.x())
                .setY(location.y())
                .setZ(location.z())
                .setYaw(location.yaw())
                .setPitch(location.pitch())
                .setOwner(warp.owner().uuid().toString())
                .setCreatedAt(warp.createdAt().toEpochMilli())
                .setCost(warp.hasCost() ? warp.cost().amount() : null)
                .setCostCurrency(warp.hasCost() ? warp.cost().currencyId() : "default")
                .setRequiredPermission(warp.requiredPermission().orElse(null))
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

    private static WarpCost cost(BigDecimal stored, String currencyId) {
        // A null cost column is a free warp; a stored amount is the price to use it.
        return stored == null ? WarpCost.free() : WarpCost.of(stored, currencyId != null ? currencyId : "default");
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
