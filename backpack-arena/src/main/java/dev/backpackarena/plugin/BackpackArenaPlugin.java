package dev.backpackarena.plugin;

import dev.backpackarena.core.content.ContentException;
import dev.backpackarena.core.content.ContentLoader;
import dev.backpackarena.core.content.GameContent;
import dev.backpackarena.plugin.command.ArenaCommand;
import dev.backpackarena.plugin.gui.MenuListener;
import dev.backpackarena.plugin.match.MatchService;
import dev.backpackarena.plugin.session.SessionManager;
import dev.backpackarena.plugin.storage.Database;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.logging.Level;

public final class BackpackArenaPlugin extends JavaPlugin {

    private GameContent content;
    private Database database;
    private SessionManager sessions;
    private MatchService matches;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        for (String file : ContentLoader.FILES) {
            if (!new File(getDataFolder(), file).exists()) {
                saveResource(file, false);
            }
        }
        try {
            content = loadContent();
        } catch (ContentException e) {
            getLogger().severe(e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        database = new Database(this, new File(getDataFolder(), getConfig().getString("database.file", "data.db")));
        database.open();

        sessions = new SessionManager();
        matches = new MatchService(this, sessions);

        getServer().getPluginManager().registerEvents(new MenuListener(this), this);
        getServer().getPluginManager().registerEvents(matches, this);

        ArenaCommand command = new ArenaCommand(this);
        PluginCommand ba = getCommand("ba");
        if (ba != null) {
            ba.setExecutor(command);
            ba.setTabCompleter(command);
        }
        getLogger().info("Загружено: героев " + content.heroes().size() + ", предметов " + content.items().size()
                + ", рецептов " + content.recipes().size() + ", реликвий " + content.relics().size());
    }

    @Override
    public void onDisable() {
        if (matches != null) {
            matches.shutdown();
        }
        if (database != null) {
            database.close();
        }
    }

    /** Перечитывает YAML. Идущие матчи продолжают работать со старым контентом. */
    public GameContent reloadContent() {
        reloadConfig();
        content = loadContent();
        return content;
    }

    private GameContent loadContent() {
        return ContentLoader.load(this::readYaml);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readYaml(String name) {
        File file = new File(getDataFolder(), name);
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            Object data = new Yaml().load(reader);
            return data instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Не удалось прочитать " + name, e);
            return Map.of();
        }
    }

    public GameContent content() {
        return content;
    }

    public Database database() {
        return database;
    }

    public SessionManager sessions() {
        return sessions;
    }

    public MatchService matches() {
        return matches;
    }
}
