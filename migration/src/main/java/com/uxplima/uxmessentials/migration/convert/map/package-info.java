/**
 * Source-neutral mapper output: the {@code Imported*} records every source's {@code Convert} produces,
 * expressed entirely in {@code :core} domain terms so the writer persists them without knowing which
 * source built them. The EssentialsX mappers and any future DB-backed source (LiteBans, LibertyBans)
 * build the same records here, so no source package leaks into another (docs/12-migration §5.1).
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.migration.convert.map;
