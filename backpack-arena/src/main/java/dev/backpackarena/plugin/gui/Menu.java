package dev.backpackarena.plugin.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Базовое меню на основе сундука. Все клики отменяются в {@link MenuListener};
 * меню получает уже «чистое» событие: слот верхнего или нижнего инвентаря.
 */
public abstract class Menu implements InventoryHolder {

    protected final Player viewer;
    private final Inventory inventory;

    protected Menu(Player viewer, int rows, Component title) {
        this.viewer = viewer;
        this.inventory = Bukkit.createInventory(this, rows * 9, title);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void open() {
        render();
        viewer.openInventory(inventory);
    }

    public abstract void render();

    /** Клик по верхнему инвентарю меню. */
    public abstract void clickTop(int slot, InventoryClickEvent event);

    /** Клик по инвентарю игрока, пока меню открыто. По умолчанию ничего не делает. */
    public void clickBottom(int slot, InventoryClickEvent event) {
    }

    /** Меню закрыто игроком (не программно при смене меню). */
    public void onClose() {
    }
}
