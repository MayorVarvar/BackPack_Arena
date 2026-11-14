package dev.backpackarena.plugin.command;

import dev.backpackarena.core.content.ContentException;
import dev.backpackarena.plugin.BackpackArenaPlugin;
import dev.backpackarena.plugin.gui.Msg;
import dev.backpackarena.plugin.match.Match;
import dev.backpackarena.plugin.session.PlayerSession;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /ba play | duel | leave | shop | bag | stats | reload | setarena a|b */
public final class ArenaCommand implements CommandExecutor, TabCompleter {

    private static final List<String> PLAYER_SUBS = List.of("play", "duel", "leave", "shop", "bag", "stats", "help");
    private static final List<String> ADMIN_SUBS = List.of("reload", "setarena");

    private final BackpackArenaPlugin plugin;

    public ArenaCommand(BackpackArenaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("reload")) {
            return reload(sender);
        }
        if (!(sender instanceof Player player)) {
            Msg.bad(sender, "Команда только для игроков.");
            return true;
        }
        switch (sub) {
            case "play", "train" -> plugin.matches().openHeroSelect(player, Match.Mode.TRAINING);
            case "duel" -> plugin.matches().openHeroSelect(player, Match.Mode.DUEL);
            case "leave" -> plugin.matches().leave(player);
            case "shop" -> requireSession(player, () -> plugin.matches().openShop(player));
            case "bag" -> requireSession(player, () -> plugin.matches().openBackpack(player));
            case "stats" -> plugin.matches().showStats(player);
            case "setarena" -> setArena(player, args);
            default -> help(player);
        }
        return true;
    }

    private void requireSession(Player player, Runnable action) {
        PlayerSession s = plugin.sessions().get(player);
        if (s == null) {
            Msg.bad(player, "Вы не в матче. /ba play — тренировка, /ba duel — дуэль.");
        } else {
            action.run();
        }
    }

    private boolean reload(CommandSender sender) {
        if (!sender.hasPermission("backpackarena.admin")) {
            Msg.bad(sender, "Нет прав.");
            return true;
        }
        try {
            var content = plugin.reloadContent();
            Msg.good(sender, "Перезагружено: героев " + content.heroes().size() + ", предметов "
                    + content.items().size() + ". Идущие матчи доиграют на старых настройках.");
        } catch (ContentException e) {
            Msg.bad(sender, "Ошибки в конфигах, оставлены старые настройки:");
            e.problems().forEach(p -> Msg.bad(sender, " - " + p));
        }
        return true;
    }

    private void setArena(Player player, String[] args) {
        if (!player.hasPermission("backpackarena.admin")) {
            Msg.bad(player, "Нет прав.");
            return;
        }
        if (args.length < 2 || !(args[1].equalsIgnoreCase("a") || args[1].equalsIgnoreCase("b"))) {
            Msg.bad(player, "Использование: /ba setarena a|b (a — первый игрок, b — второй)");
            return;
        }
        String key = "arena.spawn-" + args[1].toLowerCase(Locale.ROOT);
        plugin.getConfig().set(key, player.getLocation());
        plugin.saveConfig();
        Msg.good(player, "Точка " + args[1] + " арены сохранена.");
    }

    private void help(Player player) {
        Msg.info(player, "/ba play — тренировка против бота");
        Msg.info(player, "/ba duel — дуэль 1 на 1 (очередь)");
        Msg.info(player, "/ba shop, /ba bag — магазин и рюкзак во время матча");
        Msg.info(player, "/ba leave — выйти из матча или очереди");
        Msg.info(player, "/ba stats — ваш профиль");
        if (player.hasPermission("backpackarena.admin")) {
            Msg.info(player, "/ba reload — перечитать конфиги; /ba setarena a|b — точки арены");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> all = new ArrayList<>(PLAYER_SUBS);
            if (sender.hasPermission("backpackarena.admin")) {
                all.addAll(ADMIN_SUBS);
            }
            return all.stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("setarena")) {
            return List.of("a", "b");
        }
        return List.of();
    }
}
