package com.uxplima.uxmessentials.communication.adapter.outbound;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

import org.bukkit.Server;

import com.uxplima.uxmessentials.shared.adapter.outbound.hud.ReaderText;
import com.uxplima.uxmessentials.shared.adapter.outbound.permission.LuckPermsAccess;
import net.luckperms.api.LuckPermsProvider;
import org.jspecify.annotations.NullMarked;

/**
 * Chooses the {@link ChatMetaSource} for the chat renderer. LuckPerms stays a soft dependency: the shared
 * {@link LuckPermsAccess} guard answers whether it is usable, and the {@code net.luckperms} symbols (via
 * {@link LuckPermsChatMetaSource}) are reached only past that guard, so a server without LuckPerms never resolves
 * those classes and falls back to {@link ChatMetaSource#empty()}, empty affixes and the default (non-group) format.
 */
@NullMarked
public final class ChatMetaSources {

    private ChatMetaSources() {}

    /** Each player's chat prefix and suffix, the ones a {@code {prefix}} or {@code {suffix}} token of a HUD line shows. */
    public static Function<UUID, ReaderText.Affix> affixes(Server server) {
        ChatMetaSource meta = create(server);
        return who -> {
            ChatMeta held = meta.metaFor(who);
            return new ReaderText.Affix(held.prefix(), held.suffix());
        };
    }

    /** The LuckPerms-backed source when LuckPerms is installed, otherwise the empty fallback. */
    public static ChatMetaSource create(Server server) {
        Objects.requireNonNull(server, "server");
        if (!LuckPermsAccess.isPresent(server)) {
            return ChatMetaSource.empty();
        }
        // Loading LuckPermsChatMetaSource (and thus the net.luckperms symbols) only happens past the
        // plugin-present guard, so a server without LuckPerms never resolves those classes.
        return new LuckPermsChatMetaSource(LuckPermsProvider.get());
    }
}
