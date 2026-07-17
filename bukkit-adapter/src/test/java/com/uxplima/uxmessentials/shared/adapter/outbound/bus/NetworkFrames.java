package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.network.BalanceChanged;
import com.uxplima.uxmessentials.shared.network.BanChanged;
import com.uxplima.uxmessentials.shared.network.HologramChanged;
import com.uxplima.uxmessentials.shared.network.HomeChanged;
import com.uxplima.uxmessentials.shared.network.IgnoreChanged;
import com.uxplima.uxmessentials.shared.network.MuteChanged;
import com.uxplima.uxmessentials.shared.network.NetworkMessage;
import com.uxplima.uxmessentials.shared.network.NpcChanged;
import com.uxplima.uxmessentials.shared.network.PlayerWarpChanged;
import com.uxplima.uxmessentials.shared.network.ServerPing;
import com.uxplima.uxmessentials.shared.network.TradeSignalFrame;
import com.uxplima.uxmessentials.shared.network.VanishStateChanged;
import com.uxplima.uxmessentials.shared.network.VaultChanged;
import com.uxplima.uxmessentials.shared.network.VoteCounterChanged;
import com.uxplima.uxmessentials.shared.network.VotePartyFired;
import com.uxplima.uxmessentials.shared.network.WarpChanged;

/**
 * One representative frame of every {@link NetworkMessage} wire type, for the transport-parity sweep. Each
 * frame carries the given origin so the loop sentinel treats it as a peer's mutation. The list size and the
 * set of {@link NetworkMessage.MessageType}s it covers are asserted against
 * {@link NetworkMessage.MessageType#values()} by the parity test, so a new wire type that is not added here
 * fails the sweep rather than slipping through unproven.
 */
final class NetworkFrames {

    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-0000000000bb");

    private NetworkFrames() {}

    static List<NetworkMessage> oneOfEach(String origin) {
        return List.of(
                new BalanceChanged(origin, OWNER, "coins"),
                new HomeChanged(origin, OWNER),
                new WarpChanged(origin, "spawn"),
                new VaultChanged(origin, OWNER, 3),
                new ServerPing(origin, 1_717_000_000_000L),
                new VotePartyFired(origin, 25),
                new VoteCounterChanged(origin),
                new BanChanged(origin, TARGET),
                new MuteChanged(origin, TARGET),
                new PlayerWarpChanged(origin, OWNER),
                new HologramChanged(origin, "lobby-board"),
                new NpcChanged(origin, "guide"),
                new IgnoreChanged(origin, OWNER),
                new TradeSignalFrame(
                        origin,
                        UUID.fromString("00000000-0000-0000-0000-0000000000cc"),
                        "READY",
                        OWNER,
                        "Alice",
                        TARGET,
                        "Bob"),
                new VanishStateChanged(origin, OWNER, "Alice", true, 3));
    }
}
