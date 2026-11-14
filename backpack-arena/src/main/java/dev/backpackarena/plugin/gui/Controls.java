package dev.backpackarena.plugin.gui;

import dev.backpackarena.core.crafting.CraftingEngine;
import dev.backpackarena.core.grid.ItemInstance;
import dev.backpackarena.core.run.RunState;
import dev.backpackarena.plugin.session.PlayerSession;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * Нижний инвентарь игрока во время матча: хотбар — кнопки управления,
 * основные 27 ячеек — временное хранилище предметов.
 */
public final class Controls {

    public enum Control {
        HAND(0), ROTATE(1), SELL(2), SHOP(3), BACKPACK(4), FIGHT(7), LEAVE(8);

        private final int slot;

        Control(int slot) {
            this.slot = slot;
        }

        public int slot() {
            return slot;
        }

        public static Control bySlot(int slot) {
            for (Control c : values()) {
                if (c.slot == slot) {
                    return c;
                }
            }
            return null;
        }
    }

    public static final int STORAGE_FIRST_SLOT = 9;
    public static final int STORAGE_SLOTS = 27;

    private static NamespacedKey key;

    private Controls() {
    }

    public static void init(Plugin plugin) {
        key = new NamespacedKey(plugin, "control");
    }

    public static Control controlOf(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        String v = stack.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (v == null) {
            return null;
        }
        try {
            return Control.valueOf(v);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Перерисовывает хотбар и хранилище игрока. */
    public static void render(Player player, PlayerSession session) {
        PlayerInventory inv = player.getInventory();
        RunState run = session.run();
        inv.clear();

        ItemInstance hand = session.hand();
        inv.setItem(Control.HAND.slot(), hand == null
                ? tag(ItemRenderer.button(Material.GRAY_DYE, "В руке: пусто", NamedTextColor.GRAY,
                "Возьмите предмет из хранилища или рюкзака"), Control.HAND)
                : tag(ItemRenderer.item(hand.def(), hand, -1, false,
                List.of("В руке. Клик по клетке рюкзака — положить", "ПКМ — повернуть")), Control.HAND));
        inv.setItem(Control.ROTATE.slot(), tag(ItemRenderer.button(Material.COMPASS, "Повернуть", NamedTextColor.AQUA,
                "Повернуть предмет в руке на 90°"), Control.ROTATE));
        int sell = hand == null ? 0 : hand.def().sellPrice(run.content().economy().sellRatio());
        inv.setItem(Control.SELL.slot(), tag(ItemRenderer.button(Material.GOLD_NUGGET, "Продать", NamedTextColor.GOLD,
                hand == null ? "Возьмите предмет в руку" : "Продать «" + hand.def().name() + "» за " + sell + " зол.",
                "Shift+клик по предмету в хранилище — продать сразу"), Control.SELL));
        inv.setItem(Control.SHOP.slot(), tag(ItemRenderer.button(Material.EMERALD, "Магазин", NamedTextColor.GREEN,
                "Золото: " + run.gold()), Control.SHOP));
        inv.setItem(Control.BACKPACK.slot(), tag(ItemRenderer.button(Material.CHEST, "Рюкзак", NamedTextColor.YELLOW,
                "Раскладка предметов 6×9"), Control.BACKPACK));
        int ready = CraftingEngine.findMatches(run.grid(), run.content().recipes()).size();
        inv.setItem(Control.FIGHT.slot(), tag(ItemRenderer.button(Material.IRON_SWORD,
                session.ready() ? "Ждём соперника…" : "В бой!", NamedTextColor.RED,
                "Раунд " + run.round() + " · жизни " + run.lives() + " · победы " + run.wins()
                        + "/" + run.content().economy().winsToFinish(),
                ready > 0 ? "Готовых рецептов: " + ready : "Готовых рецептов нет"), Control.FIGHT));
        inv.setItem(Control.LEAVE.slot(), tag(ItemRenderer.button(Material.BARRIER, "Выйти из матча",
                NamedTextColor.DARK_RED, "Нажмите дважды для подтверждения"), Control.LEAVE));

        List<ItemInstance> storage = run.storage();
        for (int i = 0; i < Math.min(storage.size(), STORAGE_SLOTS); i++) {
            ItemInstance inst = storage.get(i);
            inv.setItem(STORAGE_FIRST_SLOT + i, ItemRenderer.item(inst.def(), inst, -1, false,
                    List.of("Хранилище. Клик — взять в руку", "Shift+клик — продать за "
                            + inst.def().sellPrice(run.content().economy().sellRatio()) + " зол.")));
        }
        player.updateInventory();
    }

    private static ItemStack tag(ItemStack stack, Control control) {
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, control.name());
        stack.setItemMeta(meta);
        return stack;
    }
}
