/**
 * The vote context's bukkit-adapter root: {@code VoteWiring} constructs the use cases and adapters over
 * the injected kernel ports, the persistence DSL, and the operator config, and produces the Brigadier
 * commands and Bukkit listeners the plugin registers; {@code VoteServices} is the constructed-use-case
 * holder the inbound adapters share. The inbound command/listener adapters and the outbound dispatcher /
 * audience live in the {@code inbound} and {@code outbound} subpackages.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.vote.adapter;
