package dev.backpackarena.plugin.storage;

import java.util.UUID;

/** Прогресс игрока вне матча. Изменяется только в главном потоке, сохраняется асинхронно. */
public final class PlayerProfile {

    private final UUID uuid;
    private String name;
    private int xp;
    private int gems;
    private int matches;
    private int wins;
    private int losses;
    private int bestWins;

    public PlayerProfile(UUID uuid, String name, int xp, int gems, int matches, int wins, int losses, int bestWins) {
        this.uuid = uuid;
        this.name = name;
        this.xp = xp;
        this.gems = gems;
        this.matches = matches;
        this.wins = wins;
        this.losses = losses;
        this.bestWins = bestWins;
    }

    public static PlayerProfile fresh(UUID uuid, String name) {
        return new PlayerProfile(uuid, name, 0, 0, 0, 0, 0, 0);
    }

    public void recordMatch(int roundWins, int roundLosses, int xpGain, int gemsGain) {
        matches++;
        wins += roundWins;
        losses += roundLosses;
        bestWins = Math.max(bestWins, roundWins);
        xp += Math.max(0, xpGain);
        gems += Math.max(0, gemsGain);
    }

    public int level(int xpPerLevel) {
        return 1 + xp / Math.max(1, xpPerLevel);
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int xp() {
        return xp;
    }

    public int gems() {
        return gems;
    }

    public int matches() {
        return matches;
    }

    public int wins() {
        return wins;
    }

    public int losses() {
        return losses;
    }

    public int bestWins() {
        return bestWins;
    }
}
