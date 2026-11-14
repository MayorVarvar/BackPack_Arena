package dev.backpackarena.core.content;

import dev.backpackarena.core.combat.CombatConfig;
import dev.backpackarena.core.economy.EconomyConfig;
import dev.backpackarena.core.model.HeroDef;
import dev.backpackarena.core.model.ItemDef;
import dev.backpackarena.core.model.RecipeDef;
import dev.backpackarena.core.model.RelicDef;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Весь загруженный контент режима. Неизменяем; при /ba reload создаётся заново. */
public record GameContent(Map<String, HeroDef> heroes, Map<String, ItemDef> items,
                          Map<String, RelicDef> relics, List<RecipeDef> recipes,
                          EconomyConfig economy, CombatConfig combat) {

    public HeroDef hero(String id) {
        return heroes.get(id);
    }

    public ItemDef item(String id) {
        ItemDef def = items.get(id);
        if (def == null) {
            throw new IllegalArgumentException("Неизвестный предмет: " + id);
        }
        return def;
    }

    public Collection<ItemDef> allItems() {
        return items.values();
    }
}
