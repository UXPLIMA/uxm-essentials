package com.uxplima.uxmessentials.economy.domain;

import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Domain class representing a Shared Joint Bank account.
 * Holds members, roles, permissions, creator references, and current balance.
 */
public final class SharedBank {

    private final String id;
    private final String name;
    private final Money balance;
    private final PlayerRef creator;
    private final List<BankMember> members;
    private final long createdAt;

    public SharedBank(
            String id, String name, Money balance, PlayerRef creator, List<BankMember> members, long createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.balance = Objects.requireNonNull(balance, "balance");
        this.creator = Objects.requireNonNull(creator, "creator");
        this.members = List.copyOf(Objects.requireNonNull(members, "members"));
        this.createdAt = createdAt;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public Money balance() {
        return balance;
    }

    public PlayerRef creator() {
        return creator;
    }

    public List<BankMember> members() {
        return members;
    }

    public long createdAt() {
        return createdAt;
    }

    /** This bank with its balance set to {@code newBalance} (same currency); a mismatch throws. */
    public SharedBank withBalance(Money newBalance) {
        Objects.requireNonNull(newBalance, "newBalance");
        if (!balance.currency().equals(newBalance.currency())) {
            throw new IllegalArgumentException(
                    "currency mismatch: " + balance.currency() + " vs " + newBalance.currency());
        }
        return new SharedBank(id, name, newBalance, creator, members, createdAt);
    }

    /** This bank with {@code member} added to the roster. */
    public SharedBank withMember(BankMember member) {
        Objects.requireNonNull(member, "member");
        List<BankMember> updated = new java.util.ArrayList<>(members);
        updated.add(member);
        return new SharedBank(id, name, balance, creator, updated, createdAt);
    }

    /** This bank with {@code target} removed from the roster. */
    public SharedBank withoutMember(PlayerRef target) {
        Objects.requireNonNull(target, "target");
        List<BankMember> updated = new java.util.ArrayList<>(members);
        updated.removeIf(m -> m.player().uuid().equals(target.uuid()));
        return new SharedBank(id, name, balance, creator, updated, createdAt);
    }

    public boolean isMember(PlayerRef player) {
        return members.stream().anyMatch(m -> m.player().uuid().equals(player.uuid()));
    }

    public boolean hasPermission(PlayerRef player, BankAction action) {
        return members.stream()
                .filter(m -> m.player().uuid().equals(player.uuid()))
                .findFirst()
                .map(m -> m.role().canPerform(action))
                .orElse(false);
    }

    public enum BankRole {
        MEMBER {
            @Override
            public boolean canPerform(BankAction action) {
                return action == BankAction.CHECK_BALANCE || action == BankAction.DEPOSIT;
            }
        },
        CO_LEADER {
            @Override
            public boolean canPerform(BankAction action) {
                return action != BankAction.DELETE;
            }
        },
        LEADER {
            @Override
            public boolean canPerform(BankAction action) {
                return true;
            }
        };

        public abstract boolean canPerform(BankAction action);
    }

    public enum BankAction {
        CHECK_BALANCE,
        DEPOSIT,
        WITHDRAW,
        ADD_MEMBER,
        REMOVE_MEMBER,
        DELETE
    }

    public record BankMember(PlayerRef player, BankRole role) {
        public BankMember {
            Objects.requireNonNull(player, "player");
            Objects.requireNonNull(role, "role");
        }
    }
}
