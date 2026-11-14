package dev.backpackarena.core.bot;

import dev.backpackarena.core.content.GameContent;
import dev.backpackarena.core.crafting.CraftingEngine;
import dev.backpackarena.core.economy.Shop;
import dev.backpackarena.core.grid.ItemInstance;
import dev.backpackarena.core.model.HeroDef;
import dev.backpackarena.core.model.ItemDef;
import dev.backpackarena.core.run.RunState;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Соперник для тренировки. Бот «проживает» те же раунды, что и игрок, через обычный
 * магазин: покупает то, что помещается, докупает сумки, иногда рероллит, крафтит.
 * Так бот соблюдает те же правила экономики и не нуждается в отдельном балансе.
 */
public final class BotFactory {

    private BotFactory() {
    }

    public static RunState create(GameContent content, int round, long seed) {
        Random rng = new Random(seed);
        List<HeroDef> heroes = new ArrayList<>(content.heroes().values());
        HeroDef hero = heroes.get(rng.nextInt(heroes.size()));
        RunState bot = new RunState("Бот: " + hero.name(), hero, content, rng);
        for (int r = 1; r <= round; r++) {
            shopPhase(bot, rng);
            CraftingEngine.craftAll(bot);
            if (r < round) {
                // бот «выигрывает» через раз, чтобы его доход был средним
                bot.finishRound(r % 2 == 0 ? RunState.Outcome.WIN : RunState.Outcome.DRAW);
            }
        }
        return bot;
    }

    static void shopPhase(RunState bot, Random rng) {
        for (int guard = 0; guard < 20; guard++) {
            boolean bought = false;
            List<Shop.Offer> offers = bot.shop().offers();
            for (int i = 0; i < offers.size(); i++) {
                Shop.Offer offer = offers.get(i);
                if (offer == null || bot.shop().priceOf(offer) > bot.gold()) {
                    continue;
                }
                ItemDef def = offer.item();
                boolean needBag = bot.grid().freeItemCells() < 3;
                if (!def.isBag() && needBag) {
                    continue;
                }
                if (def.isBag() && !needBag && rng.nextDouble() < 0.7) {
                    continue;
                }
                if (bot.shop().buy(i) == Shop.Result.OK) {
                    bought = true;
                }
            }
            placeStorage(bot);
            if (!bought) {
                if (bot.gold() >= bot.shop().nextRerollCost() + 3 && rng.nextDouble() < 0.6) {
                    bot.shop().reroll();
                } else {
                    break;
                }
            }
        }
        // всё, что не влезло, продаём
        for (ItemInstance inst : new ArrayList<>(bot.storage())) {
            bot.shop().sell(inst);
        }
    }

    private static void placeStorage(RunState bot) {
        // сначала сумки, потом предметы
        for (ItemInstance inst : new ArrayList<>(bot.storage())) {
            if (inst.def().isBag() && bot.grid().autoPlace(inst)) {
                bot.storage().remove(inst);
            }
        }
        for (ItemInstance inst : new ArrayList<>(bot.storage())) {
            if (!inst.def().isBag() && bot.grid().autoPlace(inst)) {
                bot.storage().remove(inst);
            }
        }
    }
}
