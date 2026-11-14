package dev.backpackarena.core.model;

import java.util.Map;

/**
 * Способность героя. {@code type} выбирает реализацию в CombatSimulator,
 * {@code params} — числа из heroes.yml.
 */
public record AbilityDef(Type type, String name, String description, double cooldown, Map<String, Double> params) {

    public enum Type {
        /** Танк: броня + отражение поглощённого урона. */
        IRON_GUARD,
        /** Маг: чередование огня и льда для ударов оружием. */
        ELEMENT_SWITCH,
        /** Рой: временные пчёлы-питомцы. */
        SWARM,
        /** Лучница: серия стрел. */
        VOLLEY
    }

    public double param(String key, double def) {
        Double v = params.get(key);
        return v == null ? def : v;
    }
}
