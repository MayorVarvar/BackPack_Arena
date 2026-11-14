package dev.backpackarena.plugin.storage;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * SQLite-хранилище профилей и истории матчей. Все запросы идут в одном фоновом потоке,
 * поэтому порядок записей сохраняется, а главный поток сервера не блокируется.
 * Для масштабирования (ТЗ, этап 6) класс заменяется реализацией на PostgreSQL.
 */
public final class Database {

    private final JavaPlugin plugin;
    private final File file;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "BackpackArena-DB");
        t.setDaemon(true);
        return t;
    });
    private final Map<UUID, PlayerProfile> cache = new ConcurrentHashMap<>();
    private Connection connection;

    public Database(JavaPlugin plugin, File file) {
        this.plugin = plugin;
        this.file = file;
    }

    public void open() {
        executor.execute(() -> {
            try {
                file.getParentFile().mkdirs();
                connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
                try (Statement st = connection.createStatement()) {
                    st.executeUpdate("""
                            CREATE TABLE IF NOT EXISTS profiles (
                              uuid TEXT PRIMARY KEY,
                              name TEXT NOT NULL,
                              xp INTEGER NOT NULL DEFAULT 0,
                              gems INTEGER NOT NULL DEFAULT 0,
                              matches INTEGER NOT NULL DEFAULT 0,
                              wins INTEGER NOT NULL DEFAULT 0,
                              losses INTEGER NOT NULL DEFAULT 0,
                              best_wins INTEGER NOT NULL DEFAULT 0
                            )""");
                    st.executeUpdate("""
                            CREATE TABLE IF NOT EXISTS match_history (
                              id INTEGER PRIMARY KEY AUTOINCREMENT,
                              match_id TEXT NOT NULL,
                              uuid TEXT NOT NULL,
                              hero TEXT NOT NULL,
                              mode TEXT NOT NULL,
                              wins INTEGER NOT NULL,
                              losses INTEGER NOT NULL,
                              rounds INTEGER NOT NULL,
                              xp INTEGER NOT NULL,
                              gems INTEGER NOT NULL,
                              finished_at INTEGER NOT NULL,
                              UNIQUE (match_id, uuid)
                            )""");
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Не удалось открыть базу " + file, e);
            }
        });
    }

    /** Загружает профиль (или создаёт новый) и вызывает callback в главном потоке. */
    public void loadProfile(UUID uuid, String name, Consumer<PlayerProfile> callback) {
        PlayerProfile cached = cache.get(uuid);
        if (cached != null) {
            callback.accept(cached);
            return;
        }
        executor.execute(() -> {
            PlayerProfile profile = PlayerProfile.fresh(uuid, name);
            if (connection != null) {
                try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM profiles WHERE uuid = ?")) {
                    ps.setString(1, uuid.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            profile = new PlayerProfile(uuid, name, rs.getInt("xp"), rs.getInt("gems"),
                                    rs.getInt("matches"), rs.getInt("wins"), rs.getInt("losses"), rs.getInt("best_wins"));
                        }
                    }
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.WARNING, "Ошибка чтения профиля " + uuid, e);
                }
            }
            PlayerProfile result = profile;
            Bukkit.getScheduler().runTask(plugin, () -> {
                PlayerProfile existing = cache.putIfAbsent(uuid, result);
                callback.accept(existing != null ? existing : result);
            });
        });
    }

    /**
     * Записывает итог матча и профиль одной транзакцией. UNIQUE(match_id, uuid) не даёт
     * выдать награду за один матч дважды даже при повторном вызове.
     */
    public void saveMatch(PlayerProfile profile, String matchId, String hero, String mode,
                          int wins, int losses, int rounds, int xp, int gems) {
        PlayerProfile snapshot = new PlayerProfile(profile.uuid(), profile.name(), profile.xp(), profile.gems(),
                profile.matches(), profile.wins(), profile.losses(), profile.bestWins());
        executor.execute(() -> {
            if (connection == null) {
                return;
            }
            try {
                connection.setAutoCommit(false);
                try (PreparedStatement ps = connection.prepareStatement("""
                        INSERT OR IGNORE INTO match_history
                        (match_id, uuid, hero, mode, wins, losses, rounds, xp, gems, finished_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""")) {
                    ps.setString(1, matchId);
                    ps.setString(2, snapshot.uuid().toString());
                    ps.setString(3, hero);
                    ps.setString(4, mode);
                    ps.setInt(5, wins);
                    ps.setInt(6, losses);
                    ps.setInt(7, rounds);
                    ps.setInt(8, xp);
                    ps.setInt(9, gems);
                    ps.setLong(10, System.currentTimeMillis());
                    if (ps.executeUpdate() == 0) {
                        connection.rollback();
                        return;
                    }
                }
                upsert(snapshot);
                connection.commit();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Ошибка сохранения матча " + matchId, e);
                try {
                    connection.rollback();
                } catch (SQLException ignored) {
                    // соединение уже в ошибке
                }
            } finally {
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException ignored) {
                    // соединение уже в ошибке
                }
            }
        });
    }

    private void upsert(PlayerProfile p) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO profiles (uuid, name, xp, gems, matches, wins, losses, best_wins)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(uuid) DO UPDATE SET name = excluded.name, xp = excluded.xp, gems = excluded.gems,
                  matches = excluded.matches, wins = excluded.wins, losses = excluded.losses,
                  best_wins = excluded.best_wins""")) {
            ps.setString(1, p.uuid().toString());
            ps.setString(2, p.name());
            ps.setInt(3, p.xp());
            ps.setInt(4, p.gems());
            ps.setInt(5, p.matches());
            ps.setInt(6, p.wins());
            ps.setInt(7, p.losses());
            ps.setInt(8, p.bestWins());
            ps.executeUpdate();
        }
    }

    public void forget(UUID uuid) {
        cache.remove(uuid);
    }

    public void close() {
        executor.execute(() -> {
            try {
                if (connection != null) {
                    connection.close();
                }
            } catch (SQLException ignored) {
                // закрываемся
            }
        });
        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
