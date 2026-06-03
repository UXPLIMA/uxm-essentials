/**
 * The vote context's inbound listeners: {@code VotifierListener} bridges the upstream Votifier vote event
 * (reached reflectively behind a plugin-present guard, so the module ships without the dependency) into the
 * {@code HandleVote} use case, and {@code VoteJoinListener} pays out an offline voter's queued rewards on
 * their next join. Both hop their work off the inbound thread through the kernel {@code Scheduler} port.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.vote.adapter.inbound.listener;
