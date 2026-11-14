package dev.backpackarena.plugin.gui;

import dev.backpackarena.core.crafting.CraftingEngine;
import dev.backpackarena.core.grid.BackpackGrid;
import dev.backpackarena.core.grid.ItemInstance;
import dev.backpackarena.core.grid.PlacedPiece;
import dev.backpackarena.core.model.Cell;
import dev.backpackarena.core.run.RunState;
import dev.backpackarena.plugin.BackpackArenaPlugin;
import dev.backpackarena.plugin.session.PlayerSession;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Рюкзак 6×9 = двойной сундук: слот = клетка (x = slot % 9, y = slot / 9).
 *
 * ЛКМ без предмета в руке — взять предмет (или пустую сумку);
 * ЛКМ с предметом — положить (клетка = левый верхний угол формы);
 * ПКМ — повернуть предмет в руке или на месте; Shift+ЛКМ — заблокировать от крафта.
 */
public final class BackpackMenu extends Menu {

    private final BackpackArenaPlugin plugin;
    private final PlayerSession session;

    public BackpackMenu(BackpackArenaPlugin plugin, Player viewer, PlayerSession session) {
        super(viewer, BackpackGrid.HEIGHT, Component.text("Рюкзак — " + session.run().hero().name(),
                NamedTextColor.DARK_GRAY));
        this.plugin = plugin;
        this.session = session;
    }

    @Override
    public void render() {
        RunState run = session.run();
        BackpackGrid grid = run.grid();
        Set<PlacedPiece> craftReady = new HashSet<>();
        for (CraftingEngine.Match m : CraftingEngine.findMatches(grid, run.content().recipes())) {
            craftReady.addAll(m.pieces());
        }
        List<PlacedPiece> bags = grid.bags();
        for (int y = 0; y < BackpackGrid.HEIGHT; y++) {
            for (int x = 0; x < BackpackGrid.WIDTH; x++) {
                Cell cell = new Cell(x, y);
                int slot = y * BackpackGrid.WIDTH + x;
                PlacedPiece item = grid.itemAt(cell);
                PlacedPiece bag = grid.bagAt(cell);
                if (item != null) {
                    ItemInstance inst = item.item();
                    getInventory().setItem(slot, ItemRenderer.item(inst.def(), inst, -1, craftReady.contains(item),
                            List.of("ЛКМ — взять · ПКМ — повернуть", "Shift+ЛКМ — блокировка крафта")));
                } else if (bag != null) {
                    getInventory().setItem(slot, ItemRenderer.bagCell(bag.item().def(), bags.indexOf(bag)));
                } else {
                    getInventory().setItem(slot, ItemRenderer.emptyCell());
                }
            }
        }
        Controls.render(viewer, session);
    }

    @Override
    public void clickTop(int slot, InventoryClickEvent event) {
        BackpackGrid grid = session.run().grid();
        Cell cell = new Cell(slot % BackpackGrid.WIDTH, slot / BackpackGrid.WIDTH);
        PlacedPiece item = grid.itemAt(cell);
        ItemInstance hand = session.hand();

        if (event.isShiftClick() && item != null) {
            item.item().setCraftLocked(!item.item().craftLocked());
            Msg.bar(viewer, item.item().craftLocked() ? "Заблокировано для крафта" : "Блокировка снята",
                    NamedTextColor.YELLOW);
        } else if (event.isRightClick()) {
            if (hand != null) {
                rotateHand(hand);
            } else if (item != null) {
                rotateInPlace(grid, item);
            }
        } else if (hand != null) {
            BackpackGrid.Result r = grid.place(hand, cell.x(), cell.y());
            if (r == BackpackGrid.Result.OK) {
                session.setHand(null);
                viewer.playSound(viewer.getLocation(), Sound.ITEM_BUNDLE_INSERT, 0.8f, 1f);
            } else {
                Msg.bar(viewer, r.message(), NamedTextColor.RED);
                viewer.playSound(viewer.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1f);
            }
        } else if (item != null) {
            grid.remove(item);
            session.setHand(item.item());
            viewer.playSound(viewer.getLocation(), Sound.ITEM_BUNDLE_REMOVE_ONE, 0.8f, 1f);
        } else {
            PlacedPiece bag = grid.bagAt(cell);
            if (bag != null) {
                BackpackGrid.Result r = grid.remove(bag);
                if (r == BackpackGrid.Result.OK) {
                    session.setHand(bag.item());
                } else {
                    Msg.bar(viewer, r.message(), NamedTextColor.RED);
                }
            }
        }
        render();
    }

    private void rotateHand(ItemInstance hand) {
        if (!hand.rotate()) {
            Msg.bar(viewer, "Этот предмет нельзя поворачивать", NamedTextColor.RED);
        }
    }

    private void rotateInPlace(BackpackGrid grid, PlacedPiece piece) {
        ItemInstance inst = piece.item();
        if (!inst.def().rotatable()) {
            Msg.bar(viewer, "Этот предмет нельзя поворачивать", NamedTextColor.RED);
            return;
        }
        grid.remove(piece);
        int original = inst.rotation();
        inst.rotate();
        if (grid.place(inst, piece.x(), piece.y()) != BackpackGrid.Result.OK) {
            inst.setRotation(original);
            grid.place(inst, piece.x(), piece.y());
            Msg.bar(viewer, "Не хватает места для поворота", NamedTextColor.RED);
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
