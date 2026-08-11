package com.uxplima.uxmessentials.rest.view;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.uxplima.uxmessentials.api.view.UxmBackPoint;
import com.uxplima.uxmessentials.api.view.UxmBaltopEntry;
import com.uxplima.uxmessentials.api.view.UxmDiscordLink;
import com.uxplima.uxmessentials.api.view.UxmHome;
import com.uxplima.uxmessentials.api.view.UxmIgnore;
import com.uxplima.uxmessentials.api.view.UxmIssuer;
import com.uxplima.uxmessentials.api.view.UxmKit;
import com.uxplima.uxmessentials.api.view.UxmLocation;
import com.uxplima.uxmessentials.api.view.UxmMail;
import com.uxplima.uxmessentials.api.view.UxmMoney;
import com.uxplima.uxmessentials.api.view.UxmPlayerState;
import com.uxplima.uxmessentials.api.view.UxmPlayerWarp;
import com.uxplima.uxmessentials.api.view.UxmPlaytime;
import com.uxplima.uxmessentials.api.view.UxmPresence;
import com.uxplima.uxmessentials.api.view.UxmRank;
import com.uxplima.uxmessentials.api.view.UxmRankStanding;
import com.uxplima.uxmessentials.api.view.UxmRegion;
import com.uxplima.uxmessentials.api.view.UxmSanction;
import com.uxplima.uxmessentials.api.view.UxmSanctionRecord;
import com.uxplima.uxmessentials.api.view.UxmSecurityStatus;
import com.uxplima.uxmessentials.api.view.UxmSnapshot;
import com.uxplima.uxmessentials.api.view.UxmTeleportRequest;
import com.uxplima.uxmessentials.api.view.UxmTrade;
import com.uxplima.uxmessentials.api.view.UxmVault;
import com.uxplima.uxmessentials.api.view.UxmVoteParty;
import com.uxplima.uxmessentials.api.view.UxmVoteRank;
import com.uxplima.uxmessentials.api.view.UxmVoteTotals;
import com.uxplima.uxmessentials.api.view.UxmWarn;
import com.uxplima.uxmessentials.api.view.UxmWarp;
import com.uxplima.uxmessentials.api.view.UxmWorld;

/**
 * How every published view becomes JSON, in one file.
 *
 * <p>One place rather than one per route, because the same home has to look the same whether it came from a list or
 * a lookup, and the only reliable way to get that is for both to call the same method.
 *
 * <p>Three conventions run through all of it. A time is an ISO-8601 instant, because a consumer in another timezone
 * should not have to know this server's. A duration is whole seconds, named {@code -seconds} so nobody has to guess
 * the unit. A value that is absent is present and {@code null} rather than missing, so the shape of an answer does
 * not depend on the data in it, and a consumer that reads a field never has to check whether the key was there.
 *
 * <p>Money is written as a JSON number and not a string. Gson renders a {@code BigDecimal} exactly, so nothing is
 * rounded on the way out; a consumer whose language would round it on the way in is a problem this cannot fix from
 * here, and quoting it would only move the surprise.
 */
public final class Views {

    private Views() {}

    /** Render each of {@code items} and collect them into an array. */
    public static <T> JsonArray each(Collection<T> items, Function<T, JsonElement> render) {
        JsonArray array = new JsonArray();
        items.forEach(item -> array.add(render.apply(item)));
        return array;
    }

    /** A number that may not be there, which is the number or {@code null} and never a missing key. */
    public static JsonElement number(Optional<? extends Number> value) {
        return value.<JsonElement>map(JsonPrimitive::new).orElse(JsonNull.INSTANCE);
    }

    public static JsonElement location(UxmLocation location) {
        JsonObject json = new JsonObject();
        json.addProperty("world", location.world());
        json.addProperty("x", location.x());
        json.addProperty("y", location.y());
        json.addProperty("z", location.z());
        json.addProperty("yaw", location.yaw());
        json.addProperty("pitch", location.pitch());
        return json;
    }

    public static JsonElement money(UxmMoney money) {
        JsonObject json = new JsonObject();
        json.addProperty("currency", money.currency());
        json.addProperty("amount", money.amount());
        return json;
    }

    public static JsonElement baltopEntry(UxmBaltopEntry entry) {
        JsonObject json = new JsonObject();
        json.addProperty("rank", entry.rank());
        json.addProperty("player-id", entry.playerId().toString());
        json.addProperty("player-name", entry.playerName());
        json.add("balance", money(entry.balance()));
        return json;
    }

