package dev.backpackarena.plugin.gui;

import dev.backpackarena.core.model.HeroDef;
import dev.backpackarena.plugin.BackpackArenaPlugin;
import dev.backpackarena.plugin.match.Match;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.ArrayList;
import java.util.List;

/** Выбор героя перед матчем. */
public final class HeroMenu extends Menu {

    private final BackpackArenaPlugin plugin;
    private final Match.Mode mode;
    private final List<HeroDef> heroes;
    private final int[] slots;

    public HeroMenu(BackpackArenaPlugin plugin, Player viewer, Match.Mode mode) {
        super(viewer, 3, Component.text("Выбор героя — " + mode.displayName(), NamedTextColor.DARK_GRAY));
        this.plugin = plugin;
        this.mode = mode;
        this.heroes = new ArrayList<>(plugin.content().heroes().values());
        this.slots = centeredSlots(Math.min(heroes.size(), 7));
    }

    /** Слоты средней строки, расставленные с промежутками по центру. */
    private static int[] centeredSlots(int n) {
        int[] result = new int[n];
        int step = n <= 4 ? 2 : 1;
        int width = (n - 1) * step + 1;
        int start = 9 + (9 - width) / 2;
        for (int i = 0; i < n; i++) {
            result[i] = start + i * step;
        }
        return result;
    }

    @Override
    public void render() {
        for (int i = 0; i < slots.length; i++) {
            getInventory().setItem(slots[i], ItemRenderer.hero(heroes.get(i)));
        }
    }

    @Override
    public void clickTop(int slot, InventoryClickEvent event) {
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == slot) {
                HeroDef hero = heroes.get(i);
                plugin.matches().pickHero(viewer, mode, hero);
                return;
            }
        }
    }
}
