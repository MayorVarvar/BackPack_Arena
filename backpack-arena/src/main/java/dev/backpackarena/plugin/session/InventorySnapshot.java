package dev.backpackarena.plugin.session;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Инвентарь и позиция игрока до матча — возвращаются после выхода. */
public record InventorySnapshot(ItemStack[] contents, Location location, GameMode gameMode) {

    public static InventorySnapshot take(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        ItemStack[] copy = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            copy[i] = contents[i] == null ? null : contents[i].clone();
        }
        return new InventorySnapshot(copy, player.getLocation().clone(), player.getGameMode());
    }

    public void restore(Player player) {
        player.closeInventory();
        player.getInventory().clear();
        player.getInventory().setContents(contents);
        player.setGameMode(gameMode);
        if (location.getWorld() != null) {
            player.teleport(location);
        }
        player.updateInventory();
    }
}