    public static JsonElement home(UxmHome home) {
        JsonObject json = new JsonObject();
        json.addProperty("owner-id", home.ownerId().toString());
        json.addProperty("slot", home.slot());
        json.addProperty("slot-number", home.slotNumber());
        json.add("location", location(home.location()));
        text(json, "label", home.label());
        text(json, "icon", home.icon());
        json.addProperty("public", home.isPublic());
        instant(json, "created-at", home.createdAt());
        instant(json, "updated-at", home.updatedAt());
        return json;
    }

    public static JsonElement warp(UxmWarp warp) {
        JsonObject json = new JsonObject();
        json.addProperty("name", warp.name());
        json.add("location", location(warp.location()));
        json.addProperty("owner-id", warp.ownerId().toString());
        json.addProperty("owner-name", warp.ownerName());
        instant(json, "created-at", warp.createdAt());
        json.add("cost", warp.cost().map(Views::money).orElse(JsonNull.INSTANCE));
        text(json, "required-permission", warp.requiredPermission());
        json.addProperty("visitors", warp.visitors());
        json.addProperty("locked", warp.locked());
        json.addProperty("password-protected", warp.passwordProtected());
        text(json, "category", warp.category());
        text(json, "icon", warp.icon());
        return json;
    }

    public static JsonElement playerWarp(UxmPlayerWarp warp) {
        JsonObject json = new JsonObject();
        json.addProperty("id", warp.id());
        json.addProperty("name", warp.name());
        text(json, "display-name", warp.displayName());
        json.addProperty("owner-id", warp.ownerId().toString());
        json.addProperty("owner-name", warp.ownerName());
        json.add("location", location(warp.location()));
        text(json, "server-id", warp.serverId());
        text(json, "category", warp.category());
        text(json, "description", warp.description());
        text(json, "icon", warp.icon());
        json.addProperty("access", warp.access().name());
        json.addProperty("password-protected", warp.passwordProtected());
        json.addProperty("status", warp.status().name());
        json.add("price", warp.price().map(Views::money).orElse(JsonNull.INSTANCE));
        addCounts(json, warp);
        json.add("sponsored-until", warp.sponsoredUntil().map(Views::isoText).orElse(JsonNull.INSTANCE));
        json.add("rent-paid-until", warp.rentPaidUntil().map(Views::isoText).orElse(JsonNull.INSTANCE));
        instant(json, "created-at", warp.createdAt());
        instant(json, "updated-at", warp.updatedAt());
        return json;
    }

    private static void addCounts(JsonObject json, UxmPlayerWarp warp) {
        json.addProperty("average-rating", warp.averageRating());
        json.addProperty("rating-count", warp.ratingCount());
        json.addProperty("visits", warp.visits());
        json.addProperty("unique-visitors", warp.uniqueVisitors());
        json.addProperty("favourites", warp.favourites());
    }

    public static JsonElement kit(UxmKit kit) {
        JsonObject json = new JsonObject();
        json.addProperty("id", kit.id());
        json.addProperty("display-name", kit.displayName());
        duration(json, "cooldown-seconds", kit.cooldown());
        json.addProperty("one-time", kit.oneTime());
        json.addProperty("requires-permission", kit.requiresPermission());
        text(json, "permission-node", kit.permissionNode());
        json.add("cost", kit.cost().map(Views::money).orElse(JsonNull.INSTANCE));
        text(json, "category", kit.category());
        json.addProperty("item-count", kit.itemCount());
        json.addProperty("first-join", kit.firstJoin());
        json.add("stock-limit", number(kit.stockLimit()));
        return json;
    }

    public static JsonElement vault(UxmVault vault) {
        JsonObject json = new JsonObject();
        json.addProperty("owner-id", vault.ownerId().toString());
        json.addProperty("index", vault.index());
        text(json, "display-name", vault.displayName());
        text(json, "icon", vault.icon());
        json.addProperty("label", vault.label());
        return json;
    }

    public static JsonElement issuer(UxmIssuer issuer) {
        JsonObject json = new JsonObject();
        json.add("uuid", issuer.uuid().map(id -> text(id.toString())).orElse(JsonNull.INSTANCE));
        json.addProperty("name", issuer.name());
        json.addProperty("console", issuer.isConsole());
        return json;
    }

    public static JsonElement sanction(UxmSanction sanction) {
        JsonObject json = new JsonObject();
        json.addProperty("kind", sanction.kind().name());
        json.addProperty("player-id", sanction.playerId().toString());
        json.add("issuer", issuer(sanction.issuer()));
        text(json, "reason", sanction.reason());
        instant(json, "issued-at", sanction.issuedAt());
        json.add("expires-at", sanction.expiresAt().map(Views::isoText).orElse(JsonNull.INSTANCE));
        json.addProperty("permanent", sanction.isPermanent());
        return json;
    }

