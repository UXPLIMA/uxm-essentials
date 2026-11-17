package com.uxplima.uxmessentials.shared.network;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * The pure-Java binary codec for every {@link NetworkMessage}. Encodes a frame to a {@code byte[]} and
 * decodes one back, with no dependency on Bukkit, Velocity, or any serialization framework, just
 * {@link DataOutputStream}/{@link DataInputStream} over a byte array. Both the backend bus client and the
 * proxy broker share this one class so the wire format cannot drift between the two sides (a single
 * pure-codec shared by both ends).
 *
 * <p>Wire layout: a one-byte protocol {@link #VERSION}, then a one-byte {@link NetworkMessage.MessageType}
 * wire tag, then the variant's fields. The version byte lets a future format change be detected rather than
 * silently mis-decoded; {@code bus-channel} and frame-codec compatibility are guaranteed across patch
 * versions only, so a mismatched version is rejected loudly. A field added to an existing variant goes at the end
 * of its body and is read only when present: an older decoder ignores the bytes after the fields it knows.
 * UUIDs are written as two longs; strings via {@link DataOutputStream#writeUTF}.
 */
public final class NetworkMessageCodec {

    /** The protocol version written as the first wire byte; bump only on an incompatible format change. */
    public static final byte VERSION = 1;

    /**
     * The most players one presence snapshot part may name. Far above what fits in a part, it only stops a corrupt
     * count from allocating an enormous list before the truncated body is noticed.
     */
    static final int MAX_SNAPSHOT_PLAYERS = 100_000;

    /** The most parts one presence roster may be split into; a corrupt count beyond it is refused. */
    static final int MAX_SNAPSHOT_PARTS = 10_000;

    private NetworkMessageCodec() {}

    /** Encode {@code message} to its wire bytes. */
    public static byte[] encode(NetworkMessage message) {
        Objects.requireNonNull(message, "message");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(VERSION);
            out.writeByte(message.type().wireTag());
            writeBody(out, message);
        } catch (IOException impossible) {
            // A ByteArrayOutputStream never performs I/O that can fail; treat any failure as a defect.
            throw new UncheckedIOException("encoding a network message to a byte array failed", impossible);
        }
        return bytes.toByteArray();
    }

    /**
     * Decode {@code frame} back into a {@link NetworkMessage}.
     *
     * @throws IllegalArgumentException if the frame is empty, carries an unknown version, or names an
     *     unknown message type. A malformed or version-incompatible frame is rejected, never half-applied
     */
    public static NetworkMessage decode(byte[] frame) {
        Objects.requireNonNull(frame, "frame");
        if (frame.length == 0) {
            throw new IllegalArgumentException("cannot decode an empty network frame");
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(frame))) {
            byte version = in.readByte();
            if (version != VERSION) {
                throw new IllegalArgumentException("unsupported network frame version: " + version);
            }
            NetworkMessage.MessageType type = NetworkMessage.MessageType.fromWireTag(in.readByte());
            return readBody(in, type);
        } catch (IOException truncated) {
            throw new IllegalArgumentException("network frame was truncated or malformed", truncated);
        }
    }

    /**
     * The origin server of {@code frame}, read from the header alone. The proxy routes on this, so it relays a frame
     * whose type it does not know yet instead of dropping it: the origin sits right after the version and type
     * bytes in every frame, whatever its type.
     *
     * @throws IllegalArgumentException if the frame is truncated or carries an unknown version
     */
    public static String readOrigin(byte[] frame) {
        Objects.requireNonNull(frame, "frame");
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(frame))) {
            byte version = in.readByte();
            if (version != VERSION) {
                throw new IllegalArgumentException("unsupported network frame version: " + version);
            }
            in.readByte();
            return in.readUTF();
        } catch (IOException truncated) {
            throw new IllegalArgumentException("network frame header was truncated or malformed", truncated);
        }
    }

    /**
     * Whether {@code frame} is a {@link ServerPing}, read from the header alone so the proxy can answer a heartbeat
     * without decoding the body. A frame of another version, or too short to carry a type, is not one.
     */
    public static boolean isServerPing(byte[] frame) {
        Objects.requireNonNull(frame, "frame");
        return frame.length >= 2 && frame[0] == VERSION && frame[1] == NetworkMessage.MessageType.SERVER_PING.wireTag();
    }

    /**
     * Whether {@code frame} is of {@code type}, read from the header alone, so the proxy can pick out the frames it
     * reads without decoding the rest. A frame of another version, or too short to carry a type, is of none.
     */
    public static boolean isType(byte[] frame, NetworkMessage.MessageType type) {
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(type, "type");
        return frame.length >= 2 && frame[0] == VERSION && frame[1] == type.wireTag();
    }

    /** How many bytes {@link DataOutputStream#writeUTF} spends on {@code text}, its two-byte length included. */
    static int utfBytes(String text) {
        int bytes = 2;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0x0001 && c <= 0x007F) {
                bytes += 1;
            } else if (c <= 0x07FF) {
                bytes += 2;
            } else {
                bytes += 3;
            }
        }
        return bytes;
    }

    private static void writeBody(DataOutputStream out, NetworkMessage message) throws IOException {
        out.writeUTF(message.originServer());
        switch (message) {
            case BalanceChanged balance -> {
                writeUuid(out, balance.owner());
                out.writeUTF(balance.currencyId());
            }
            case HomeChanged home -> writeUuid(out, home.owner());
            case WarpChanged warp -> out.writeUTF(warp.warp());
            case VaultChanged vault -> {
                writeUuid(out, vault.owner());
                out.writeInt(vault.vaultIndex());
            }
            case BanChanged ban -> writeUuid(out, ban.target());
            case MuteChanged mute -> writeUuid(out, mute.target());
            case PlayerWarpChanged playerWarp -> writeUuid(out, playerWarp.owner());
            case HologramChanged hologram -> out.writeUTF(hologram.name());
            case NpcChanged npc -> out.writeUTF(npc.name());
            case IgnoreChanged ignore -> writeUuid(out, ignore.owner());
            case PlayerSettingsChanged settings -> writeUuid(out, settings.player());
            case ServerPing ping -> {
                out.writeLong(ping.epochMillis());
                // Written last: a peer on the older layout stops reading after the timestamp and never sees it.
                out.writeLong(ping.bootNonce());
            }
            case VotePartyFired party -> writeParty(out, party);
            case VoteCounterChanged counter -> {
                // origin already written above; the counter frame carries no further body.
            }
            case TradeSignalFrame trade -> {
                writeUuid(out, trade.tradeId());
                out.writeUTF(trade.signal());
                writeUuid(out, trade.fromUuid());
                out.writeUTF(trade.fromName());
                writeUuid(out, trade.toUuid());
                out.writeUTF(trade.toName());
            }
            case VanishStateChanged vanish -> {
                writeUuid(out, vanish.player());
                out.writeUTF(vanish.playerName());
                out.writeBoolean(vanish.vanished());
                out.writeInt(vanish.level());
                // Written last: a peer on the older layout stops reading after the level and never sees it.
                out.writeBoolean(vanish.departed());
                out.writeLong(vanish.run());
                out.writeLong(vanish.sequence());
            }
            case GuardChanged guard -> {
                out.writeUTF(guard.scope());
                out.writeUTF(guard.world());
                out.writeUTF(guard.region());
            }
            case IpBanChanged ipBan -> out.writeUTF(ipBan.ip());
            case RankChanged rank -> writeUuid(out, rank.player());
            case PresenceChanged presence -> {
                out.writeLong(presence.session());
                out.writeLong(presence.sequence());
                writeUuid(out, presence.player());
                out.writeUTF(presence.playerName());
                out.writeBoolean(presence.online());
            }
            case PresenceSnapshot snapshot -> {
                out.writeLong(snapshot.session());
                out.writeLong(snapshot.sequence());
                out.writeBoolean(snapshot.replyRequested());
                out.writeInt(snapshot.part());
                out.writeInt(snapshot.parts());
                out.writeInt(snapshot.players().size());
                for (PresenceSnapshot.Member member : snapshot.players()) {
                    writeUuid(out, member.player());
                    out.writeUTF(member.name());
                }
            }
            case PrivateMessageFrame privateMessage -> {
                writeUuid(out, privateMessage.messageId());
                out.writeUTF(privateMessage.targetServer());
                writeUuid(out, privateMessage.senderUuid());
                out.writeUTF(privateMessage.senderName());
                out.writeInt(privateMessage.senderSeeLevel());
                writeUuid(out, privateMessage.targetUuid());
                out.writeUTF(privateMessage.targetName());
                out.writeUTF(privateMessage.body());
                out.writeBoolean(privateMessage.reply());
            }
            case PrivateMessageAnswerFrame answer -> {
                writeUuid(out, answer.messageId());
                out.writeUTF(answer.outcome());
                writeUuid(out, answer.senderUuid());
                out.writeUTF(answer.senderName());
                writeUuid(out, answer.targetUuid());
                out.writeUTF(answer.targetName());
                out.writeUTF(answer.body());
                out.writeBoolean(answer.afk());
                out.writeUTF(answer.afkReason());
            }
            case HelpOpFrame helpOp -> {
                writeUuid(out, helpOp.requestId());
                writeUuid(out, helpOp.requesterUuid());
                out.writeUTF(helpOp.requesterName());
                out.writeUTF(helpOp.body());
            }
            case StaffChatFrame staffChat -> {
                writeUuid(out, staffChat.messageId());
                writeUuid(out, staffChat.senderUuid());
                out.writeUTF(staffChat.senderName());
                out.writeUTF(staffChat.message());
            }
            case JailChanged jail -> {
                writeUuid(out, jail.frameId());
                writeUuid(out, jail.target());
                out.writeBoolean(jail.jailed());
            }
            case JailHopFailed failed -> {
                writeUuid(out, failed.frameId());
                writeUuid(out, failed.target());
                out.writeUTF(failed.targetName());
                out.writeUTF(failed.jail());
            }
            case TeleportRequestFrame request -> {
                writeUuid(out, request.requestId());
                writeUuid(out, request.requesterUuid());
                out.writeUTF(request.requesterName());
                writeUuid(out, request.targetUuid());
                out.writeUTF(request.targetName());
                out.writeUTF(request.targetServer());
                out.writeUTF(request.direction());
                out.writeInt(request.seeLevel());
                out.writeLong(request.ttlMillis());
            }
            case TeleportAnswerFrame answer -> {
                writeUuid(out, answer.requestId());
                out.writeUTF(answer.answer());
                out.writeUTF(answer.toServer());
                writeUuid(out, answer.aboutUuid());
                out.writeUTF(answer.aboutName());
                out.writeUTF(answer.detail());
            }
            case StaffTeleportFrame staff -> {
                writeUuid(out, staff.requestId());
                writeUuid(out, staff.actorUuid());
                out.writeUTF(staff.actorName());
                writeUuid(out, staff.subjectUuid());
                out.writeUTF(staff.subjectName());
                writeUuid(out, staff.destinationUuid());
                out.writeUTF(staff.destinationName());
                out.writeUTF(staff.subjectServer());
                out.writeInt(staff.seeLevel());
            }
            case WorldChanged world -> {
                writeUuid(out, world.changeId());
                out.writeUTF(world.server());
                out.writeUTF(world.world());
            }
            case FreezeChanged freeze -> {
                writeUuid(out, freeze.frameId());
                writeUuid(out, freeze.target());
                out.writeUTF(freeze.targetName());
                out.writeBoolean(freeze.frozen());
                out.writeLong(freeze.changedAtMillis());
            }
            case FreezeReset reset -> out.writeLong(reset.clearedAtMillis());
            case AllBalancesChanged swept -> out.writeUTF(swept.currencyId());
            case PermissionQueryFrame query -> {
                writeUuid(out, query.queryId());
                out.writeUTF(query.toServer());
                writeUuid(out, query.player());
                out.writeUTF(query.node());
                out.writeUTF(query.step());
            }
            case MailArrivedFrame mail -> {
                writeUuid(out, mail.noticeId());
                writeUuid(out, mail.recipientUuid());
                out.writeUTF(mail.recipientName());
                out.writeUTF(mail.senderName());
            }
            case ProxyConnectionFrame connected -> {
                writeUuid(out, connected.frameId());
                writeUuid(out, connected.player());
                writeUuid(out, connected.connection());
                out.writeUTF(connected.server());
                out.writeBoolean(connected.switched());
            }
            case PlayerSkinChanged skin -> writeUuid(out, skin.player());
            case RankActionsKept kept -> writeUuid(out, kept.player());
            case InventoryRestoreKept kept -> writeUuid(out, kept.player());
        }
    }

    private static NetworkMessage readBody(DataInputStream in, NetworkMessage.MessageType type) throws IOException {
        String origin = in.readUTF();
        return switch (type) {
            case BALANCE_CHANGED -> new BalanceChanged(origin, readUuid(in), in.readUTF());
            case HOME_CHANGED -> new HomeChanged(origin, readUuid(in));
            case WARP_CHANGED -> new WarpChanged(origin, in.readUTF());
            case VAULT_CHANGED -> new VaultChanged(origin, readUuid(in), in.readInt());
            case BAN_CHANGED -> new BanChanged(origin, readUuid(in));
            case MUTE_CHANGED -> new MuteChanged(origin, readUuid(in));
            case PLAYER_WARP_CHANGED -> new PlayerWarpChanged(origin, readUuid(in));
            case HOLOGRAM_CHANGED -> new HologramChanged(origin, in.readUTF());
            case NPC_CHANGED -> new NpcChanged(origin, in.readUTF());
            case IGNORE_CHANGED -> new IgnoreChanged(origin, readUuid(in));
            case PLAYER_SETTINGS_CHANGED -> new PlayerSettingsChanged(origin, readUuid(in));
            case SERVER_PING -> new ServerPing(origin, in.readLong(), readOptionalLong(in, ServerPing.NO_NONCE));
            case VOTE_PARTY_FIRED -> readParty(in, origin);
            case VOTE_COUNTER_CHANGED -> new VoteCounterChanged(origin);
            case TRADE_SIGNAL ->
                new TradeSignalFrame(
                        origin, readUuid(in), in.readUTF(), readUuid(in), in.readUTF(), readUuid(in), in.readUTF());
            case VANISH_STATE_CHANGED ->
                new VanishStateChanged(
                        origin,
                        readUuid(in),
                        in.readUTF(),
                        in.readBoolean(),
                        in.readInt(),
                        readOptionalBoolean(in),
                        readOptionalLong(in, ServerPing.NO_NONCE),
                        readOptionalLong(in, VanishStateChanged.NO_SEQUENCE));
            case GUARD_CHANGED -> new GuardChanged(origin, in.readUTF(), in.readUTF(), in.readUTF());
            case IP_BAN_CHANGED -> new IpBanChanged(origin, in.readUTF());
            case RANK_CHANGED -> new RankChanged(origin, readUuid(in));
            case PRESENCE_CHANGED ->
                new PresenceChanged(origin, in.readLong(), in.readLong(), readUuid(in), in.readUTF(), in.readBoolean());
            case PRESENCE_SNAPSHOT -> readSnapshot(in, origin);
            case PRIVATE_MESSAGE ->
                new PrivateMessageFrame(
                        origin,
                        readUuid(in),
                        in.readUTF(),
                        readUuid(in),
                        in.readUTF(),
                        in.readInt(),
                        readUuid(in),
                        in.readUTF(),
                        in.readUTF(),
                        in.readBoolean());
            case PRIVATE_MESSAGE_ANSWER ->
                new PrivateMessageAnswerFrame(
                        origin,
                        readUuid(in),
                        in.readUTF(),
                        readUuid(in),
                        in.readUTF(),
                        readUuid(in),
                        in.readUTF(),
                        in.readUTF(),
                        in.readBoolean(),
                        in.readUTF());
            case HELP_OP -> new HelpOpFrame(origin, readUuid(in), readUuid(in), in.readUTF(), in.readUTF());
            case STAFF_CHAT -> new StaffChatFrame(origin, readUuid(in), readUuid(in), in.readUTF(), in.readUTF());
            case JAIL_CHANGED -> new JailChanged(origin, readUuid(in), readUuid(in), in.readBoolean());
            case JAIL_HOP_FAILED -> new JailHopFailed(origin, readUuid(in), readUuid(in), in.readUTF(), in.readUTF());
            case TELEPORT_REQUEST ->
                new TeleportRequestFrame(
                        origin,
                        readUuid(in),
                        readUuid(in),
                        in.readUTF(),
                        readUuid(in),
                        in.readUTF(),
                        in.readUTF(),
                        in.readUTF(),
                        in.readInt(),
                        in.readLong());
            case TELEPORT_ANSWER ->
                new TeleportAnswerFrame(
                        origin, readUuid(in), in.readUTF(), in.readUTF(), readUuid(in), in.readUTF(), in.readUTF());
            case STAFF_TELEPORT ->
                new StaffTeleportFrame(
                        origin,
                        readUuid(in),
                        readUuid(in),
                        in.readUTF(),
                        readUuid(in),
                        in.readUTF(),
                        readUuid(in),
                        in.readUTF(),
                        in.readUTF(),
                        in.readInt());
            case WORLD_CHANGED -> new WorldChanged(origin, readUuid(in), in.readUTF(), in.readUTF());
            case FREEZE_CHANGED ->
                new FreezeChanged(origin, readUuid(in), readUuid(in), in.readUTF(), in.readBoolean(), in.readLong());
            case FREEZE_RESET -> new FreezeReset(origin, in.readLong());
            case ALL_BALANCES_CHANGED -> new AllBalancesChanged(origin, in.readUTF());
            case PERMISSION_QUERY ->
                new PermissionQueryFrame(origin, readUuid(in), in.readUTF(), readUuid(in), in.readUTF(), in.readUTF());
            case MAIL_ARRIVED -> new MailArrivedFrame(origin, readUuid(in), readUuid(in), in.readUTF(), in.readUTF());
            case PROXY_CONNECTION ->
                new ProxyConnectionFrame(
                        origin, readUuid(in), readUuid(in), readUuid(in), in.readUTF(), in.readBoolean());
            case PLAYER_SKIN_CHANGED -> new PlayerSkinChanged(origin, readUuid(in));
            case RANK_ACTIONS_KEPT -> new RankActionsKept(origin, readUuid(in));
            case INVENTORY_RESTORE_KEPT -> new InventoryRestoreKept(origin, readUuid(in));
        };
    }

    /**
     * A trailing field a newer peer appends to a frame. A frame from an older peer ends before it, so the
     * absence reads as {@code absent} rather than as a truncated frame; this keeps a field addition compatible
     * in both directions without moving {@link #VERSION}.
     */
    private static long readOptionalLong(DataInputStream in, long absent) throws IOException {
        return in.available() >= Long.BYTES ? in.readLong() : absent;
    }

    /** A trailing flag a newer peer appends; a frame from an older peer ends before it and reads as {@code false}. */
    private static boolean readOptionalBoolean(DataInputStream in) throws IOException {
        return in.available() >= 1 && in.readBoolean();
    }

    /**
     * The reach and voters follow the threshold, and the party id follows them, so an older decoder, which stops
     * after the fields it knows, still reads the frame it knew.
     */
    private static void writeParty(DataOutputStream out, VotePartyFired party) throws IOException {
        out.writeInt(party.threshold());
        out.writeByte(party.reach().wireTag());
        if (party.reach() == VotePartyFired.Reach.VOTERS) {
            out.writeInt(party.voters().size());
            for (UUID voter : party.voters()) {
                writeUuid(out, voter);
            }
        }
        writeUuid(out, party.partyId());
        out.writeLong(party.firedAt().toEpochMilli());
        out.writeBoolean(party.bothCarriers());
    }

    private static VotePartyFired readParty(DataInputStream in, String origin) throws IOException {
        int threshold = in.readInt();
        if (in.available() == 0) {
            // A frame without the reach byte names nobody for a peer to reward.
            return new VotePartyFired(origin, VotePartyFired.NO_ID, threshold, VotePartyFired.Reach.NONE, Set.of());
        }
        VotePartyFired.Reach reach = VotePartyFired.Reach.fromWireTag(in.readByte());
        Set<UUID> voters = reach == VotePartyFired.Reach.VOTERS ? readVoters(in) : Set.of();
        UUID partyId = readOptionalUuid(in);
        if (in.available() < Long.BYTES + 1) {
            // A sender that predates the fire time: the party is never taken for a late copy.
            return new VotePartyFired(origin, partyId, threshold, reach, voters);
        }
        Instant firedAt = Instant.ofEpochMilli(in.readLong());
        return new VotePartyFired(origin, partyId, threshold, reach, voters, firedAt, in.readBoolean());
    }

    private static Set<UUID> readVoters(DataInputStream in) throws IOException {
        int count = in.readInt();
        if (count < 0) {
            throw new IOException("negative voter count: " + count);
        }
        Set<UUID> voters = new HashSet<>();
        for (int i = 0; i < count; i++) {
            voters.add(readUuid(in));
        }
        return voters;
    }

    /** A trailing id a newer peer appends; a frame from an older peer ends before it and reads as no id. */
    private static UUID readOptionalUuid(DataInputStream in) throws IOException {
        return in.available() >= 2 * Long.BYTES ? readUuid(in) : VotePartyFired.NO_ID;
    }

    private static PresenceSnapshot readSnapshot(DataInputStream in, String origin) throws IOException {
        long session = in.readLong();
        long sequence = in.readLong();
        boolean replyRequested = in.readBoolean();
        int part = in.readInt();
        int parts = in.readInt();
        if (parts < 1 || parts > MAX_SNAPSHOT_PARTS || part < 0 || part >= parts) {
            throw new IllegalArgumentException("presence snapshot names an impossible part: " + part + "/" + parts);
        }
        int count = in.readInt();
        if (count < 0 || count > MAX_SNAPSHOT_PLAYERS) {
            throw new IllegalArgumentException("presence snapshot names an impossible player count: " + count);
        }
        List<PresenceSnapshot.Member> players = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            players.add(new PresenceSnapshot.Member(readUuid(in), in.readUTF()));
        }
        return new PresenceSnapshot(origin, session, sequence, replyRequested, part, parts, players);
    }

    private static void writeUuid(DataOutputStream out, UUID uuid) throws IOException {
        out.writeLong(uuid.getMostSignificantBits());
        out.writeLong(uuid.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream in) throws IOException {
        long high = in.readLong();
        long low = in.readLong();
        return new UUID(high, low);
    }
}
