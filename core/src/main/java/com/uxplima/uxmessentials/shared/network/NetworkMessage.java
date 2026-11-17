package com.uxplima.uxmessentials.shared.network;

/**
 * A cross-server sync frame carried over the proxy bus. Pure Java (no Bukkit, no Velocity) so the same
 * type hierarchy is shared by the backend bus client (the bukkit adapter) and the proxy broker (the velocity
 * adapter), using a pure-Java codec pattern. The hierarchy is {@code sealed}; the
 * {@link NetworkMessageCodec} is the only encoder/decoder and every variant has a stable
 * {@link MessageType wire tag}.
 *
 * <p>Every message carries an {@link #originServer() origin server id}: the {@code server-id} of the backend
 * that produced it. The broker fans a frame out to every backend except its origin, and a backend's bus
 * client drops any frame whose origin equals its own id. Together these two checks are the replication-loop
 * sentinel. A mutation made on {@code survival-1} reaches the other
 * backends once and never echoes back to bounce around the cluster.
 *
 * <p>A frame is a <strong>notification, not the source of truth</strong>. The shared database holds the authoritative
 * state; a frame tells a peer "owner X's homes/balance/etc.
 * changed on me, drop your cached copy" so the peer re-reads the fresh row on its next access. It never
 * carries a full aggregate to be written blindly: that would race the DB and risk a lost update.
 */
public sealed interface NetworkMessage
        permits BalanceChanged,
                HomeChanged,
                WarpChanged,
                VaultChanged,
                BanChanged,
                MuteChanged,
                PlayerWarpChanged,
                HologramChanged,
                NpcChanged,
                IgnoreChanged,
                ServerPing,
                VotePartyFired,
                VoteCounterChanged,
                TradeSignalFrame,
                VanishStateChanged,
                GuardChanged,
                IpBanChanged,
                RankChanged,
                PresenceChanged,
                PresenceSnapshot,
                PrivateMessageFrame,
                PrivateMessageAnswerFrame,
                HelpOpFrame,
                StaffChatFrame,
                JailChanged,
                JailHopFailed,
                TeleportRequestFrame,
                TeleportAnswerFrame,
                StaffTeleportFrame,
                WorldChanged,
                FreezeChanged,
                FreezeReset,
                AllBalancesChanged,
                PermissionQueryFrame,
                ProxyConnectionFrame,
                PlayerSkinChanged,
                PlayerSettingsChanged,
                MailArrivedFrame,
                RankActionsKept,
                InventoryRestoreKept,
                BroadcastFrame,
                VoteRewardsQueued,
                StaffActionFrame {

    /** The {@code server-id} of the backend that produced this frame; the loop sentinel keys on it. */
    String originServer();

    /** The stable wire tag the codec writes first so a decoder can dispatch to the right variant. */
    MessageType type();

    /**
     * The closed set of wire tags. Each carries an explicit {@link #wireTag()} byte that is the on-wire
     * discriminator (a stable assigned number, never the enum ordinal) so a variant can be added without
     * renumbering the others and reordering the constants cannot silently change the wire format.
     */
    enum MessageType {
        BALANCE_CHANGED(1),
        HOME_CHANGED(2),
        WARP_CHANGED(3),
        VAULT_CHANGED(4),
        SERVER_PING(5),
        VOTE_PARTY_FIRED(6),
        VOTE_COUNTER_CHANGED(7),
        BAN_CHANGED(8),
        MUTE_CHANGED(9),
        PLAYER_WARP_CHANGED(10),
        HOLOGRAM_CHANGED(11),
        NPC_CHANGED(12),
        IGNORE_CHANGED(13),
        TRADE_SIGNAL(14),
        VANISH_STATE_CHANGED(15),
        GUARD_CHANGED(16),
        RANK_CHANGED(17),
        IP_BAN_CHANGED(18),
        PRESENCE_CHANGED(19),
        PRESENCE_SNAPSHOT(20),
        PRIVATE_MESSAGE(21),
        PRIVATE_MESSAGE_ANSWER(22),
        HELP_OP(23),
        STAFF_CHAT(24),
        TELEPORT_REQUEST(25),
        TELEPORT_ANSWER(26),
        STAFF_TELEPORT(27),
        JAIL_CHANGED(28),
        JAIL_HOP_FAILED(29),
        WORLD_CHANGED(30),
        FREEZE_CHANGED(31),
        FREEZE_RESET(32),
        ALL_BALANCES_CHANGED(33),
        PERMISSION_QUERY(34),
        PROXY_CONNECTION(35),
        PLAYER_SKIN_CHANGED(36),
        PLAYER_SETTINGS_CHANGED(37),
        MAIL_ARRIVED(38),
        RANK_ACTIONS_KEPT(39),
        INVENTORY_RESTORE_KEPT(40),
        BROADCAST(43),
        VOTE_REWARDS_QUEUED(44),
        STAFF_ACTION(45);

        private final byte wireTag;

        MessageType(int wireTag) {
            this.wireTag = (byte) wireTag;
        }

        /** The stable on-wire discriminator byte for this type. */
        public byte wireTag() {
            return wireTag;
        }

        /**
         * The type carrying {@code wireTag}.
         *
         * @throws UnknownMessageTypeException if no type uses that tag: a frame from a newer peer, or a malformed one
         */
        public static MessageType fromWireTag(byte wireTag) {
            for (MessageType type : values()) {
                if (type.wireTag == wireTag) {
                    return type;
                }
            }
            throw new UnknownMessageTypeException(wireTag);
        }
    }
}
