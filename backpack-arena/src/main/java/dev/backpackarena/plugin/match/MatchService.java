package dev.backpackarena.plugin.match;

import dev.backpackarena.core.bot.BotFactory;
import dev.backpackarena.core.combat.CombatResult;
import dev.backpackarena.core.combat.CombatSimulator;
import dev.backpackarena.core.combat.FighterSpec;
import dev.backpackarena.core.content.GameContent;
import dev.backpackarena.core.crafting.CraftingEngine;
import dev.backpackarena.core.economy.EconomyConfig;
import dev.backpackarena.core.economy.Shop;
import dev.backpackarena.core.grid.ItemInstance;
import dev.backpackarena.core.model.HeroDef;
import dev.backpackarena.core.model.RelicDef;
import dev.backpackarena.core.run.RunState;
import dev.backpackarena.plugin.BackpackArenaPlugin;
import dev.backpackarena.plugin.gui.BackpackMenu;
import dev.backpackarena.plugin.gui.Controls;
import dev.backpackarena.plugin.gui.HeroMenu;
import dev.backpackarena.plugin.gui.Menu;
import dev.backpackarena.plugin.gui.Msg;
import dev.backpackarena.plugin.gui.RelicMenu;
import dev.backpackarena.plugin.gui.ShopMenu;
import dev.backpackarena.plugin.session.InventorySnapshot;
import dev.backpackarena.plugin.session.PlayerSession;
import dev.backpackarena.plugin.session.SessionManager;
import dev.backpackarena.plugin.storage.PlayerProfile;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Жизненный цикл матчей: выбор героя → подготовка → бой → награды.
 * Вся логика выполняется в главном потоке сервера.
 */
public final class MatchService implements Listener {

    private final BackpackArenaPlugin plugin;
    private final SessionManager sessions;
    private final Map<UUID, HeroDef> duelQueue = new LinkedHashMap<>();
    private final List<Match> active = new ArrayList<>();
    private final Map<Match, BattleReplayer> replays = new HashMap<>();

    public MatchService(BackpackArenaPlugin plugin, SessionManager sessions) {
        this.plugin = plugin;
        this.sessions = sessions;
    }

    // ---------------------------------------------------------------- старт

    public void openHeroSelect(Player player, Match.Mode mode) {
        if (sessions.inMatch(player)) {
            Msg.bad(player, "Вы уже в матче. /ba leave — выйти.");
            return;
        }
        new HeroMenu(plugin, player, mode).open();
    }

    public void pickHero(Player player, Match.Mode mode, HeroDef hero) {
        player.closeInventory();
        if (sessions.inMatch(player)) {
            return;
        }
        if (mode == Match.Mode.TRAINING) {
            Match match = new Match(Match.Mode.TRAINING);
            active.add(match);
            begin(player, hero, match, arenaSpawn("a"));
            Msg.good(player, "Тренировка началась! Герой: " + hero.name() + ". Соперник — бот.");
            openShop(player);
            return;
        }
        // дуэль: ищем ожидающего соперника
        for (Map.Entry<UUID, HeroDef> waiting : new ArrayList<>(duelQueue.entrySet())) {
            Player other = Bukkit.getPlayer(waiting.getKey());
            if (other == null || other.equals(player)) {
                continue;
            }
            duelQueue.remove(waiting.getKey());
            duelQueue.remove(player.getUniqueId());
            Match match = new Match(Match.Mode.DUEL);
            active.add(match);
            begin(other, waiting.getValue(), match, arenaSpawn("a"));
            begin(player, hero, match, arenaSpawn("b"));
            Msg.good(other, "Соперник найден: " + player.getName() + " (" + hero.name() + ")");
            Msg.good(player, "Соперник найден: " + other.getName() + " (" + waiting.getValue().name() + ")");
            openShop(other);
            openShop(player);
            return;
        }
        duelQueue.put(player.getUniqueId(), hero);
        Msg.info(player, "Вы в очереди дуэлей (" + hero.name() + "). Ждём соперника… /ba leave — отменить.");
    }

    private void begin(Player player, HeroDef hero, Match match, Location spawn) {
        InventorySnapshot snapshot = InventorySnapshot.take(player);
        GameContent content = plugin.content();
        RunState run = new RunState(player.getName(), hero, content, new Random());
        PlayerSession session = new PlayerSession(player, run, match, snapshot);
        match.players().add(session);
        sessions.put(session);
        player.getInventory().clear();
        player.setGameMode(GameMode.ADVENTURE);
        if (spawn != null) {
            player.teleport(spawn);
        }
        Controls.render(player, session);
    }

    private Location arenaSpawn(String key) {
        return plugin.getConfig().getLocation("arena.spawn-" + key);
    }

