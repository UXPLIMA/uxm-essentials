/**
 * The vote context's sealed domain-event family: the {@code VoteEvent} seal and its two concrete
 * records, {@code VoteReceived} (a credited online vote) and {@code VotePartyTriggered} (the threshold
 * was reached and a party fired). Every concrete event is a record (value equality backs the in-process
 * bus and equality-based test consumers); the adapter bridges each to a Bukkit event.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.vote.domain.event;
