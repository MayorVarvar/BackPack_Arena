package dev.backpackarena.core;

import dev.backpackarena.core.bot.BotFactory;
import dev.backpackarena.core.combat.CombatEvent;
import dev.backpackarena.core.combat.CombatResult;
import dev.backpackarena.core.combat.CombatSimulator;
import dev.backpackarena.core.combat.FighterSpec;
import dev.backpackarena.core.content.GameContent;
import dev.backpackarena.core.crafting.CraftingEngine;
import dev.backpackarena.core.model.HeroDef;
import dev.backpackarena.core.model.ItemDef;
import dev.backpackarena.core.run.RunState;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatTest {

    private final GameContent content = TestContent.get();

    private FighterSpec spec(String hero, String... items) {
        return new FighterSpec(hero, content.hero(hero),
                Arrays.stream(items).map(content::item).toList(), List.of());
    }

    @Test
    void sameSeedSameResult() {
        FighterSpec a = spec("bastion", "iron_sword", "shield");
        FighterSpec b = spec("elma", "blaze_rod", "amethyst_shard", "frost_crystal");
        CombatResult r1 = CombatSimulator.simulate(a, b, content.combat(), 42);
        CombatResult r2 = CombatSimulator.simulate(a, b, content.combat(), 42);
        assertEquals(r1.winner(), r2.winner());
        assertEquals(r1.duration(), r2.duration());
        assertEquals(r1.events(), r2.events());
    }

    @Test
    void everyHeroPairFinishesWithinLimit() {
        for (HeroDef h1 : content.heroes().values()) {
            for (HeroDef h2 : content.heroes().values()) {
                FighterSpec a = new FighterSpec("a", h1, starting(h1), List.of());
                FighterSpec b = new FighterSpec("b", h2, starting(h2), List.of());
                CombatResult r = CombatSimulator.simulate(a, b, content.combat(), 1);
                assertTrue(r.duration() <= content.combat().maxDuration() + 1e-6);
                assertEquals(CombatEvent.Type.END, r.events().get(r.events().size() - 1).type());
            }
        }
    }

    private List<ItemDef> starting(HeroDef h) {
        return h.startingItems().stream().map(content::item).toList();
    }

    @Test
    void strongerBuildWinsMostly() {
        FighterSpec weak = spec("silva", "bow");
        FighterSpec strong = spec("silva", "netherite_axe", "iron_chestplate", "healing_potion");
        int strongWins = 0;
        for (int seed = 0; seed < 50; seed++) {
            if (CombatSimulator.simulate(weak, strong, content.combat(), seed).winner() == 1) {
                strongWins++;
            }
        }
        assertTrue(strongWins >= 45, "сильная сборка выиграла только " + strongWins);
    }

    @Test
    void armorReducesDamageTaken() {
        FighterSpec attacker = spec("silva", "iron_sword");
        FighterSpec naked = spec("silva");
        FighterSpec armored = new FighterSpec("x", content.hero("silva"),
                List.of(content.item("iron_chestplate")), List.of());
        double hpNaked = hpAfter(attacker, naked);
        double hpArmored = hpAfter(attacker, armored);
        assertTrue(hpArmored > hpNaked, hpArmored + " vs " + hpNaked);
    }

    private double hpAfter(FighterSpec attacker, FighterSpec defender) {
        // смотрим HP защитника на 10-й секунде
        CombatResult r = CombatSimulator.simulate(attacker, defender, content.combat(), 5);
        double hp = defender.hero().maxHp();
        for (CombatEvent e : r.events()) {
            if (e.time() > 10) {
                break;
            }
            hp = e.hp1();
        }
        return hp;
    }

    @Test
    void noStaminaMeansNoDamage() {
        // много тяжёлого оружия — часть атак должна сорваться из-за выносливости
        FighterSpec heavy = spec("bastion", "mace", "netherite_axe", "iron_sword", "stone_axe");
        FighterSpec dummy = new FighterSpec("dummy", content.hero("bastion"),
                List.of(content.item("guardian_shield"), content.item("iron_chestplate")), List.of());
        CombatResult r = CombatSimulator.simulate(heavy, dummy, content.combat(), 9);
        assertTrue(r.events().stream().anyMatch(e -> e.type() == CombatEvent.Type.NO_STAMINA));
    }

    @Test
    void relicsApply() {
        var withRelic = new FighterSpec("r", content.hero("bastion"), List.of(),
                List.of(content.relics().get("heart_stone")));
        var opponent = spec("bastion");
        CombatResult r = CombatSimulator.simulate(withRelic, opponent, content.combat(), 1);
        assertEquals(content.hero("bastion").maxHp() + 15, r.maxHp()[0]);
    }

    @Test
    void botsCanPlayFullRun() {
        // два бота играют полный матч — проверяем, что экономика и крафт не ломаются
        for (long seed = 0; seed < 5; seed++) {
            RunState player = BotFactory.create(content, 1, seed);
            int guard = 0;
            while (!player.isFinished() && guard++ < 40) {
                RunState enemy = BotFactory.create(content, player.round(), seed * 100 + guard);
                CombatResult r = CombatSimulator.simulate(FighterSpec.of(player), FighterSpec.of(enemy),
                        content.combat(), seed + guard);
                CraftingEngine.craftAll(player);
                player.finishRound(r.isDraw() ? RunState.Outcome.DRAW
                        : r.winner() == 0 ? RunState.Outcome.WIN : RunState.Outcome.LOSS);
                assertTrue(player.gold() >= 0);
            }
            assertTrue(player.isFinished(), "матч не завершился");
        }
    }

    @Test
    void botSpendsBudgetOnItems() {
        RunState bot = BotFactory.create(content, 8, 11);
        assertEquals(8, bot.round());
        assertTrue(bot.grid().items().size() >= 4, "у бота мало предметов: " + bot.grid().items().size());
        assertTrue(bot.storage().isEmpty());
    }
}
