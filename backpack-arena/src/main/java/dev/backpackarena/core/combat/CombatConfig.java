package dev.backpackarena.core.combat;

/** Параметры combat.yml: шаг симуляции, статусы и их лимиты, анти-затягивание. */
public record CombatConfig(
        double tick,
        double maxDuration,
        double statusInterval,
        double fatigueStart,
        double fatigueDamage,
        double burnDamage, double burnDecay, double burnCap,
        double poisonDamage, double poisonCap,
        double chillSlow, double chillDecay, double chillCap,
        double hasteSpeed, double hasteDecay, double hasteCap,
        double stunSeconds, double stunCap,
        double empowerDamage, double empowerCap,
        double thornsCap, double lifestealCap, double armorCap,
        double minSpeed, double maxSpeed,
        int maxLoggedEvents) {

    public static CombatConfig defaults() {
        return new CombatConfig(0.1, 60, 1.0, 30, 1,
                1, 1, 60,
                1, 50,
                0.02, 0.5, 50,
                0.02, 0.5, 50,
                0.5, 3,
                1, 20,
                30, 20, 300,
                0.25, 3.0,
                400);
    }
}
