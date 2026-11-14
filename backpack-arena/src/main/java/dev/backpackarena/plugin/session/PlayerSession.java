package dev.backpackarena.plugin.session;

import dev.backpackarena.core.grid.ItemInstance;
import dev.backpackarena.core.model.RelicDef;
import dev.backpackarena.core.run.RunState;
import dev.backpackarena.plugin.match.Match;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/** Игрок внутри матча: его забег, предмет «в руке», флаги готовности и боя. */
public final class PlayerSession {

    private final UUID uuid;
    private final String name;
    private final RunState run;
    private final Match match;
    private final InventorySnapshot snapshot;
    private ItemInstance hand;
    private boolean ready;
    private boolean inBattle;
    private List<RelicDef> relicChoices = List.of();
    private long leaveConfirmUntil;

    public PlayerSession(Player player, RunState run, Match match, InventorySnapshot snapshot) {
        this.uuid = player.getUniqueId();
        this.name = player.getName();
        this.run = run;
        this.match = match;
        this.snapshot = snapshot;
    }

    public Player player() {
        return Bukkit.getPlayer(uuid);
    }

    /** Возвращает предмет из руки в хранилище (перед боем, при закрытии меню). */
    public void stashHand() {
        if (hand != null) {
            run.storage().add(hand);
            hand = null;
        }
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public RunState run() {
        return run;
    }

    public Match match() {
        return match;
    }

    public InventorySnapshot snapshot() {
        return snapshot;
    }

    public ItemInstance hand() {
        return hand;
    }

    public void setHand(ItemInstance hand) {
        this.hand = hand;
    }

    public boolean ready() {
        return ready;
    }

    public void setReady(boolean ready) {
        this.ready = ready;
    }

    public boolean inBattle() {
        return inBattle;
    }

    public void setInBattle(boolean inBattle) {
        this.inBattle = inBattle;
    }

    public List<RelicDef> relicChoices() {
        return relicChoices;
    }

    public void setRelicChoices(List<RelicDef> relicChoices) {
        this.relicChoices = relicChoices;
    }

    /** Выход требует двойного нажатия в течение 5 секунд. */
    public boolean confirmLeave() {
        long now = System.currentTimeMillis();
        if (now < leaveConfirmUntil) {
            return true;
        }
        leaveConfirmUntil = now + 5000;
        return false;
    }
}