    public static JsonElement warn(UxmWarn warn) {
        JsonObject json = new JsonObject();
        json.add("issuer", issuer(warn.issuer()));
        text(json, "reason", warn.reason());
        instant(json, "issued-at", warn.issuedAt());
        json.add("expires-at", warn.expiresAt().map(Views::isoText).orElse(JsonNull.INSTANCE));
        return json;
    }

    public static JsonElement sanctionRecord(UxmSanctionRecord record) {
        JsonObject json = new JsonObject();
        json.addProperty("action", record.action().name());
        json.addProperty("player-id", record.playerId().toString());
        json.add("actor", issuer(record.actor()));
        text(json, "reason", record.reason());
        instant(json, "at", record.at());
        json.add("expiry", record.expiry().map(Views::isoText).orElse(JsonNull.INSTANCE));
        return json;
    }

    public static JsonElement presence(UxmPresence presence) {
        JsonObject json = new JsonObject();
        json.addProperty("player-id", presence.playerId().toString());
        json.addProperty("afk", presence.afk());
        text(json, "afk-reason", presence.afkReason());
        instant(json, "last-activity", presence.lastActivity());
        return json;
    }

    public static JsonElement playerState(UxmPlayerState state) {
        JsonObject json = new JsonObject();
        json.addProperty("player-id", state.playerId().toString());
        json.addProperty("god-mode", state.godMode());
        json.addProperty("flying", state.flying());
        json.add("game-mode", state.gameMode().map(mode -> text(mode.name())).orElse(JsonNull.INSTANCE));
        json.addProperty("walk-speed", state.walkSpeed());
        json.addProperty("fly-speed", state.flySpeed());
        return json;
    }

    public static JsonElement playtime(UxmPlaytime playtime) {
        JsonObject json = new JsonObject();
        duration(json, "today-active-seconds", playtime.todayActive());
        duration(json, "today-afk-seconds", playtime.todayAfk());
        duration(json, "week-active-seconds", playtime.weekActive());
        duration(json, "week-afk-seconds", playtime.weekAfk());
        duration(json, "month-active-seconds", playtime.monthActive());
        duration(json, "month-afk-seconds", playtime.monthAfk());
        duration(json, "total-active-seconds", playtime.totalActive());
        duration(json, "total-afk-seconds", playtime.totalAfk());
        return json;
    }

    public static JsonElement world(UxmWorld world) {
        JsonObject json = new JsonObject();
        json.addProperty("name", world.name());
        text(json, "alias", world.alias());
        json.addProperty("display-name", world.displayName());
        json.addProperty("environment", world.environment());
        json.addProperty("generation", world.generation());
        json.add("seed", number(world.seed()));
        json.addProperty("auto-load", world.autoLoad());
        json.addProperty("loaded", world.loaded());
        json.addProperty("player-count", world.playerCount());
        return json;
    }

    public static JsonElement rank(UxmRank rank) {
        JsonObject json = new JsonObject();
        json.addProperty("id", rank.id());
        json.addProperty("display-name", rank.displayName());
        json.addProperty("order", rank.order());
        json.addProperty("cost", rank.cost());
        return json;
    }

    public static JsonElement rankStanding(UxmRankStanding standing) {
        JsonObject json = new JsonObject();
        json.add("rank", rank(standing.rank()));
        json.add("next", standing.next().map(Views::rank).orElse(JsonNull.INSTANCE));
        json.addProperty("prestige", standing.prestige());
        json.addProperty("at-top", standing.atTop());
        return json;
    }

    public static JsonElement discordLink(UxmDiscordLink link) {
        JsonObject json = new JsonObject();
        json.addProperty("player-id", link.playerId().toString());
        json.addProperty("discord-id", link.discordId());
        json.addProperty("linked-at", link.linkedAt().toString());
        return json;
    }

    public static JsonElement securityStatus(UxmSecurityStatus status) {
        JsonObject json = new JsonObject();
        json.addProperty("player-id", status.playerId().toString());
        json.addProperty("enrolled", status.enrolled());
        json.addProperty("totp-enabled", status.totpEnabled());
        json.addProperty("pin-set", status.pinSet());
        json.add("enrolled-at", status.enrolledAt().map(Views::isoText).orElse(JsonNull.INSTANCE));
        json.addProperty("locked-out", status.lockedOut());
        return json;
    }

    public static JsonElement snapshot(UxmSnapshot snapshot) {
        JsonObject json = new JsonObject();
        json.addProperty("id", snapshot.id().toString());
        json.addProperty("owner-id", snapshot.ownerId().toString());
        json.addProperty("cause", snapshot.cause().name());
        json.addProperty("taken-at", snapshot.takenAt().toString());
        return json;
    }

