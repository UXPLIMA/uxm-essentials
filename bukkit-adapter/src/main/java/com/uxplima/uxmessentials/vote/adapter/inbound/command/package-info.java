/**
 * The vote context's inbound Brigadier commands: {@code VoteCommand} ({@code /vote}, with its
 * {@code testreward} subcommand that simulates a vote for the sender) and {@code VotePartyCommand}
 * ({@code /voteparty}, the party-progress display). Both reject a console source with the players-only
 * MessageKey and run their reads off the tick thread through the kernel {@code Scheduler} port.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.vote.adapter.inbound.command;
