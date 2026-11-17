/**
 * Per-player settings shared by every backend of one database: held in memory for the players online on a backend,
 * read when they log in and written back when they change, so a check on the tick never reads the database and a
 * player keeps their settings when they move to another backend.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.shared.application.settings;
