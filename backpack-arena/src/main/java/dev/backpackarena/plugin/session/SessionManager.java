package dev.backpackarena.plugin.session;

import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Активные игровые сессии по UUID. Используется только из главного потока. */
public final class SessionManager {

    private final Map<UUID, PlayerSession> sessions = new HashMap<>();

    public PlayerSession get(Player player) {
        return sessions.get(player.getUniqueId());
    }

    public boolean inMatch(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    public void put(PlayerSession session) {
        sessions.put(session.uuid(), session);
    }

    public void remove(UUID uuid) {
        sessions.remove(uuid);
    }

    public Collection<PlayerSession> all() {
        return new ArrayList<>(sessions.values());
    }
}
