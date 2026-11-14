package dev.backpackarena.plugin.match;

import dev.backpackarena.plugin.session.PlayerSession;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Матч: тренировка (1 игрок против бота) или дуэль (2 игрока). */
public final class Match {

    public enum Mode {
        TRAINING("Тренировка"),
        DUEL("Дуэль 1 на 1");

        private final String displayName;

        Mode(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }

    private final String id = UUID.randomUUID().toString();
    private final Mode mode;
    private final List<PlayerSession> players = new ArrayList<>();
    private boolean ended;

    public Match(Mode mode) {
        this.mode = mode;
    }

    public String id() {
        return id;
    }

    public Mode mode() {
        return mode;
    }

    public List<PlayerSession> players() {
        return players;
    }

    public boolean allReady() {
        return players.stream().allMatch(PlayerSession::ready);
    }

    public PlayerSession opponentOf(PlayerSession session) {
        for (PlayerSession p : players) {
            if (p != session) {
                return p;
            }
        }
        return null;
    }

    public boolean ended() {
        return ended;
    }

    public void setEnded() {
        this.ended = true;
    }
}
