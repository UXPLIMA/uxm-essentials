package com.uxplima.uxmessentials.bossbar.domain.event;

import com.uxplima.uxmessentials.shared.domain.DomainEvent;

/** A fact about the boss bars. */
public sealed interface BossBarEvent extends DomainEvent permits BossBarVisibilityToggled {}
