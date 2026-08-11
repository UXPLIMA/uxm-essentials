package com.uxplima.uxmessentials.discordlink.adapter.outbound;

import java.util.Objects;

import com.uxplima.uxmessentials.api.link.DiscordLinkConfirmation;
import com.uxplima.uxmessentials.discordlink.application.ConfirmLink;
import com.uxplima.uxmessentials.discordlink.domain.ConfirmedLink;
import com.uxplima.uxmessentials.discordlink.domain.DiscordId;
import com.uxplima.uxmessentials.discordlink.domain.DiscordLinkError;
import com.uxplima.uxmessentials.discordlink.domain.LinkCode;
import com.uxplima.uxmessentials.discordlink.domain.event.AccountLinked;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The host-side implementation of the {@code :api} {@link DiscordLinkConfirmation} seam, registered into the
 * Bukkit {@code ServicesManager} so the optional Discord bridge redeems a {@code /link} code through the
 * {@link ConfirmLink} use case without any compile-time link to the host jar.
 *
 * <p>It maps the bridge's plain-string inputs to the domain value objects (rejecting a malformed code or id as
 * {@link DiscordLinkConfirmation.Result#INVALID} rather than letting the constructor exception escape across the
 * seam), runs the use case, and maps the {@link Result} back to an {@link DiscordLinkConfirmation.Outcome},
 * resolving the bound player's name on success, and publishing the {@link AccountLinked} fact the published API
 * bridges. The bridge calls this on JDA's own thread (off any server tick); the use case and the jOOQ store behind
 * it are thread-safe for that off-tick call.
 */
@NullMarked
public final class ConfirmLinkService implements DiscordLinkConfirmation {

    private final ConfirmLink confirmLink;
    private final PlayerLookup players;
    private final DomainEventPublisher events;

    public ConfirmLinkService(ConfirmLink confirmLink, PlayerLookup players, DomainEventPublisher events) {
        this.confirmLink = Objects.requireNonNull(confirmLink, "confirmLink");
        this.players = Objects.requireNonNull(players, "players");
        this.events = Objects.requireNonNull(events, "events");
    }

    @Override
    public Outcome confirm(String code, String discordId) {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(discordId, "discordId");
        LinkCode parsedCode;
        DiscordId parsedId;
        try {
            parsedCode = LinkCode.of(code);
            parsedId = DiscordId.of(discordId);
        } catch (IllegalArgumentException malformed) {
            return Outcome.failure(Result.INVALID);
        }
        com.uxplima.uxmessentials.shared.domain.Result<ConfirmedLink, DiscordLinkError> result =
                confirmLink.confirm(parsedCode, parsedId);
        return result.isOk() ? linked(result.orElseThrow()) : Outcome.failure(map(result.errorOrThrow()));
    }

    private Outcome linked(ConfirmedLink link) {
        String name = players.findByUuid(link.player())
                .map(PlayerRef::name)
                .orElseGet(() -> link.player().toString());
        // Published from here rather than from the use case because this is where a name can be put to the uuid:
        // the code is redeemed on Discord, and the fact reaches listeners as a player event.
        events.publish(new AccountLinked(new PlayerRef(link.player(), name), link.discordId(), link.linkedAt()));
        return Outcome.linked(name);
    }

    private static Result map(DiscordLinkError error) {
        return switch (error) {
            case NO_PENDING_CODE -> Result.NO_CODE;
            case CODE_EXPIRED -> Result.EXPIRED;
            case DISCORD_ALREADY_LINKED -> Result.ALREADY_LINKED;
            case NOT_LINKED -> Result.NO_CODE;
        };
    }
}
