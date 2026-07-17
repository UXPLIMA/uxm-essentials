/**
 * The security context's inbound Bukkit listeners for the join-verification freeze: the join/quit edges that hand a
 * player to the {@code VerificationController} and clear a leaver's pending state, and the freeze listener that
 * cancels a still-unverified player's movement, commands, chat, interactions, and block edits until they prove a
 * second factor.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.security.adapter.inbound.listener;
