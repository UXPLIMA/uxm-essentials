/**
 * The player-warps context's inbound listeners: the join cache-warmer that loads the joining player's owned
 * warps off the join thread so the name-argument suggesters have the names in memory without a tick-thread
 * database read.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.playerwarps.adapter.inbound.listener;
