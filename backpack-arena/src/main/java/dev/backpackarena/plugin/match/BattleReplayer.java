package dev.backpackarena.plugin.match;

import dev.backpackarena.core.combat.CombatEvent;
import dev.backpackarena.core.combat.CombatResult;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Проигрывает заранее рассчитанный бой: босс-бары HP, action bar, звуки и частицы.
 * Клиент ничего не решает — он только смотрит журнал событий сервера.
 */
public final class BattleReplayer extends BukkitRunnable {

    public record Viewer(UUID uuid, int side) {
    }

    private record Bars(Viewer viewer, BossBar self, BossBar enemy) {
    }

    private static final int END_PAUSE_TICKS = 40;

    private final CombatResult result;
    private final List<Bars> bars = new ArrayList<>();
    private final double speed;
    private final Runnable onFinish;
    private double time;
    private int index;
    private int endTicks = -1;
    private boolean finished;

    public BattleReplayer(CombatResult result, List<Viewer> viewers, double speed, Runnable onFinish) {
        this.result = result;
        this.speed = Math.max(0.1, speed);
        this.onFinish = onFinish;
        for (Viewer v : viewers) {
            BossBar self = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.GREEN, BossBar.Overlay.NOTCHED_10);
            BossBar enemy = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.RED, BossBar.Overlay.NOTCHED_10);
            bars.add(new Bars(v, self, enemy));
        }
    }

    public void start(Plugin plugin, long delayTicks) {
        for (Bars b : bars) {
            Player p = Bukkit.getPlayer(b.viewer.uuid());
            if (p == null) {
                continue;
            }
            int enemySide = 1 - b.viewer.side();
            updateBars(b, result.maxHp()[0], result.maxHp()[1], 0, 0);
            p.showBossBar(b.self);
            p.showBossBar(b.enemy);
            p.showTitle(Title.title(Component.text("Бой!", NamedTextColor.RED),
                    Component.text(result.names()[b.viewer.side()] + " vs " + result.names()[enemySide], NamedTextColor.GRAY),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1200), Duration.ofMillis(300))));
            p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 0.6f, 1.2f);
        }
        runTaskTimer(plugin, delayTicks, 1L);
    }

    @Override
    public void run() {
        if (endTicks >= 0) {
            if (++endTicks >= END_PAUSE_TICKS) {
                finish(true);
            }
            return;
        }
        time += 0.05 * speed;
        List<CombatEvent> events = result.events();
        while (index < events.size() && events.get(index).time() <= time) {
            show(events.get(index++));
        }
        if (index >= events.size()) {
            endTicks = 0;
        }
    }

    /** Прерывает повтор (выход игрока, остановка сервера). Колбэк не вызывается. */
    public void abort() {
        finish(false);
    }

    private void finish(boolean callback) {
        if (finished) {
            return;
        }
        finished = true;
        if (!isCancelled()) {
            try {
                cancel();
            } catch (IllegalStateException ignored) {
                // задача ещё не была запущена
            }
        }
        for (Bars b : bars) {
            Player p = Bukkit.getPlayer(b.viewer.uuid());
            if (p != null) {
                p.hideBossBar(b.self);
                p.hideBossBar(b.enemy);
            }
        }
        if (callback) {
            onFinish.run();
        }
    }

    private void show(CombatEvent e) {
        for (Bars b : bars) {
            updateBars(b, e.hp0(), e.hp1(), e.armor0(), e.armor1());
            Player p = Bukkit.getPlayer(b.viewer.uuid());
            if (p == null) {
                continue;
            }
            boolean mine = e.side() == b.viewer.side();
            if (e.type() == CombatEvent.Type.END) {
                boolean draw = result.isDraw();
                boolean won = !draw && result.winner() == b.viewer.side();
                p.showTitle(Title.title(
                        Component.text(draw ? "Ничья" : won ? "Победа!" : "Поражение",
                                draw ? NamedTextColor.YELLOW : won ? NamedTextColor.GREEN : NamedTextColor.RED),
                        Component.text(String.format(Locale.ROOT, "%.1f с", result.duration()), NamedTextColor.GRAY)));
                p.playSound(p.getLocation(), won ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_WITHER_HURT,
                        0.7f, 1f);
                continue;
            }
            TextColor color = e.side() < 0 ? NamedTextColor.GRAY : mine ? NamedTextColor.GREEN : NamedTextColor.RED;
            p.sendActionBar(Component.text((mine ? "Вы: " : e.side() < 0 ? "" : "Враг: ") + e.text(), color));
            effects(p, e, mine);
        }
    }

    private void effects(Player p, CombatEvent e, boolean mine) {
        var loc = p.getLocation().add(0, 1, 0);
        switch (e.type()) {
            case HIT -> p.playSound(loc, mine ? Sound.ENTITY_PLAYER_ATTACK_STRONG : Sound.ENTITY_PLAYER_HURT, 0.5f, 1f);
            case CRIT -> {
                p.playSound(loc, Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.7f, 1f);
                p.spawnParticle(Particle.CRIT, loc, 12, 0.4, 0.4, 0.4, 0.1);
            }
            case BLOCK -> p.playSound(loc, Sound.ITEM_SHIELD_BLOCK, 0.6f, 1f);
            case ABILITY -> {
                p.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 1.6f);
                p.spawnParticle(Particle.ENCHANT, loc, 30, 0.5, 0.6, 0.5, 0.5);
            }
            case STATUS -> {
                if (e.text().startsWith("Ожог")) {
                    p.spawnParticle(Particle.FLAME, loc, 6, 0.3, 0.4, 0.3, 0.01);
                }
            }
            case HEAL -> p.spawnParticle(Particle.HEART, loc.add(0, 0.8, 0), 2, 0.3, 0.2, 0.3, 0);
            default -> {
            }
        }
    }

    private void updateBars(Bars b, double hp0, double hp1, double armor0, double armor1) {
        int me = b.viewer.side();
        double[] hp = {hp0, hp1};
        double[] armor = {armor0, armor1};
        b.self.name(label(result.names()[me], hp[me], result.maxHp()[me], armor[me], NamedTextColor.GREEN));
        b.self.progress(progress(hp[me], result.maxHp()[me]));
        b.enemy.name(label(result.names()[1 - me], hp[1 - me], result.maxHp()[1 - me], armor[1 - me], NamedTextColor.RED));
        b.enemy.progress(progress(hp[1 - me], result.maxHp()[1 - me]));
    }

    private static Component label(String name, double hp, double max, double armor, TextColor color) {
        String text = String.format(Locale.ROOT, "%s  ❤ %.0f/%.0f", name, Math.max(0, hp), max);
        if (armor > 0) {
            text += String.format(Locale.ROOT, "  ⛨ %.0f", armor);
        }
        return Component.text(text, color);
    }

    private static float progress(double hp, double max) {
        return (float) Math.max(0, Math.min(1, hp / max));
    }
}
