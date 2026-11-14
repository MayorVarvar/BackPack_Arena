package dev.backpackarena.core.combat;

import dev.backpackarena.core.grid.PlacedPiece;
import dev.backpackarena.core.model.HeroDef;
import dev.backpackarena.core.model.ItemDef;
import dev.backpackarena.core.model.RelicDef;
import dev.backpackarena.core.run.RunState;

import java.util.ArrayList;
import java.util.List;

/**
 * Неизменяемый снимок экипировки перед боем (шаг 1 алгоритма боя из ТЗ):
 * в бою участвуют только предметы, стоящие в рюкзаке; хранилище не учитывается.
 */
public record FighterSpec(String name, HeroDef hero, List<ItemDef> items, List<RelicDef> relics) {

    public static FighterSpec of(RunState run) {
        List<ItemDef> items = new ArrayList<>();
        for (PlacedPiece p : run.grid().items()) {
            items.add(p.item().def());
        }
        return new FighterSpec(run.ownerName(), run.hero(), List.copyOf(items), List.copyOf(run.combatRelics()));
    }

    public double relicMod(String key) {
        double sum = 0;
        for (RelicDef r : relics) {
            sum += r.mod(key);
        }
        return sum;
    }
}
