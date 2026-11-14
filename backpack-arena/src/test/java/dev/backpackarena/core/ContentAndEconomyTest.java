package dev.backpackarena.core;

import dev.backpackarena.core.content.ContentException;
import dev.backpackarena.core.content.ContentLoader;
import dev.backpackarena.core.crafting.CraftingEngine;
import dev.backpackarena.core.economy.Shop;
import dev.backpackarena.core.grid.ItemInstance;
import dev.backpackarena.core.model.Cell;
import dev.backpackarena.core.model.HeroDef;
import dev.backpackarena.core.model.Rarity;
import dev.backpackarena.core.run.RunState;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentAndEconomyTest {

    @Test
    void defaultContentLoads() {
        var content = TestContent.get();
        assertEquals(4, content.heroes().size());
        // в контенте есть все 6 редкостей (критерий MVP)
        for (Rarity r : Rarity.values()) {
            assertTrue(content.allItems().stream().anyMatch(i -> i.rarity() == r), "нет предметов " + r);
        }
    }

    @Test
    void startingKitsArePlaced() {
        var content = TestContent.get();
        for (HeroDef hero : content.heroes().values()) {
            RunState run = new RunState("t", hero, content, new Random(1));
            assertEquals(hero.startingBags().size(), run.grid().bags().size(), hero.id());
            assertEquals(hero.startingItems().size(), run.grid().items().size(), hero.id());
            assertTrue(run.storage().isEmpty(), hero.id());
            assertEquals(content.economy().startGold(), run.gold());
        }
    }

    @Test
    void brokenConfigReportsAllErrors() {
        Map<String, Map<String, Object>> files = new HashMap<>();
        for (String f : ContentLoader.FILES) {
            files.put(f, TestContent.read(f));
        }
        Map<String, Object> recipes = Map.of("recipes", Map.of(
                "bad", Map.of("ingredients", List.of("nope", "apple"), "result", "missing")));
        files.put("recipes.yml", recipes);
        ContentException ex = assertThrows(ContentException.class, () -> ContentLoader.load(files::get));
        assertEquals(2, ex.problems().size(), ex.getMessage());
    }

    @Test
    void rerollCostsFollowSchedule() {
        var eco = TestContent.get().economy();
        int[] costs = new int[13];
        for (int i = 0; i < costs.length; i++) {
            costs[i] = eco.rerollCost(i + 1);
        }
        assertEquals("[1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 3, 3, 3]", Arrays.toString(costs));
    }

    @Test
    void incomeModel() {
        var eco = TestContent.get().economy();
        assertEquals(10, eco.income(1, false, 0));
        assertEquals(8, eco.income(2, false, 0));
        assertEquals(8 + 2 + 1, eco.income(2, true, 1));
        assertEquals(14, eco.income(30, false, 0));
        assertEquals(14 + 2 + 3, eco.income(30, true, 10));
    }

    @Test
    void buySellAndNoNegativeGold() {
        var content = TestContent.get();
        RunState run = new RunState("t", content.hero("bastion"), content, new Random(7));
        Shop shop = run.shop();
        int slot = 0;
        while (shop.offers().get(slot) == null) {
            slot++;
        }
        int price = shop.priceOf(shop.offers().get(slot));
        int before = run.gold();
        assertEquals(Shop.Result.OK, shop.buy(slot));
        assertEquals(before - price, run.gold());
        assertEquals(1, run.storage().size());

        ItemInstance bought = run.storage().get(0);
        assertEquals(Shop.Result.OK, shop.sell(bought));
        assertEquals(before - price + (int) Math.ceil(bought.def().price() * 0.5), run.gold());

        while (run.gold() > 0) {
            run.spend(1);
        }
        assertFalse(run.spend(1));
        assertEquals(Shop.Result.NOT_ENOUGH_GOLD, shop.reroll());
        assertEquals(0, run.gold());
    }

    @Test
    void reservedOfferSurvivesReroll() {
        var content = TestContent.get();
        RunState run = new RunState("t", content.hero("silva"), content, new Random(3));
        Shop.Offer first = run.shop().offers().get(0);
        run.shop().toggleReserve(0);
        run.addGold(100);
        for (int i = 0; i < 5; i++) {
            run.shop().reroll();
        }
        assertTrue(run.shop().offers().get(0) == first);
    }

    @Test
    void craftingNeedsAdjacencyAndRespectsLock() {
        var content = TestContent.get();
        RunState run = new RunState("t", content.hero("bastion"), content, new Random(1));
        // освобождаем стартовые предметы
        for (var p : List.copyOf(run.grid().items())) {
            run.grid().remove(p);
        }
        var grid = run.grid();
        grid.place(run.newInstance(content.item("great_backpack")), 4, 0);
        ItemInstance shield = run.newInstance(content.item("shield"));
        ItemInstance cactus = run.newInstance(content.item("cactus"));
        assertEquals(dev.backpackarena.core.grid.BackpackGrid.Result.OK, grid.place(shield, 4, 0));
        // далеко — крафта нет
        grid.place(cactus, 6, 2);
        assertTrue(CraftingEngine.findMatches(grid, content.recipes()).isEmpty());
        // рядом, но заблокирован
        grid.remove(grid.itemAt(new Cell(6, 2)));
        grid.place(cactus, 6, 0);
        cactus.setCraftLocked(true);
        assertTrue(CraftingEngine.findMatches(grid, content.recipes()).isEmpty());
        cactus.setCraftLocked(false);

        var created = CraftingEngine.craftAll(run);
        assertEquals(1, created.size());
        assertEquals("spiked_shield", created.get(0).def().id());
        assertEquals("spiked_shield", grid.itemAt(new Cell(4, 0)).item().def().id());
        assertEquals(1, grid.items().size());
    }

    @Test
    void threeItemRecipeNeedsConnectedGroup() {
        var content = TestContent.get();
        RunState run = new RunState("t", content.hero("bortnik"), content, new Random(1));
        for (var p : List.copyOf(run.grid().items())) {
            run.grid().remove(p);
        }
        var grid = run.grid();
        grid.place(run.newInstance(content.item("long_tube")), 0, 5); // 4×1 в нижней строке
        grid.place(run.newInstance(content.item("bee")), 0, 5);
        grid.place(run.newInstance(content.item("honey_bottle")), 1, 5);
        grid.place(run.newInstance(content.item("bee")), 2, 5);
        assertEquals(1, CraftingEngine.findMatches(grid, content.recipes()).size());
        assertEquals("beehive", CraftingEngine.craftAll(run).get(0).def().id());
    }
}
