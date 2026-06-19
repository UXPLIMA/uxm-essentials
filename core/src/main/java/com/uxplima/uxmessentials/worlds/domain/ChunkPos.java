package com.uxplima.uxmessentials.worlds.domain;

/** A chunk coordinate (chunk-space, not block-space): {@code (x, z)} identifies one 16x16 column. */
public record ChunkPos(int x, int z) {}
