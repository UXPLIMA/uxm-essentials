package com.uxplima.uxmessentials.vaults.application;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Renders the vaults context's player-facing feedback by resolving a {@link VaultsMessageKey} in the viewer's
 * locale and delivering it through the {@link MessageSink}. Every command path that tells the player something
 * — the vault opened, the listing of owned vaults, a quota rejection, the admin override notice — goes through
 * one of these methods, so there is no inline player-facing text anywhere in the context.
 */
public final class VaultNotifier {

    private final Messages messages;
    private final MessageSink sink;

    public VaultNotifier(Messages messages, MessageSink sink) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    /** Tell {@code viewer} the vault at {@code index} opened. */
    public void opened(PlayerRef viewer, int index) {
        send(viewer, VaultsMessageKey.VAULT_OPENED, Map.of("index", Integer.toString(index)));
    }

    /** Tell {@code actor} they opened {@code owner}'s vault at {@code index} (the admin override). */
    public void adminOpened(PlayerRef actor, PlayerRef owner, int index) {
        send(
                actor,
                VaultsMessageKey.VAULT_ADMIN_OPENED,
                Map.of("player", owner.name(), "index", Integer.toString(index)));
    }

    /** List {@code owner}'s vault numbers as a header plus one comma-joined entry line. */
    public void list(PlayerRef viewer, List<Integer> indices) {
        send(viewer, VaultsMessageKey.VAULT_LIST_HEADER, Map.of("count", Integer.toString(indices.size())));
        String joined = indices.stream().map(Object::toString).collect(Collectors.joining(", "));
        send(viewer, VaultsMessageKey.VAULT_LIST_ENTRY, Map.of("vaults", joined));
    }

    /** Tell {@code viewer} they own no vaults yet. */
    public void noneOwned(PlayerRef viewer) {
        send(viewer, VaultsMessageKey.VAULT_NONE_OWNED, Map.of());
    }

    /** Tell {@code viewer} the requested index exceeds their vault-count quota. */
    public void amountExceeded(PlayerRef viewer, int index) {
        send(viewer, VaultsMessageKey.VAULT_AMOUNT_EXCEEDED, Map.of("index", Integer.toString(index)));
    }

    /** Tell {@code actor} the audited player name resolved to nobody. */
    public void unknownTarget(PlayerRef actor, String name) {
        send(actor, VaultsMessageKey.VAULT_ADMIN_UNKNOWN_TARGET, Map.of("player", name));
    }

    /** Tell {@code sender} the command is player-only (a console actor cannot open a GUI). */
    public void playersOnly(PlayerRef sender) {
        send(sender, VaultsMessageKey.VAULT_PLAYERS_ONLY, Map.of());
    }

    private void send(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
        sink.deliver(viewer, messages.resolve(viewer, key, placeholders));
    }
}
