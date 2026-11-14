package dev.backpackarena.plugin.gui;

import dev.backpackarena.core.economy.Shop;
import dev.backpackarena.core.run.RunState;
import dev.backpackarena.plugin.BackpackArenaPlugin;
import dev.backpackarena.plugin.session.PlayerSession;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Магазин между боями.
 * Строка 2 — предложения (ЛКМ купить, ПКМ резерв), строка 3 — реролл, ускоренный крафт,
 * в бой, рюкзак. Хранилище и кнопки — в инвентаре игрока.
 */
public final class ShopMenu extends Menu {

    private static final int INFO = 4;
    private static final int FIRST_OFFER = 11;
    private static final int REROLL = 18;
    private static final int HAMMER = 20;
    private static final int FIGHT = 22;
    private static final int BACKPACK = 26;

    private final BackpackArenaPlugin plugin;
    private final PlayerSession session;

    public ShopMenu(BackpackArenaPlugin plugin, Player viewer, PlayerSession session) {
        super(viewer, 3, Component.text("Магазин — раунд " + session.run().round(), NamedTextColor.DARK_GREEN));
        this.plugin = plugin;
        this.session = session;
    }

    @Override
    public void render() {
        RunState run = session.run();
        Shop shop = run.shop();
        getInventory().clear();
        getInventory().setItem(INFO, ItemRenderer.button(Material.GOLD_INGOT, "Золото: " + run.gold(),
                NamedTextColor.GOLD,
                "Раунд " + run.round() + " · жизни " + run.lives() + " · победы " + run.wins()
                        + "/" + run.content().economy().winsToFinish(),
                "Герой: " + run.hero().name(),
                "Реликвии: " + run.relics().size()));

        List<Shop.Offer> offers = shop.offers();
        for (int i = 0; i < offers.size() && i < 5; i++) {
            Shop.Offer offer = offers.get(i);
            int slot = FIRST_OFFER + i;
            if (offer == null) {
                getInventory().setItem(slot, ItemRenderer.button(Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                        "Продано", NamedTextColor.GRAY));
                continue;
            }
            List<String> hints = new ArrayList<>();
            if (offer.onSale()) {
                hints.add("СКИДКА! Было " + offer.item().price() + " зол.");
            }
            hints.add(offer.reserved() ? "В резерве (сохранится при обновлении)" : "ПКМ — оставить в резерве");
            hints.add("ЛКМ — купить");
            getInventory().setItem(slot, ItemRenderer.item(offer.item(), null, shop.priceOf(offer),
                    offer.reserved(), hints));
        }

        getInventory().setItem(REROLL, ItemRenderer.button(Material.SUNFLOWER,
                "Обновить (" + shop.nextRerollCost() + " зол.)", NamedTextColor.YELLOW,
                "Новые предложения; резерв останется",
                "Обновлений в этом раунде: " + shop.rerollsThisRound()));
        getInventory().setItem(HAMMER, ItemRenderer.button(Material.ANVIL,
                "Ускоренный крафт (" + run.content().economy().craftHammerCost(run.hammerUses()) + " зол.)",
                NamedTextColor.LIGHT_PURPLE, "Объединить готовые рецепты сейчас, не дожидаясь боя"));
        getInventory().setItem(FIGHT, ItemRenderer.button(Material.IRON_SWORD,
                session.ready() ? "Ждём соперника…" : "В бой!", NamedTextColor.RED,
                "Бой идёт автоматически"));
        getInventory().setItem(BACKPACK, ItemRenderer.button(Material.CHEST, "Рюкзак", NamedTextColor.YELLOW,
                "Разложить купленные предметы"));
        Controls.render(viewer, session);
    }

    @Override
    public void clickTop(int slot, InventoryClickEvent event) {
        Shop shop = session.run().shop();
        if (slot >= FIRST_OFFER && slot < FIRST_OFFER + 5) {
            int index = slot - FIRST_OFFER;
            Shop.Result r = event.isRightClick() ? shop.toggleReserve(index) : shop.buy(index);
            feedback(r, event.isRightClick() ? null : Sound.ENTITY_EXPERIENCE_ORB_PICKUP);
        } else if (slot == REROLL) {
            feedback(shop.reroll(), Sound.UI_BUTTON_CLICK);
        } else if (slot == HAMMER) {
            plugin.matches().craftNow(viewer, session);
        } else if (slot == FIGHT) {
            plugin.matches().ready(viewer);
            return;
        } else if (slot == BACKPACK) {
            plugin.matches().openBackpack(viewer);
            return;
        }
        render();
    }

    private void feedback(Shop.Result r, Sound sound) {
        if (r == Shop.Result.OK) {
            if (sound != null) {
                viewer.playSound(viewer.getLocation(), sound, 0.7f, 1.2f);
            }
        } else {
            Msg.bar(viewer, r.message(), NamedTextColor.RED);
            viewer.playSound(viewer.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1f);
        }
    }

    @Override
    public void clickBottom(int slot, InventoryClickEvent event) {
        plugin.matches().clickPlayerInventory(viewer, session, slot, event);
        render();
    }

    @Override
    public void onClose() {
        session.stashHand();
    }
}
