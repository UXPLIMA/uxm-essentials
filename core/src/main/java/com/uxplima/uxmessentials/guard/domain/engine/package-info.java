/**
 * The compiled, read-mostly form of the guard's regions: per world indexes of chunk columns and interned 16
 * block section cells, region sets in precedence order, and rule tables flattened into one byte per role row and
 * action. Every listener check reads an immutable snapshot without locking. Nothing here names Bukkit.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.guard.domain.engine;
