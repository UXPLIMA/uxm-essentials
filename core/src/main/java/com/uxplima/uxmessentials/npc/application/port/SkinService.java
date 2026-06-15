package com.uxplima.uxmessentials.npc.application.port;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.npc.domain.NpcSkin;

/**
 * Outbound port that resolves a Minecraft account's signed skin from its username — any account, online or
 * offline. This is what lets {@code /npc skin <name> name:<username>} dress a fake player as a player who has
 * never set foot on the server: the username is resolved to its profile out of band, and the signed
 * texture/signature pair comes back as a domain {@link NpcSkin}.
 *
 * <p>The lookup is a network round-trip, so the contract is strictly off-thread: the returned
 * {@link CompletableFuture} completes on whatever thread the adapter ran the fetch on, never the calling
 * thread, and a caller bridges back to a tick/region thread itself before touching game state. The future is
 * deliberately fail-soft — it completes with an empty {@link Optional} for every miss (an unknown username, a
 * profile carrying no skin, a network timeout, a parse failure) and never completes exceptionally, so a caller
 * only ever distinguishes "got a skin" from "did not", with no exception handling on the completion path. The
 * adapter logs the cause with the username for the operator; the empty result is the whole story the caller
 * needs.
 */
public interface SkinService {

    /**
     * Resolve {@code username}'s signed skin off-thread. Completes with the present skin when the name resolves
     * to an account that carries a texture, or with {@link Optional#empty()} for any miss or failure. Never
     * completes exceptionally; never blocks the calling thread.
     */
    CompletableFuture<Optional<NpcSkin>> fetchByName(String username);
}