    public static JsonElement region(UxmRegion region) {
        JsonObject json = new JsonObject();
        json.addProperty("world", region.world());
        json.addProperty("id", region.id());
        json.addProperty("priority", region.priority());
        json.add("owners", each(region.owners(), Views::text));
        json.add("members", each(region.members(), Views::text));
        JsonObject flags = new JsonObject();
        region.flags().forEach(flag -> flags.addProperty(flag.name(), flag.value()));
        json.add("flags", flags);
        return json;
    }

    public static JsonElement trade(UxmTrade trade) {
        JsonObject json = new JsonObject();
        json.addProperty("id", trade.id().toString());
        json.addProperty("initiator-id", trade.initiatorId().toString());
        json.addProperty("initiator-name", trade.initiatorName());
        json.addProperty("partner-id", trade.partnerId().toString());
        json.addProperty("partner-name", trade.partnerName());
        json.addProperty("initiator-confirmed", trade.initiatorConfirmed());
        json.addProperty("partner-confirmed", trade.partnerConfirmed());
        json.addProperty("both-confirmed", trade.bothConfirmed());
        return json;
    }

    public static JsonElement voteTotals(UxmVoteTotals totals) {
        JsonObject json = new JsonObject();
        json.addProperty("all-time", totals.allTime());
        json.addProperty("daily", totals.daily());
        json.addProperty("weekly", totals.weekly());
        json.addProperty("monthly", totals.monthly());
        json.addProperty("current-streak", totals.currentStreak());
        json.addProperty("best-streak", totals.bestStreak());
        return json;
    }

    public static JsonElement voteRank(UxmVoteRank rank) {
        JsonObject json = new JsonObject();
        json.addProperty("rank", rank.rank());
        json.addProperty("player-id", rank.playerId().toString());
        json.addProperty("player-name", rank.playerName());
        json.addProperty("votes", rank.votes());
        return json;
    }

    public static JsonElement voteParty(UxmVoteParty party) {
        JsonObject json = new JsonObject();
        json.addProperty("count", party.count());
        json.addProperty("threshold", party.threshold());
        json.addProperty("remaining", party.remaining());
        return json;
    }

    public static JsonElement mail(UxmMail mail) {
        JsonObject json = new JsonObject();
        json.addProperty("id", mail.id());
        json.addProperty("recipient-id", mail.recipientId().toString());
        json.add("sender-id", mail.senderId().map(id -> text(id.toString())).orElse(JsonNull.INSTANCE));
        json.addProperty("sender-name", mail.senderName());
        json.addProperty("from-player", mail.fromPlayer());
        json.addProperty("body", mail.body());
        instant(json, "sent-at", mail.sentAt());
        json.addProperty("read", mail.read());
        return json;
    }

    public static JsonElement ignore(UxmIgnore ignore) {
        JsonObject json = new JsonObject();
        json.addProperty("player-id", ignore.playerId().toString());
        json.addProperty("scope", ignore.scope().name());
        return json;
    }

    public static JsonElement teleportRequest(UxmTeleportRequest request) {
        JsonObject json = new JsonObject();
        json.addProperty("requester-id", request.requesterId().toString());
        json.addProperty("requester-name", request.requesterName());
        json.addProperty("target-id", request.targetId().toString());
        json.addProperty("target-name", request.targetName());
        json.addProperty("direction", request.direction().name());
        json.addProperty("mover-id", request.moverId().toString());
        json.addProperty("anchor-id", request.anchorId().toString());
        instant(json, "expires-at", request.expiresAt());
        return json;
    }

    public static JsonElement backPoint(UxmBackPoint point) {
        JsonObject json = new JsonObject();
        json.add("location", location(point.location()));
        json.addProperty("cause", point.cause().name());
        instant(json, "captured-at", point.capturedAt());
        return json;
    }

    /** A list of currency ids, which is the one query that answers plain strings. */
    public static JsonArray strings(List<String> values) {
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        return array;
    }

    private static void text(JsonObject json, String name, Optional<String> value) {
        json.add(name, value.map(Views::text).orElse(JsonNull.INSTANCE));
    }

    private static JsonElement text(String value) {
        return new JsonPrimitive(value);
    }

    private static JsonElement isoText(Instant when) {
        return text(when.toString());
    }

    private static void instant(JsonObject json, String name, Instant when) {
        json.addProperty(name, when.toString());
    }

    private static void duration(JsonObject json, String name, Duration length) {
        json.addProperty(name, length.toSeconds());
    }
}
