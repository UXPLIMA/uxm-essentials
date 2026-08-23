package com.uxplima.uxmessentials.scoreboard.support;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.entity.Player;

import com.uxplima.uxmlib.packet.scoreboard.ScoreboardDisplaySlot;
import com.uxplima.uxmlib.packet.scoreboard.ScoreboardObjective;
import com.uxplima.uxmlib.packet.scoreboard.ScoreboardPackets;
import com.uxplima.uxmlib.packet.scoreboard.ScoreboardScore;

public final class RecordingScoreboardPackets implements ScoreboardPackets {

    private final List<List<Object>> sends = new ArrayList<>();

    @Override
    public Object createObjective(ScoreboardObjective objective) {
        return new Create(objective);
    }

    @Override
    public Object updateObjective(ScoreboardObjective objective) {
        return new Update(objective);
    }

    @Override
    public Object removeObjective(String objectiveName) {
        return new RemoveObjective(objectiveName);
    }

    @Override
    public Object displayObjective(ScoreboardDisplaySlot slot, String objectiveName) {
        return new Display(slot, objectiveName);
    }

    @Override
    public Object clearDisplay(ScoreboardDisplaySlot slot) {
        return new ClearDisplay(slot);
    }

    @Override
    public Object setScore(ScoreboardScore score) {
        return new SetScore(score);
    }

    @Override
    public Object removeScore(String objectiveName, String holder) {
        return new RemoveScore(objectiveName, holder);
    }

    @Override
    public void sendPacket(Player viewer, Object packet) {
        sends.add(List.of(packet));
    }

    @Override
    public void sendPackets(Player viewer, List<Object> packets) {
        if (!packets.isEmpty()) {
            sends.add(List.copyOf(packets));
        }
    }

    public List<List<Object>> sends() {
        return List.copyOf(sends);
    }

    public List<Object> operations() {
        return sends.stream().flatMap(List::stream).toList();
    }

    public void reset() {
        sends.clear();
    }

    public record Create(ScoreboardObjective objective) {}

    public record Update(ScoreboardObjective objective) {}

    public record RemoveObjective(String objectiveName) {}

    public record Display(ScoreboardDisplaySlot slot, String objectiveName) {}

    public record ClearDisplay(ScoreboardDisplaySlot slot) {}

    public record SetScore(ScoreboardScore score) {}

    public record RemoveScore(String objectiveName, String holder) {}
}
