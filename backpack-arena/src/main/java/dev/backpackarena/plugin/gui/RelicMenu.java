package dev.backpackarena.plugin.gui;

import dev.backpackarena.core.model.RelicDef;
import dev.backpackarena.plugin.BackpackArenaPlugin;
import dev.backpackarena.plugin.session.PlayerSession;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.List;

/** Выбор реликвии (1 из N) в раунде relic-round. Закрыть меню без выбора нельзя — оно откроется снова. */
public final class RelicMenu extends Menu {

    private final BackpackArenaPlugin plugin;
    private final PlayerSession session;

    public RelicMenu(BackpackArenaPlugin plugin, Player viewer, PlayerSession session) {
        super(viewer, 3, Component.text("Выберите реликвию", NamedTextColor.DARK_PURPLE));
        this.plugin = plugin;
        this.session = session;
    }

    private int slotOf(int index, int count) {
        return 13 - (count - 1) + index * 2;
    }

    @Override
    public void render() {
        List<RelicDef> choices = session.relicChoices();
        for (int i = 0; i < choices.size(); i++) {
            getInventory().setItem(slotOf(i, choices.size()), ItemRenderer.relic(choices.get(i)));
        }
        Controls.render(viewer, session);
    }

    @Override
    public void clickTop(int slot, InventoryClickEvent event) {
        List<RelicDef> choices = session.relicChoices();
        for (int i = 0; i < choices.size(); i++) {
            if (slotOf(i, choices.size()) == slot) {
                plugin.matches().chooseRelic(viewer, session, choices.get(i));
                return;
            }
        }
    }

    @Override
    public void onClose() {
        if (!session.relicChoices().isEmpty()) {
            plugin.matches().reopenLater(viewer);
        }
    }
}
