package dev.backpackarena.core.model;

/**
 * Боевые параметры атакующего предмета (оружие или питомец).
 *
 * @param stamina расход выносливости за атаку (питомцы — 0)
 * @param ranged  дальний бой: не вызывает шипы противника
 * @param pet     питомец: атакует без выносливости
 */
public record WeaponStats(double minDamage, double maxDamage, double cooldown, double stamina,
                          double accuracy, double critChance, boolean ranged, boolean pet) {

    public double averageDamage() {
        return (minDamage + maxDamage) / 2.0;
    }

    public double dps() {
        return averageDamage() * accuracy / cooldown;
    }
}
