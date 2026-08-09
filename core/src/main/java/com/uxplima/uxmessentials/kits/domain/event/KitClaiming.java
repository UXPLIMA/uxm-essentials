package com.uxplima.uxmessentials.kits.domain.event;

import java.util.Objects;

import com.uxplima.uxmessentials.kits.domain.KitId;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A kit is about to be handed out.
 *
 * <p>Asked once the recipient is known to be allowed it and off cooldown, and before anything is charged or any item
 * is placed, so a refusal costs them neither money nor a cooldown.
 *
 * @param kit which kit
 * @param recipient who would receive it
 * @param actor who ran the command, the same as the recipient in the ordinary case
 */
public record KitClaiming(KitId kit, PlayerRef recipient, PlayerRef actor) implements KitProposal {

    public KitClaiming {
        Objects.requireNonNull(kit, "kit");
        Objects.requireNonNull(recipient, "recipient");
        Objects.requireNonNull(actor, "actor");
    }
}
