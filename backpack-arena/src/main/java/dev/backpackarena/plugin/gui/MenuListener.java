package dev.backpackarena.plugin.gui;

import dev.backpackarena.plugin.BackpackArenaPlugin;
import dev.backpackarena.plugin.session.PlayerSession;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Перехватывает все клики в меню и в инвентаре игрока во время матча.
 * Предметы режима никогда не попадают в обычный инвентарь как «настоящие» вещи.
 */
public final class MenuListener implements Listener {

    private final BackpackArenaPlugin plugin;

    public MenuListener(BackpackArenaPlugin plugin) {
        this.plugin = plugin;
        Controls.init(plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        InventoryHolder holder = event.getView().getTopInventory().getHolder(false);
        boolean inMatch = plugin.sessions().inMatch(player);
        if (!(holder instanceof Menu menu)) {
            if (inMatch) {
                // инвентарь игрока без меню (клавиша E): хотбар и хранилище работают как кнопки
                event.setCancelled(true);
                if (event.getClickedInventory() == player.getInventory()) {
                    PlayerSession session = plugin.sessions().get(player);
                    plugin.matches().clickPlayerInventory(player, session, event.getSlot(), event);
                }
            }
            return;
        }
        event.setCancelled(true);
        Inventory clicked = event.getClickedInventory();
        if (clicked == null) {
            return;
        }
        if (clicked == event.getView().getTopInventory()) {
            menu.clickTop(event.getSlot(), event);
        } else if (inMatch) {
            menu.clickBottom(event.getSlot(), event);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof Menu
                || (event.getWhoClicked() instanceof Player p && plugin.sessions().inMatch(p))) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder(false) instanceof Menu menu
                && event.getReason() != InventoryCloseEvent.Reason.OPEN_NEW) {
            menu.onClose();
        }
    }

    @EventHandler(ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!plugin.sessions().inMatch(player)) {
            return;
        }
        event.setCancelled(true);
        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Controls.Control control = Controls.controlOf(event.getItem());
            if (control != null) {
                plugin.matches().useControl(player, plugin.sessions().get(player), control);
            }
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (plugin.sessions().inMatch(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (plugin.sessions().inMatch(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player p && plugin.sessions().inMatch(p)) {
            event.setCancelled(true);
        }
    }
}
