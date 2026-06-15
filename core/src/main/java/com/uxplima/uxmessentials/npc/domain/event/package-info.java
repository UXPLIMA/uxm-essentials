/**
 * The npc context's domain events: the sealed {@code NpcEvent} family and its {@code NpcCreated} /
 * {@code NpcDeleted} records. Every concrete event is a record (value equality), past tense, and bridged to a
 * Bukkit event only in the adapter. No Bukkit, Paper, Kyori, or logging type appears here.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.npc.domain.event;