    // ---------------------------------------------------------------- меню

    public void openShop(Player player) {
        PlayerSession s = sessions.get(player);
        if (s == null || s.inBattle()) {
            return;
        }
        later(() -> {
            if (!s.relicChoices().isEmpty()) {
                new RelicMenu(plugin, player, s).open();
            } else {
                new ShopMenu(plugin, player, s).open();
            }
        });
    }

    public void openBackpack(Player player) {
        PlayerSession s = sessions.get(player);
        if (s == null || s.inBattle()) {
            return;
        }
        if (!s.relicChoices().isEmpty()) {
            openShop(player);
            return;
        }
        later(() -> new BackpackMenu(plugin, player, s).open());
    }

    /** Повторно открывает обязательное меню (например, выбор реликвии) на следующем тике. */
    public void reopenLater(Player player) {
        openShop(player);
    }

    private void later(Runnable r) {
        Bukkit.getScheduler().runTask(plugin, r);
    }

    private void refresh(Player player, PlayerSession s) {
        if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof Menu menu) {
            menu.render();
        } else {
            Controls.render(player, s);
        }
    }

    // ---------------------------------------------------------------- управление

    /** Клик по инвентарю игрока: хотбар — кнопки, основные слоты — хранилище. */
    public void clickPlayerInventory(Player player, PlayerSession s, int slot, InventoryClickEvent event) {
        if (s == null || s.inBattle()) {
            return;
        }
        if (slot < Controls.STORAGE_FIRST_SLOT) {
            Controls.Control control = Controls.Control.bySlot(slot);
            if (control != null) {
                useControl(player, s, control);
            }
            return;
        }
        int index = slot - Controls.STORAGE_FIRST_SLOT;
        if (index >= Controls.STORAGE_SLOTS) {
            return; // слоты брони/второй руки
        }
        List<ItemInstance> storage = s.run().storage();
        if (event.isShiftClick() && index < storage.size()) {
            sell(player, s, storage.get(index));
        } else if (s.hand() == null) {
            if (index < storage.size()) {
                s.setHand(storage.remove(index));
            }
        } else {
            storage.add(s.hand());
            s.setHand(null);
        }
        Controls.render(player, s);
    }

    public void useControl(Player player, PlayerSession s, Controls.Control control) {
        if (s == null || s.inBattle()) {
            return;
        }
        switch (control) {
            case HAND -> {
                return;
            }
            case ROTATE -> {
                if (s.hand() == null) {
                    Msg.bar(player, "Сначала возьмите предмет", NamedTextColor.RED);
                } else if (!s.hand().rotate()) {
                    Msg.bar(player, "Этот предмет нельзя поворачивать", NamedTextColor.RED);
                }
            }
            case SELL -> {
                if (s.hand() == null) {
                    Msg.bar(player, "Возьмите предмет в руку, чтобы продать", NamedTextColor.RED);
                } else {
                    ItemInstance inst = s.hand();
                    s.setHand(null);
                    s.run().storage().add(inst);
                    sell(player, s, inst);
                }
            }
            case SHOP -> {
                openShop(player);
                return;
            }
            case BACKPACK -> {
                openBackpack(player);
                return;
            }
            case FIGHT -> {
                ready(player);
                return;
            }
            case LEAVE -> {
                if (s.confirmLeave()) {
                    leave(player);
                } else {
                    Msg.bar(player, "Нажмите ещё раз, чтобы выйти из матча", NamedTextColor.YELLOW);
                }
                return;
            }
        }
        refresh(player, s);
    }

    private void sell(Player player, PlayerSession s, ItemInstance inst) {
        int price = inst.def().sellPrice(s.run().content().economy().sellRatio());
        Shop.Result r = s.run().shop().sell(inst);
        if (r == Shop.Result.OK) {
            Msg.bar(player, "Продано: " + inst.def().name() + " +" + price + " зол.", NamedTextColor.GOLD);
        } else {
            Msg.bar(player, r.message(), NamedTextColor.RED);
        }
    }

    /** Ускоренный крафт из магазина. */
    public void craftNow(Player player, PlayerSession s) {
        RunState run = s.run();
        List<CraftingEngine.Match> matches = CraftingEngine.findMatches(run.grid(), run.content().recipes());
        if (matches.isEmpty()) {
            Msg.bar(player, "Нет готовых рецептов: положите ингредиенты рядом в рюкзаке", NamedTextColor.RED);
            return;
        }
        int cost = run.content().economy().craftHammerCost(run.hammerUses());
        if (!run.spend(cost)) {
            Msg.bar(player, "Не хватает золота (" + cost + ")", NamedTextColor.RED);
            return;
        }
        run.incrementHammerUses();
        for (CraftingEngine.Match m : matches) {
            ItemInstance created = CraftingEngine.apply(run, m);
            Msg.good(player, "Создано: " + created.def().name());
        }
    }

    public void chooseRelic(Player player, PlayerSession s, RelicDef relic) {
        s.run().relics().add(relic);
        s.setRelicChoices(List.of());
        Msg.good(player, "Реликвия получена: " + relic.name() + " — " + relic.description());
        openShop(player);
    }

    // ---------------------------------------------------------------- бой

    public void ready(Player player) {
        PlayerSession s = sessions.get(player);
        if (s == null || s.inBattle() || s.ready()) {
            return;
        }
        if (!s.relicChoices().isEmpty()) {
            openShop(player);
            return;
        }
        s.stashHand();
        s.setReady(true);
        player.closeInventory();
        Match match = s.match();
        if (match.mode() == Match.Mode.TRAINING || match.allReady()) {
            startBattle(match);
        } else {
            Controls.render(player, s);
            Msg.info(player, "Вы готовы. Ждём соперника…");
            PlayerSession other = match.opponentOf(s);
            if (other != null && other.player() != null) {
                Msg.info(other.player(), player.getName() + " готов к бою.");
            }
        }
    }

    private void startBattle(Match match) {
        long seed = ThreadLocalRandom.current().nextLong();
        PlayerSession first = match.players().get(0);
        RunState run = first.run();
        FighterSpec a = FighterSpec.of(run);
        FighterSpec b;
        if (match.mode() == Match.Mode.TRAINING) {
            b = FighterSpec.of(BotFactory.create(run.content(), run.round(), seed ^ 0x5DEECE66DL));
        } else {
            b = FighterSpec.of(match.players().get(1).run());
        }
        CombatResult result = CombatSimulator.simulate(a, b, run.content().combat(), seed);

        List<BattleReplayer.Viewer> viewers = new ArrayList<>();
        for (int i = 0; i < match.players().size(); i++) {
            PlayerSession s = match.players().get(i);
            s.setInBattle(true);
            Player p = s.player();
            if (p != null) {
                p.closeInventory();
                FighterSpec enemy = i == 0 ? b : a;
                Msg.info(p, "Раунд " + s.run().round() + ": против " + enemy.name()
                        + " (" + enemy.hero().name() + ", предметов: " + enemy.items().size() + ")");
            }
            viewers.add(new BattleReplayer.Viewer(s.uuid(), i));
        }
        BattleReplayer replayer = new BattleReplayer(result, viewers,
                plugin.getConfig().getDouble("replay-speed", 1.5),
                () -> afterBattle(match, result));
        replays.put(match, replayer);
        replayer.start(plugin, plugin.getConfig().getLong("battle-start-delay", 30));
    }

    private void afterBattle(Match match, CombatResult result) {
        replays.remove(match);
        if (match.ended()) {
            return;
        }
        for (int i = 0; i < match.players().size(); i++) {
            PlayerSession s = match.players().get(i);
            RunState run = s.run();
            RunState.Outcome outcome = result.isDraw() ? RunState.Outcome.DRAW
                    : result.winner() == i ? RunState.Outcome.WIN : RunState.Outcome.LOSS;
            List<ItemInstance> crafted = CraftingEngine.craftAll(run);
            int goldBefore = run.gold();
            run.finishRound(outcome);
            s.setInBattle(false);
            s.setReady(false);
            Player p = s.player();
            if (p == null) {
                continue;
            }
            switch (outcome) {
                case WIN -> Msg.good(p, "Победа! Побед: " + run.wins() + "/" + run.content().economy().winsToFinish());
                case LOSS -> Msg.bad(p, "Поражение. Осталось жизней: " + run.lives());
                case DRAW -> Msg.info(p, "Ничья.");
            }
            for (ItemInstance c : crafted) {
                Msg.good(p, "Крафт: " + c.def().name());
            }
            if (!run.isFinished()) {
                Msg.info(p, "Раунд " + run.round() + ": +" + (run.gold() - goldBefore) + " зол. (всего " + run.gold() + ")");
            }
        }
        boolean finished = match.players().stream().anyMatch(s -> s.run().isFinished());
        if (finished) {
            endMatch(match, null);
            return;
        }
        for (PlayerSession s : match.players()) {
            if (s.run().isRelicRound()) {
                s.setRelicChoices(rollRelics(s.run()));
            }
            Player p = s.player();
            if (p != null) {
                openShop(p);
            }
        }
    }

    private List<RelicDef> rollRelics(RunState run) {
        List<RelicDef> pool = new ArrayList<>(run.content().relics().values());
        pool.removeAll(run.relics());
        Collections.shuffle(pool, run.random());
        return List.copyOf(pool.subList(0, Math.min(run.content().economy().relicChoices(), pool.size())));
    }

    // ---------------------------------------------------------------- завершение

    public void leave(Player player) {
        if (duelQueue.remove(player.getUniqueId()) != null) {
            Msg.info(player, "Вы вышли из очереди.");
            return;
        }
        PlayerSession s = sessions.get(player);
        if (s == null) {
            Msg.bad(player, "Вы не в матче.");
            return;
        }
        endMatch(s.match(), s);
    }

    /** @param leaver игрок, покинувший матч досрочно (не получает награду), или null */
    private void endMatch(Match match, PlayerSession leaver) {
        if (match.ended()) {
            return;
        }
        match.setEnded();
        active.remove(match);
        BattleReplayer replay = replays.remove(match);
        if (replay != null) {
            replay.abort();
        }
        for (PlayerSession s : match.players()) {
            sessions.remove(s.uuid());
            s.stashHand();
            Player p = s.player();
            boolean won = isWinner(match, s, leaver);
            if (s != leaver) {
                grantRewards(match, s);
            }
            if (p != null) {
                s.snapshot().restore(p);
                RunState run = s.run();
                Msg.info(p, "Матч окончен. Победы: " + run.wins() + ", поражения: " + run.losses()
                        + ", раундов: " + (run.round() - 1) + ".");
                if (match.mode() == Match.Mode.DUEL) {
                    if (won) {
                        Msg.good(p, "Вы выиграли дуэль!");
                    } else {
                        Msg.bad(p, "Дуэль проиграна.");
                    }
                }
            }
        }
    }

    private boolean isWinner(Match match, PlayerSession s, PlayerSession leaver) {
        if (leaver != null) {
            return s != leaver && match.mode() == Match.Mode.DUEL;
        }
        RunState run = s.run();
        if (run.wins() >= run.content().economy().winsToFinish()) {
            return true;
        }
        PlayerSession other = match.opponentOf(s);
        return other != null && run.lives() > 0 && other.run().lives() <= 0;
    }

    private void grantRewards(Match match, PlayerSession s) {
        RunState run = s.run();
        if (!run.markRewarded()) {
            return;
        }
        EconomyConfig.Rewards r = run.content().economy().rewards();
        int xp = r.xpPerMatch() + run.wins() * r.xpPerWin();
        // бонус только за полный забег: победа из-за выхода соперника его не даёт
        int gems = run.wins() * r.gemsPerWin()
                + (run.wins() >= run.content().economy().winsToFinish() ? r.gemsForFullRun() : 0);
        plugin.database().loadProfile(s.uuid(), s.name(), profile -> {
            profile.recordMatch(run.wins(), run.losses(), xp, gems);
            plugin.database().saveMatch(profile, match.id(), run.hero().id(), match.mode().name(),
                    run.wins(), run.losses(), run.round() - 1, xp, gems);
            Player p = s.player();
            if (p != null) {
                Msg.good(p, "Награда: +" + xp + " опыта, +" + gems + " самоцветов. Уровень: "
                        + profile.level(plugin.getConfig().getInt("xp-per-level", 100)));
            }
        });
    }

    public void showStats(Player player) {
        plugin.database().loadProfile(player.getUniqueId(), player.getName(), (PlayerProfile p) -> {
            int perLevel = plugin.getConfig().getInt("xp-per-level", 100);
            Msg.info(player, "Уровень " + p.level(perLevel) + " (" + p.xp() + " опыта) · самоцветы: " + p.gems());
            Msg.info(player, "Матчей: " + p.matches() + " · побед в раундах: " + p.wins()
                    + " · поражений: " + p.losses() + " · лучший забег: " + p.bestWins());
        });
    }

    /** Остановка сервера: всем вернуть инвентари, награды не выдаются. */
    public void shutdown() {
        for (BattleReplayer r : new ArrayList<>(replays.values())) {
            r.abort();
        }
        replays.clear();
        for (Match m : new ArrayList<>(active)) {
            m.setEnded();
            for (PlayerSession s : m.players()) {
                Player p = s.player();
                if (p != null) {
                    s.snapshot().restore(p);
                }
                sessions.remove(s.uuid());
            }
        }
        active.clear();
        duelQueue.clear();
    }

    // ---------------------------------------------------------------- события

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        duelQueue.remove(player.getUniqueId());
        PlayerSession s = sessions.get(player);
        if (s != null) {
            endMatch(s.match(), s);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        plugin.database().loadProfile(p.getUniqueId(), p.getName(), profile -> profile.setName(p.getName()));
    }
}
