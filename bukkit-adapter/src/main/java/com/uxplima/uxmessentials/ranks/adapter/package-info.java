/**
 * The ranks context's Bukkit adapter: the {@link com.uxplima.uxmessentials.ranks.adapter.RanksWiring} that
 * assembles the context's use cases over the injected kernel ports and the shared persistence DSL (the parsed
 * ladder, the DB-backed {@code PlayerRankRepository} through the persistence factory, never a jOOQ type, and the
 * {@code CurrentRank} read use case), with the inbound command and GUI adapters and the outbound economy,
 * permission and playtime seams in the packages below.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.ranks.adapter;
