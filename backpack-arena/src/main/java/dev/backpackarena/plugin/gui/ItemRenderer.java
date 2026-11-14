package dev.backpackarena.plugin.gui;

import dev.backpackarena.core.grid.ItemInstance;
import dev.backpackarena.core.model.EffectDef;
import dev.backpackarena.core.model.HeroDef;
import dev.backpackarena.core.model.ItemDef;
import dev.backpackarena.core.model.RelicDef;
import dev.backpackarena.core.model.WeaponStats;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Превращает определения из core в ItemStack с названием и описанием. */
public final class ItemRenderer {

    private static final List<Material> BAG_PANES = List.of(
            Material.LIME_STAINED_GLASS_PANE, Material.LIGHT_BLUE_STAINED_GLASS_PANE,
            Material.YELLOW_STAINED_GLASS_PANE, Material.ORANGE_STAINED_GLASS_PANE,
            Material.MAGENTA_STAINED_GLASS_PANE, Material.CYAN_STAINED_GLASS_PANE,
            Material.PINK_STAINED_GLASS_PANE, Material.WHITE_STAINED_GLASS_PANE);

    private ItemRenderer() {
    }

    public static Material material(String name) {
        Material m = Material.matchMaterial(name.toUpperCase(Locale.ROOT));
        return m != null && m.isItem() ? m : Material.PAPER;
    }

    public static ItemStack button(Material material, String name, TextColor color, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Msg.plain(name, color).decoration(TextDecoration.BOLD, true));
        List<Component> lines = new ArrayList<>();
        for (String l : lore) {
            lines.add(Msg.plain(l, NamedTextColor.GRAY));
        }
        meta.lore(lines);
        meta.addItemFlags(ItemFlag.values());
        stack.setItemMeta(meta);
        return stack;
    }

    /** Предмет с полной подсказкой. {@code extra} — строки-подсказки управления. */
    public static ItemStack item(ItemDef def, ItemInstance inst, int price, boolean glint, List<String> extra) {
        ItemStack stack = new ItemStack(material(def.material()));
        ItemMeta meta = stack.getItemMeta();
        TextColor color = TextColor.color(def.rarity().color());
        meta.displayName(Msg.plain(def.name(), color));

        List<Component> lore = new ArrayList<>();
        lore.add(Msg.plain(def.rarity().displayName() + " · " + def.category().displayName()
                + " · " + def.shape().describe(), color));
        if (def.hero() != null) {
            lore.add(Msg.plain("Предмет героя", NamedTextColor.YELLOW));
        }
        WeaponStats w = def.weapon();
        if (w != null) {
            lore.add(Msg.plain(String.format(Locale.ROOT, "Урон %.0f–%.0f · КД %.1f с · DPS %.1f",
                    w.minDamage(), w.maxDamage(), w.cooldown(), w.dps()), NamedTextColor.RED));
            String stamina = w.pet() ? "без выносливости" : String.format(Locale.ROOT, "выносл. %.2f", w.stamina());
            lore.add(Msg.plain(String.format(Locale.ROOT, "Точность %.0f%% · крит %.0f%% · %s · %s",
                    w.accuracy() * 100, w.critChance() * 100, stamina,
                    w.pet() ? "питомец" : w.ranged() ? "дальний" : "ближний"), NamedTextColor.GRAY));
        }
        for (EffectDef e : def.effects()) {
            lore.add(Msg.plain("• " + e.describe(), NamedTextColor.AQUA));
        }
        if (!def.description().isEmpty()) {
            lore.add(Msg.plain(def.description(), NamedTextColor.DARK_GRAY));
        }
        if (price >= 0) {
            lore.add(Msg.plain("Цена: " + price + " зол.", NamedTextColor.GOLD));
        }
        if (inst != null && inst.craftLocked()) {
            lore.add(Msg.plain("Заблокирован для крафта", NamedTextColor.DARK_RED));
        }
        if (glint) {
            lore.add(Msg.plain("Рецепт готов — объединится после боя", NamedTextColor.GREEN));
        }
        for (String x : extra) {
            lore.add(Msg.plain(x, NamedTextColor.DARK_GRAY));
        }
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.values());
        if (glint) {
            meta.setEnchantmentGlintOverride(true);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    /** Пустая клетка сумки. */
    public static ItemStack bagCell(ItemDef bag, int bagIndex) {
        ItemStack stack = new ItemStack(BAG_PANES.get(bagIndex % BAG_PANES.size()));
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Msg.plain(bag.name() + " — свободно", NamedTextColor.GRAY));
        meta.lore(List.of(
                Msg.plain("Клик с предметом в руке — положить сюда", NamedTextColor.DARK_GRAY),
                Msg.plain("(клетка = левый верхний угол предмета)", NamedTextColor.DARK_GRAY),
                Msg.plain("Клик без предмета — забрать пустую сумку", NamedTextColor.DARK_GRAY)));
        stack.setItemMeta(meta);
        return stack;
    }

    /** Клетка вне сумок. */
    public static ItemStack emptyCell() {
        ItemStack stack = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Msg.plain("Пусто", NamedTextColor.DARK_GRAY));
        meta.lore(List.of(Msg.plain("Сюда можно поставить сумку", NamedTextColor.DARK_GRAY)));
        stack.setItemMeta(meta);
        return stack;
    }

    public static ItemStack hero(HeroDef hero) {
        ItemStack stack = new ItemStack(material(hero.material()));
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Msg.plain(hero.name() + " — " + hero.title(), NamedTextColor.GOLD)
                .decoration(TextDecoration.BOLD, true));
        List<Component> lore = new ArrayList<>();
        lore.add(Msg.plain(hero.description(), NamedTextColor.GRAY));
        lore.add(Msg.plain(String.format(Locale.ROOT, "HP %.0f · броня %.0f · выносл. %.0f (+%.1f/с)",
                hero.maxHp(), hero.startArmor(), hero.maxStamina(), hero.staminaRegen()), NamedTextColor.WHITE));
        if (hero.maxMana() > 0) {
            lore.add(Msg.plain(String.format(Locale.ROOT, "Мана %.0f (+%.1f/с)", hero.maxMana(), hero.manaRegen()),
                    NamedTextColor.BLUE));
        }
        if (hero.critChance() > 0) {
            lore.add(Msg.plain(String.format(Locale.ROOT, "Крит %.0f%%", hero.critChance() * 100), NamedTextColor.WHITE));
        }
        lore.add(Component.empty());
        lore.add(Msg.plain("Способность: " + hero.ability().name(), NamedTextColor.AQUA));
        lore.add(Msg.plain(hero.ability().description(), NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(Msg.plain("Клик — выбрать", NamedTextColor.GREEN));
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.values());
        stack.setItemMeta(meta);
        return stack;
    }

    public static ItemStack relic(RelicDef relic) {
        ItemStack stack = new ItemStack(material(relic.material()));
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Msg.plain(relic.name(), TextColor.color(relic.rarity().color())));
        meta.lore(List.of(Msg.plain(relic.description(), NamedTextColor.GRAY),
                Msg.plain("Клик — взять реликвию", NamedTextColor.GREEN)));
        meta.setEnchantmentGlintOverride(true);
        meta.addItemFlags(ItemFlag.values());
        stack.setItemMeta(meta);
        return stack;
    }
}
